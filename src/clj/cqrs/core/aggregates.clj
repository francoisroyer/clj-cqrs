(ns cqrs.core.aggregates
  (:require [immutant.caching :as caching]
            [taoensso.timbre :refer [log trace debug info warn error]]
            [com.stuartsierra.component :as component :refer [using]]))




;================================================================================
; Aggregate Repository
;================================================================================

(defprotocol IAggregateRepository
  (get-aggregate [this id])
  (save [this aggid agg]))


(defrecord InfinispanAggregateRepository []
  component/Lifecycle
  (start [this]
    (let [cache (immutant.caching/cache "aggregates")]
      (info "Started AggregateRepository backed by Infinispan")
      {:cache cache}))
  (stop [this]
    (immutant.caching/stop (:cache this))
    (dissoc this :cache))

  IAggregateRepository
  (get-aggregate [this id] (get (:cache this) id))
  (save [this aggid agg]
    (try (.put (:cache this) aggid agg)
         (catch Exception e (error "Aggregate repository unavailable")))))



(defrecord H2AggregateRepository []) ;Use H2 as engine

(defrecord PostgresAggregateRepository []) ;Use PostgreSQL as engine

(defn build-aggregaterepository
  "Build AggregateRepository in dev or prod mode - TODO build InMemory or H2 or PG depending on config"
  [config]
  ;Dispatch on deployment option
  (map->InfinispanAggregateRepository {:options config}))


;TODO load aggregate, keep a pointer to its repository - need factory?
;Add apply methods to Events -> update state directly on agg or on agg repo?
