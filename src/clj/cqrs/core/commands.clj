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

;TODO Call get-agg-id to route to correct topic to ensure routing to single daemon handler - add hashing
;TODO get client-id to send back command status via ws
;TODO check user groups + authorized activities here?
(defn accept-command [q cmd]
  (let [uuid (str (java.util.UUID/randomUUID))]
    (publish q (assoc cmd :uuid uuid) :encoding :fressian)
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

(map #(hash-to-bucket % 10) ["a" "b" "c" "cc" "d" "ee" "f" "i" "j" "k" "l" "m"
                             "n" "o" "p" "q" "r" "s" "t" "u" "vv" "v" "x" "y" "z" "ab" "bc" "cd"
                             "user/1" "user/2"
                             ])

;TODO rename into topic - add N topics given :cmd-partitions option
;Embed in CommandHandler to handle sync=true mode?
(defrecord CommandQueue [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandQueue component with options " options) )
    (assoc this :queue (queue "cqrs-commands"))
    )
  (stop [this]
    (info "Stopping CommandQueue")
    this))

(defn build-commandqueue [config]
  (map->CommandQueue {:options config}))


(defrecord AggregateRepository []
  component/Lifecycle
  (start [this]
    (let [cache (immutant.caching/cache "aggregates")]
      {:cache cache}))
  (stop [this]
    (immutant.caching/stop (:cache this))
    (dissoc this :cache)
    )
  )

(defn apply-events [state events]
  (reduce #(apply-event %2 %1) state events))



;TODO Should be a Singleton/daemon subscribing to a Command topic - i.e. call Cmd get-agg-id before inserting in topic
;TODO hash based routing on agg id - start up to N command handlers
;TODO encapsulate agg cache in aggregate-repository


;TODO add handler - connected clients -> should filter created commands on their client ids, chsk-send! command and its events to each

(defrecord CommandHandler [options command-queue event-repository]  ;state-cache for Aggregate objects?
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandHandler component with options " options) )
    (let [aggregates (immutant.caching/cache "aggregates")
          status (immutant.caching/cache "command-status" :ttl [1 :minute])
          ;daemons (atom {}) ;sharded command handler daemons
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
                                  (catch Exception e (println "WARNING - Aggregate cache unavailable")))
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
          handler (listen (:queue command-queue) handle-command)
          ;Start N daemons to shard command topic handling
          daemon (let [dhandler (atom nil)
                       dname "command-handler-1"]
                   (singleton-daemon dname
                                     (fn []
                                       ;TODO subscribe to topic 1 in command-queue - rename also command-queue to topic!
                                       ;(reset! dhandler (listen (:queue command-queue) handle-command))
                                       (println "daemon started"))
                                     (fn []
                                       ;(.close @dhandler)
                                       (println "daemon stopped") )))
          ]
      (.put aggregates :test {:_version 0})
      (assoc this :handler handler
                  :daemon daemon
                  :status status
                  :aggregates aggregates)))
  (stop [this]
    (info "Stopping CommandHandler")
    (.close (:handler this))
    (.stop (:daemon this))
    (immutant.caching/stop (:aggregates this))
    (immutant.caching/stop (:status this))
    (dissoc this :handler :cache :daemon) ))

(defn build-commandhandler [config]
  (map->CommandHandler {:options config}))


;daemon functions
;(defonce listener (atom nil))
;(defn start-listener []
;  (swap! listener (fn [listener] (listen "my-queue" handle-msg) )))
;(defn stop-listener []
;    (swap! listener (fn [listener] (when listener (unlisten listener) ))))


;================================================================================
; COMMAND CHANNEL/WEBSOCKET
;================================================================================

;TODO when client connected, open a subscription to new topic?

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

;on handshake - have a command-status-handler started in CommandHandler,
;should register channel with user id, get all command-status for this user

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
           ;(GET  "/commands" req (ring-ajax-get-or-ws-handshake req))
           ;(POST "/commands" req (ring-ajax-post                req))
           ))


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
