(ns cqrs.core.commands
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer [publish queue listen]]
    [immutant.caching :as caching]
    [immutant.web.async :as async]
    [immutant.daemons :refer [singleton-daemon]]
    [schema.core :as s]
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
                              _links :- {:status s/Str} ])

(defprotocol ICommand
  (get-aggregate-id [this]) ;used to dispatch on command topic and retrieve aggregate from repo
  (perform [command state aggid version]))

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

;TODO Call get-agg-id to route to correct topic to ensure routing to single daemon handler - add hashing
;TODO get client-id to send back command status via ws
;TODO check user groups + authorized activities here?
(defn accept-command [cmd-bus cmd]
  (let [uuid (str (java.util.UUID/randomUUID))
        ;agg-id (get-agg-id cmd)
        ;topic-id (str "cmd-" (hash-to-bucket agg-id N))
        ;topic (get command-topics topic-id)
        ]
    (publish (get-in cmd-bus [:queues 0]) (assoc cmd :uuid uuid) :encoding :fressian)
    ;Return error 503 if queue unavailable
    (accepted {:id uuid
               :message "Command accepted"
               :_links {:status (str "/command/" uuid)
                        :status-channel (str "/commands") ;ws:// ?
                        }
               })
    ))

;(go-loop []
;         (when-let [msg (<! websocket-chan)]
;           (>! message-queue-chan msg)
;           (recur)))


;TODO add N topics given :cmd-partitions option
;Embed in CommandHandler to handle sync=true mode?
(defrecord CommandBus [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandBus component with options " options) )
    (let [N 1 ;Number of topics for sharding command handling
          queues (into [] (for [i (range N)] (queue (str "cqrs-commands-" i)))) ]
      (assoc this :queues queues)
      ))
  (stop [this]
    (info "Stopping CommandBus")
    this))

(defn build-commandbus [config]
  (map->CommandBus {:options config}))



(defn apply-events [state events]
  (reduce #(apply-event %2 %1) state events))



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
                           (let [aggid (get-aggregate-id cmd)
                                 agg (get aggregates aggid)
                                 version (:_version agg)
                                 old-events (load-events event-repository aggid version)
                                 current-state (apply-events agg old-events)
                                 _ (println current-state)
                                 events (perform cmd current-state aggid version) ;catch here exceptions - handle sync/async cases
                                 ]
                             ;Save new aggregate state
                             (try (.put aggregates aggid (assoc current-state :_version (inc version)))
                                  (catch Exception e (println "WARNING - Aggregate repository unavailable")))
                             ;publish events to command status cache (expiry of 1 minute)
                             (try (.put status (:uuid cmd) {:uuid (:uuid cmd)
                                                            :command (.getSimpleName (type cmd))
                                                            :status "PROCESSED"
                                                            :events events
                                                            })
                                  (catch Exception e (println "WARNING - Command status cache unavailable")))
                             ;broadcast to clients
                             (broadcast-command (assoc cmd :events events))
                             ;TODO add aggid / version here if success - when events exist
                             (insert-events event-repository aggid events) ))
          ;handler (listen (:queue command-bus) handle-command)
          ;Start N daemons to shard command topic handling
          N 1
          daemons (into [] (for [i (range N)]
                             (let [dhandler (atom nil)
                                   dname (str "command-handler/" i)]
                               (singleton-daemon dname
                                                 (fn []
                                                   (reset! dhandler (listen (get-in command-bus [:queues i]) handle-command))
                                                   (println (str "Daemon " dname " started")))
                                                 (fn []
                                                   (.close @dhandler)
                                                   (println (str "Daemon " dname " stopped")))))))]
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
