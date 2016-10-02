(ns cqrs.core.commands
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer [publish queue listen stop]]
    [immutant.caching :as caching]
    [immutant.web.async :as async]
    [immutant.daemons :refer [singleton-daemon]]
    [schema.core :as s]
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [compojure.api.sweet :refer :all]
    [ring.util.http-response :refer :all]
    ;[taoensso.sente :as sente]
    ;[taoensso.sente.server-adapters.immutant      :refer (get-sch-adapter)]
    ;[clojure.core.async :refer [go-loop <! >!]]
    [cqrs.core.events :refer :all]
    [cqrs.core.ws :refer [broadcast-command]]
    )
  (:gen-class)
  )


(s/defrecord CommandAccepted [id :- s/Num
                              message :- s/Str
                              links :- [{:rel s/Str
                                         :href s/Str}] ])

(defprotocol ICommand
  (get-aggregate-id [this]) ;used to dispatch on command topic and retrieve aggregate from repo
  (perform [command state aggid version]) ;check state, produce events
  )

(defn sch [r] (last (last (schema.utils/class-schema r))))



;================================================================================
; TOPIC SHARDING
;================================================================================

(def murmur
  (let [m (com.google.common.hash.Hashing/murmur3_128)]
    (fn ^Long [^String s]
      (-> (doto (.newHasher m)
            (.putString s com.google.common.base.Charsets/UTF_8))
          (.hash)
          (.asLong)
          ))))

(defn hash-to-bucket [e B]
  (let [h (+ 0.5 (/
                   (murmur e)
                   (Math/pow 2 64)
                   ))]
    (int (Math/floor (* h B)))
    ))

;(map #(hash-to-bucket % 10) ["a" "b" "c" "cc" "d" "ee" "f" "i" "j" "k" "l" "m"
;                             "n" "o" "p" "q" "r" "s" "t" "u" "vv" "v" "x" "y" "z" "ab" "bc" "cd"
;                             "user/1" "user/2"
;                             ])

(defn make-timestamp [] (c/to-long (t/now)) )

;TODO get client-id to send back command status via ws?
;TODO check user groups + authorized activities here?
(defn accept-command [cmd-bus cmd]
  (let [uuid (str (java.util.UUID/randomUUID))
        n-handlers (:n-handlers cmd-bus)
        agg-id (name (get-aggregate-id cmd)) ;or apply str "/" if []?
        topic-id (hash-to-bucket agg-id n-handlers)
        ]
    (publish (get-in cmd-bus [:queues topic-id]) (assoc cmd :uuid uuid) :encoding :fressian)
    ;TODO Return error 503 if queue unavailable
    (accepted {:id uuid
               :message "Command accepted"
               :links [{:rel "status" :href (str "/command/" uuid)}
                       {:rel "status-channel" :href (str "/commands")}] ;ws:// ?
               })
    ))

;(go-loop []
;         (when-let [msg (<! websocket-chan)]
;           (>! message-queue-chan msg)
;           (recur)))


;Embed in CommandHandler to handle sync=true mode?
(defrecord CommandBus [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandBus component with options " options) )
    (let [N (get options :n-handlers 1) ;Number of topics for sharding command handling
          queues (into [] (for [i (range N)] (queue (str "cqrs-commands/" i)))) ]
      (assoc this :queues queues :n-handlers N)
      ))
  (stop [this]
    (info "Stopping CommandBus")
    (doseq [q (:queues this)] (stop q))
    this))

(defn build-commandbus [config]
  (map->CommandBus {:options config}))




;TODO hash based routing on agg id - start up to N command handlers
;TODO encapsulate agg cache in aggregate-repository
;TODO add handler - connected clients -> should filter created commands on their client ids, chsk-send! command and its events to each

(defrecord CommandHandler [options command-bus event-repository]  ;Add AggregateRepository for Aggregate objects
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandHandler component with options " options) )
    (let [aggregates (immutant.caching/cache "aggregates")
          status (immutant.caching/cache "command-status" :ttl [1 :minute])
          handle-command (fn [cmd]
                           ;should load Aggregate here - hide aggid afterwards!
                           (let [aggid (get-aggregate-id cmd)
                                 agg (get aggregates aggid) ;lazy load instead!
                                 version (:_version agg)
                                 old-events (load-events event-repository aggid version)
                                 current-state (apply-events agg old-events) ;Should be updated Agg object - may have changed on disk
                                 _ (debug current-state)
                                 events (perform cmd current-state aggid version) ;catch here exceptions - handle sync/async cases
                                 ]
                             ;TODO Move save new aggregate state to AggregateRepository
                             (try (.put aggregates aggid (assoc current-state :_version (inc version)))
                                  (catch Exception e (error "Aggregate repository unavailable")))
                             ;Persist command status cache (expiry of 1 minute)
                             ;If error -> add command error status
                             (try (.put status (:uuid cmd) {:uuid (:uuid cmd)
                                                            :command (.getSimpleName (type cmd))
                                                            :status "PROCESSED" ;or FAILURE/REJECTED
                                                            :events events
                                                            })
                                  (catch Exception e (error "Command status cache unavailable")))
                             ;broadcast to clients
                             (broadcast-command (assoc cmd :events events))
                             ;TODO add aggid / version here if success - when events exist
                             (insert-events event-repository aggid events) ))
          ;handler (listen (:queue command-bus) handle-command)
          ;Start N daemons to shard command topic handling
          N (get options :n-handlers 1)
          daemons (into [] (for [i (range N)]
                             (let [dhandler (atom nil)
                                   dname (str "command-handler/" i)]
                               (singleton-daemon dname
                                                 (fn []
                                                   (reset! dhandler (listen (get-in command-bus [:queues i]) handle-command))
                                                   (info (str "Daemon " dname " started")))
                                                 (fn []
                                                   (.close @dhandler)
                                                   (info (str "Daemon " dname " stopped")))))))]
      (.put aggregates :test {:_version 0}) ;FIXME remove this
      (assoc this ;:handler handler
                  :daemons daemons
                  :status status
                  :aggregates aggregates)))
  (stop [this]
    (info "Stopping CommandHandler")
    ;(.close (:handler this))
    (doseq [d (:daemons this)] (.stop d))
    (immutant.caching/stop (:aggregates this))
    (immutant.caching/stop (:status this))
    (dissoc this :daemons) ))

(defn build-commandhandler [config]
  (map->CommandHandler {:options config}))


;================================================================================
; COMMAND CHANNEL/WEBSOCKET
;================================================================================

(s/defschema CommandStatus {:uuid s/Str
                            :command s/Str
                            :status s/Str
                            :events [{s/Keyword s/Any}]
                            })

(defn get-command-status [cmd-handler id]
  (if-let [status (get (:status cmd-handler) id)]
    (ok status)
    (gone {:error "Command status not available or expired."})
    )
  )

;TODO when client connected, open a subscription to new topic?
;on handshake - have a command-status-handler started in CommandHandler,
;should register channel with user id, get all command-status for this user?

(defn commands-routes [command-handler]
  (context "/api" []
           :tags ["Commands"]
           (GET "/commands/:id" []
                :path-params [id :- s/Str]
                :responses {200 {:schema CommandStatus :description "Command status and generated events."}
                            410 {:schema {:error s/Str} :description "Command status not available or expired."}}
                :summary "Returns command status"
                (get-command-status command-handler id)
                )
           ))

;TODO expose entities via API
(defn aggregate-routes [])

;define Command
;(defmacro defcommand)

;(defdomain "model"
; TestCommand)

;(defevent TestCreated) ;should call defrecord and create dummy constructor

;(defmacro defevent
;  [name args & [comment]]
;  (let [tname (symbol (s/capitalize (str name)))]
;    `(do
;       (deftype ~tname ~args)
;       (defn ~name ~(str comment) ~args (new ~tname ~@args)))))


;(defcommand RequestTest [message :- s/String]
; :agg-id (fn [cmd] :test)
; :perform (fn [command state aggid version] [(->TestCreated)])
; :context "/api/v1"
; :tags ["Tests"]
; :route "/test/create"
; :summary "Run a test")
;)
