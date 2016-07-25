(ns cqrs.services.stats
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer :all]
    [cqrs.engines.elasticsearch :refer [index-event]]
    ))


(defrecord StatsService [options event-topic index-engine]
  component/Lifecycle
  (start [this]
    (let [update (fn [event] (index-event index-engine event) )
          listener (subscribe (:topic event-topic) "stats-service" update)]
      (info (str "Starting Stats Service component with options " options) )
      (assoc this
        :listener listener
        :subscription-name "stats-service")))
  (stop [this]
    (info "Stopping Stats Service")
    (.close (:listener this))
    (unsubscribe (:topic event-topic) (:subscription-name this) )
    (dissoc this :subscription-name)))


(defn build-stats-service [config]
  (map->StatsService {:options config}))

;TODO RemoteHttpService or WebHookService
;TODO remote webhook or microservice - Push Avro or JSON via POST to a given URL

