(ns cqrs.services.usage
  "
  Use inheritance to provide behavior for events for this service
  http://david-mcneil.com/post/1475458103/implementation-inheritance-in-clojure
  "
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer :all]
    [cqrs.engines.elasticsearch :refer [index-event]]
    )
  (:import (cqrs.domains.model ModelCreatedEvent)))

;(defprotocol IUsageEvent [])

;TODO extend here ModelCreatedEvent with IUsageEvent protocol
;(extend ModelCreatedEvent
;  IElasticsearchIndex
;  base-indexer ;should pass service config (ex: which index etc) ;insert/update etc...
;  IUsageEvent
;  (apply [event]) ;should return [:method :payload]  ;e.g. increment counter etc
;  )

(defrecord UsageService [options event-bus index-engine]
  component/Lifecycle
  (start [this]
    (let [update (fn [event] (index-event index-engine event) )
          listener (subscribe (:topic event-bus) "usage-service" update)]
      (info (str "Starting Usage Service component with options " options) )
      (assoc this
        :listener listener
        :subscription-name "usage-service")))
  (stop [this]
    (info "Stopping Usage Service")
    (.close (:listener this))
    (unsubscribe (:topic event-bus) (:subscription-name this) )
    (dissoc this :subscription-name)))


(defn build-usage-service [config]
  (map->UsageService {:options config}))

;TODO RemoteHttpService or WebHookService
;TODO remote webhook or microservice - Push Avro or JSON via POST to a given URL

;TODO expose here service routes
;TODO cli for stand-alone deployment? Docker?