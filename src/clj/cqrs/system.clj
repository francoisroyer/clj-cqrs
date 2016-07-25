(ns cqrs.system
  "
  DESCRIPTION
  REST/CQRS api.
  Use Event-sourcing to maintain state.
  TODO deployable as Immutant app or stand-alone?

  REFERENCES
  http://www.jayway.com/2013/04/02/event-sourcing-in-clojure/
  https://www.authorea.com/users/13936/articles/86622
  "
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [clojure.java.io              :as io]
    [immutant.messaging :refer :all]
    [immutant.caching :as caching]
    [immutant.web :as web]
    [schema.core :as s]
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [hashids.core :as h]
    [ring.util.http-response :refer :all]
    [cqrs.core.api :refer :all]
    [cqrs.core.commands :refer :all]
    [cqrs.engines.elasticsearch :refer [build-index-engine]]
    [cqrs.services.stats :refer [build-stats-service]]
    [environ.core       :refer (env)]
    )
  (:gen-class)
  )
;(remove-ns 'clj-cqrs.api)

;================================================================================
; CQRS COMPONENTS
;================================================================================

;TODO EventStore - file or DB (PG-hstore, mapDB, H2, Dynamo...)


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


(defprotocol IEventStore
  (retrieve-event-stream [this aggregate-id])
  (append-events [this aggregate-id previous-event-stream events]))


(defn store [event]
  (let [current-label (atom "now")
        event-file (clojure.java.io/file "data" (str @current-label ".events"))]
    (io! (with-open [w (clojure.java.io/writer event-file :append true)]
           (clojure.pprint/pprint event w)))
    ;(when (>= (event :seq) events-per-snapshot)
    ;        (snapshot @world))
    ))

;Should implement IEventStore with store, retrieve-events etc...
(defrecord EventStore [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventStore component with options " options))
    (assoc this :store (atom {})))
  (stop [this]
    (info "Stopping EventStore")
    (dissoc this :store))
  )

(defn build-eventstore [config]
  (map->EventStore {:options config}))



(defrecord EventStream [version transactions])


;Enables replaying of events from event-store
;Handles also loading of fixtures if they are given in options -> inserts them in EventStore and EventQueue
;Create fixtures?
;sync / update method: At start-up, asks state-cache version-id - if behind, will replay events
;add a synchronous update! method?
(defrecord EventRepository [options event-store event-topic state-cache]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventRepository component with options " options) )
    (assoc this :event-store event-store
                :event-topic event-topic))
  (stop [this]
    (info "Stopping EventRepository")
    this))

(defn insert-events
  "Insert events in event-store and publish them via event-topic"
  [event-repository aggid events]
  ;Save events
  (swap! (get-in event-repository [:event-store :store])
         update-in [aggid] concat events)
  ;Publish events
  (doseq [event events]
    (publish (-> event-repository :event-topic :topic) event :encoding :fressian))
  )

(defn load-events
  "Load events from event repository for given aggregate, since given version"
  [event-repo aggid version]
  (filter #(>= version (:version %)) (get @(get-in event-repo [:event-store :store]) aggid))
  )

(defn build-eventrepo [config]
  (map->EventRepository {:options config}))



;================================================================================
; Test command and events
;================================================================================

(defrecord TestEvent [aggid version
                      id created-at message]
  IEvent
  (apply-event [event state]
    (assoc state :state :tested
                 :last-tested-at (:created-at event)))
  )


(s/defrecord TestCommand [message]
             ICommand
             (get-aggregate-id [this] :test)
             (perform [command state aggid version]
                      ;(when (:state state)
                      ;  (throw (Exception. "Already in started")))
                      (let [new-version (inc version)
                            created-at (c/to-long (t/now))
                            id 0]
                        (println "TestCommand executed.")
                        [(->TestEvent aggid new-version id created-at message)]) ))

;define Command
;(defmacro defcommand)

;(defdomain "model"
; TestCommand)

;(defcommand TestCommand
; :agg-id (fn [cmd] :test)
; :perform (fn [command state aggid version] )
; :context "/api/v1"
; :tags ["Models"]
; :route "/test"
; :summary "Run a test")
;)

;================================================================================
; EVENT AGGREGATES
;================================================================================

(defn apply-events [state events]
  (reduce #(apply-event %2 %1) state events))

(defrecord CommandHandler [options command-queue event-repository]  ;state-cache for Aggregate objects?
  component/Lifecycle
  (start [this]
    (info (str "Starting CommandHandler component with options " options) )
    (let [cache (immutant.caching/cache "aggregates")
          ;status-cache (immutant.caching "command-status" :ttl [1 :minute])
          handle-command (fn [cmd]
                           (let [aggid (get-aggregate-id cmd)
                                 agg (get cache aggid)
                                 version (:_version agg)
                                 old-events (load-events event-repository aggid version)
                                 current-state (apply-events agg old-events)
                                 _ (println current-state)
                                 events (perform cmd current-state aggid version) ;catch here exceptions - handle sync/async cases
                                 ]
                             ;publish events to command status cache (expiry of 1 minute?)
                             (try (.put cache aggid (assoc current-state :_version (inc version)))
                                  (catch Exception e (println "WARNING - Cache problem?")))
                             ;TODO add aggid / version here if success - when events exist
                             (insert-events event-repository aggid events) ))
          handler (listen (:queue command-queue) handle-command)]
      (.put cache :test {:_version 0})
      (assoc this :handler handler
                  :cache cache)))
  (stop [this]
    (info "Stopping CommandHandler")
    (.close (:handler this))
    (immutant.caching/stop (:cache this))
    (dissoc this :handler :cache) ))

(defn build-commandhandler [config]
  (map->CommandHandler {:options config}))



;================================================================================
; Event pub/sub
;================================================================================

(defrecord EventTopic [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventTopic component with options " options) )
    (assoc this :topic (topic "events"))
    )
  (stop [this]
    (info "Stopping EventTopic")
    (stop (:topic this))
    this))

(defn build-eventtopic [config]
  (map->EventTopic {:options config}))


;================================================================================
; CQRS SYSTEM
;================================================================================

(defrecord WebServer [options api]
  component/Lifecycle
  (start [this]
    (info (str "Starting WebServer with options " options) )
    (let [server (web/run (:handler api)
                          :host "localhost"
                          :port 8080 :path "/"
                          ;(merge
                          ;  {"host" (env :demo-web-host)
                          ;   "port" (env :demo-web-port 8080)})
                          )]
      (assoc this :server server) ))
  (stop [this]
    (info "Stopping WebServer")
    this))

(defn build-webserver [config]
  (map->WebServer {:options config}))


(defn make-app-system [config]
  (let [{:keys [host port]} config]
    (component/system-map
      :command-queue (build-commandqueue config )
      :event-repository (using (build-eventrepo config) [:event-store :event-topic])
      :command-handler (using (build-commandhandler config) [:event-repository :command-queue] )
      ;:agg-store ;use immutant.cache
      :event-store (build-eventstore config)
      :event-topic (build-eventtopic config)
      ;:resource-staging ;S3? For datasets or documents
      ;:record-topic ;AWS SQS? For coerced records, i.e. InsertRecord{:type ... :fields ...}
      ;Object-topic ;send Objects?
      ;handle insert/serving of records given their types - ex: Solr or Elasticsearch
      :index-engine (using (build-index-engine config) [:event-topic])
      ;Subscribe each event-service to events, insert as stream or batch if restart
      :stats-service (using (build-stats-service config) [:event-topic :index-engine] )
      :api (using (build-api (:api config)) [:command-queue] )
      :web-server (using (build-webserver config) [:api])
      )))

(defn make-service-system
  "Remote services system - ES, SOLR, Ingest/ETL (Kafka/Samsara), stream/batch, data staging/repositories"
  [config] )

;:job-server ;stream/batch, onyx?
;:collectors ;riemann?
;:datastore ;?
;:datamart ;solr

;Riemann for metrics/logs

(def system (make-app-system {:http {:host "localhost"
                                     :port 9999}
                              :elasticsearch {:local true
                                              :path "http://localhost:9200"}
                              :api {}
                              }))

(defn start! []
  (try (alter-var-root #'system component/start)
       (catch Exception e (println (component/ex-without-components e)) ))
  )

(defn stop! []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

;define user/project *context* for command
(defn run-command! [cmd]
  ;Call accept-command from API routes
  ;Store result of last command in *result*
  (accept-command (-> system :command-queue :queue) cmd )
  )

;(start!)
;(stop!)
;(run-command! (map->TestCommand {:message "ok"}) )

(defn run-query [system q])

;TODO "saga" or EventStory in reaction to Events for integration of other services etc...
;TODO scheduled events -> Quartz

;TODO IAM with Keycloak

;TODO deploy on wildfly/openshift/jenkins

;TODO store/send events as Avro?

(defn -main [& args]
  (start!))

;(defn -main [& {:as args}]
;  (web/run
;    (-> routes
;        (immutant.web.middleware/wrap-session
;          {:timeout 20}))
;    (merge
;      {"host" (env :demo-web-host)
;       "port" (env :demo-web-port 8080)
;       "ssl-port" (env :ssl-port)
;       "http2?" (env :http2?)
;       :keystore (io/resource "server.keystore")
;       :key-password "password"
;       :truststore (io/resource "server.truststore")
;       :trust-password "password"}
;      args)))
