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
      :command-bus (build-commandbus config )
      :event-repository (using (build-eventrepo config) [:event-store :event-bus])
      :command-handler (using (build-commandhandler config) [:event-repository :command-bus] )
      ;:aggregate-repository ;use immutant.cache or clj/aggregate with jdbc
      :event-store (build-eventstore config)
      :event-bus (build-eventbus config)
      ;:resource-staging ;S3? For datasets or documents
      ;:record-bus ;AWS SQS? For coerced records, i.e. InsertRecord{:type ... :fields ...}
      ;handle insert/serving of records given their types - ex: Solr or Elasticsearch
      :index-engine (using (build-index-engine config) [:event-bus])
      ;Subscribe each event-service to a dedicated topic in event-bus, insert as stream or batch if restart
      :usage-service (using (build-usage-service config) [:event-bus :index-engine] )
      :api (using (build-api (:api config)) [:command-bus :command-handler] )
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
  (accept-command (-> system :command-bus) cmd )
  )

;(start!)
;(stop!)
;(run-command! (map->TestCommand {:message "ok"}) )

(defn run-query [system q])


;TODO IAM with Keycloak or buddy
;TODO auth service - activity based UI and RBAC resource listing - http://clojutre.org/2015/slides/kekkonen_clojutre2015_print.pdf

;TODO deploy on wildfly/openshift/jenkins
;TODO "saga" or EventStory in reaction to Events for integration of other services etc...
;TODO service for scheduled events -> Quartz - should subscribe to all events - apply-until (without side effects)
;TODO monitoring service / dashboard - Riemann - Docker see samsara?
;TODO datasource crawl service on onyx/jboss topic

;TODO login screen in dev/prod mode
;TODO redirect to login if id missing in session
;TODO auth client with unique id, or login! Insert client-id in command if authenticated.

;TODO receive command status/events via ws - have CommandHandler look up command.client-id in cache and broadcast to connected aliases
;TODO client side: handle-command-correlation -> receive via ws events with correlation id equal to command uuid, or poll, retry and time out
;TODO send command to server via ws via client re-frame handler - same channel?

;TODO command handler daemon + topic sharding - based on aggid hash?
;TODO transaction for writing state to agg cache and sending events to event store / event topic
;TODO execute command handler in a transaction? Then add a parameter sync=true - send back _links if async, or use websocket for both directions

;TODO Reference app for IOT - asset catalog, classification, supervision, BPM, time series, analytics, alerts
;TODO use PG for agg/entity storage - use Solr for graph/text/geo search
;TODO paginate child entities in aggregate entities (links - next) - pagesize & page
;TODO audit route for each entity/agg /:entity/:id/audit/logs /audit/errors

;TODO on connection of service, ask EventRepository to reply with all events since :last-update
;TODO provide implementations for EventStore: h2, pg, file...
;TODO add /service/:service/events?version=xxx endpoint to retrieve events for a given service/topic?

;TODO Command + Aggregate + Event specs + Services topics -> Avro? Datalog? Swagger?

;TODO store/send events as Avro records - push to Kafka or HornetQ Rest topics or webhooks (Kafka/Hermes)
;TODO get in ENV variable or edn: /api/green/commands or /api/v1.0.1/commands
;TODO only Events should be modeled/versioned as Avro, not Commands


;name wars as cqrs-green or cqrs-blue OR cqrs-0.2.1 OR cqrs-0.3.0
;git-flow -> get tag on master?

;Avro schemas for Commands and Events? Avro service for Aggregates?
;clara rules for aggregates? Or object methods?
;(defrule update-billing-address
;         "Updates billing address, ensures unique default"
;         [UpdateDeliveryAddress (= ?client client)]
;         [AccountAggregate (= ?client client) (= ?name name)]
;         =>
;         (->DeliveryAddressUpdated address zip-code city)
; )

;Swagger API (YAML)
;OperationId ->  method name (command handler or service)
;AggregateId -> which agg to load
;Clara rules -> execute rules on aggregate, command, generate events


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
