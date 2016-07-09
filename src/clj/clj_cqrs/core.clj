(ns clj-cqrs.core
  "
  DESCRIPTION
  REST/CQRS api.
  Embeds a datomic instance in dev mode.
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
    [clj-cqrs.api :refer :all]
    [clj-cqrs.domains :refer :all]
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
    (assoc this :event-store (atom {})))
  (stop [this]
    (info "Stopping EventStore")
    (dissoc this :event-store))
  )

(defn build-eventstore [config]
  (map->EventStore {:options config}))



(defrecord EventStream [version transactions])


;Enables replaying of events from event-store
;Handles also loading of fixtures if they are given in options -> inserts them in EventStore and EventQueue
;Create fixtures?
;sync / update method: At start-up, asks state-cache version-id - if behind, will replay events
;add a synchronous update! method?
(defrecord EventRepository [options event-store event-queue state-cache]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventRepository component with options " options) )
    (assoc this :event-store event-store
                :event-queue event-queue))
  (stop [this]
    (info "Stopping EventRepository")
    this))

(defn insert-events
  "Insert events in event-store and publish them via event-queue"
  [event-repository aggid events]
  ;Save events
  (swap! (get-in event-repository [:event-store :event-store])
         update-in [aggid] concat events)
  ;Publish events
  ;(doseq [event events]
  ;(publish (-> event-repository :event-queue :queue) event :encoding :fressian))
  )

(defn load-events
  "Load events from event repository for given aggregate, since given version"
  [event-repo aggid version]
  (filter #(>= version (:version %)) (get @(get-in event-repo [:event-store :event-store]) aggid))
  )

(defn build-eventrepo [config]
  (map->EventRepository {:options config}))



;================================================================================
; Test command and events
;================================================================================

(defrecord TestEvent [aggid version
                      id created-at message])

(defmethod apply-event :TestEvent [state event]
  (assoc state :state :tested
               :last-tested-at (:created-at event)))

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


;================================================================================
; EVENT AGGREGATES
;================================================================================

(defn apply-events [state events]
  (reduce apply-event state events))

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
; Event publishing
;================================================================================

(defrecord EventQueue [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventQueue component with options " options) )
    (assoc this :queue (queue "events"))
    )
  (stop [this]
    (info "Stopping EventQueue")
    this))

(defn build-eventqueue [config]
  (map->EventQueue {:options config}))



;Updates world state with event
(defn update-views! [state-store event])

(defrecord EventHandler [options event-queue state-store]
  component/Lifecycle
  (start [this]
    (let [update (fn [event] (update-views! state-store event) )
          handler (listen (:queue event-queue) update)]
      (info (str "Starting EventHandler component with options " options) )
      (assoc this
        :handler handler)))
  (stop [this]
    (info "Stopping EventHandler")
    (.close (:handler this))
    (dissoc this :handler)))

(defn build-eventhandler [config]
  (map->EventHandler {:options config}))


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


(defn make-system [config]
  (let [{:keys [host port]} config]
    (component/system-map
      :command-queue (build-commandqueue config )
      :event-repository (using (build-eventrepo config) [:event-store :event-queue])
      :command-handler (using (build-commandhandler config) [:event-repository :command-queue] )
      ;:state-cache ;use immutant.cache
      :event-store (build-eventstore config)
      :event-queue (build-eventqueue config)
      :event-handler (using (build-eventhandler config) {:event-queue :event-queue} )
      ;:records-staging ;S3? For raw records
      ;:records-queue ;AWS SQS? For coerced records, i.e. InsertRecord{:type ... :fields ...}
      ;:records-handlers ;
      ;:records-store ;handle insertion of records given their types
      :api (using (build-api (:api config)) [:command-queue] )
      :web-server (using (build-webserver config) [:api])
      )))

;:job-server ;stream/batch, onyx?
;:collectors ;riemann?
;:datastore ;?
;:datamart ;solr

;Riemann for metrics/logs

(def system (make-system {:http {:host "localhost"
                                 :port 9999}
                          :api {}
                          :datalog {:engine :graphlite-local}
                          :database {:engine :atom}
                          }))

(defn start! []
  (try (alter-var-root #'system component/start)
       (catch Exception e (println (component/ex-without-components e)) ))
  )

(defn stop! []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

;define user/project *context* for command
(defn run-command! [system cmd]
  ;Call accept-command from API routes
  ;Store result of last command in *result*
  (accept-command (-> system :command-queue :queue) cmd )
  )

;(start!)
;(stop!)
;(run-command! system (map->TestCommand {:message "ok"}) )

(defn run-query [system q])

;TODO "saga" or EventStory in reaction to Events for integration of other services etc...
;TODO scheduled events -> Quartz

;TODO IAM with Keycloak

;TODO deploy on wildfly/openshift/jenkins

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
