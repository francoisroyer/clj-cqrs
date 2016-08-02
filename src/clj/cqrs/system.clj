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
    [immutant.web :as web]
    [schema.core :as s]
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [hashids.core :as h]
    [cqrs.core.api :refer :all]
    [cqrs.core.commands :refer :all]
    [cqrs.core.events :refer :all]
    [cqrs.engines.elasticsearch :refer [build-index-engine]]
    [cqrs.services.usage :refer [build-usage-service]]
    [environ.core       :refer (env)]
    )
  (:gen-class)
  )
;(remove-ns 'clj-cqrs.api)


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
      :usage-service (using (build-usage-service config) [:event-topic :index-engine] )
      :api (using (build-api (:api config)) [:command-queue :command-handler] )
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

;TODO IAM with Keycloak or buddy

;TODO deploy on wildfly/openshift/jenkins

;TODO send command status/events via ws
;TODO command handler daemon sharding
;TODO store/send events as Avro records - push to Kafka or HornetQ Rest topics or webhooks (Kafka/Hermes)



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
