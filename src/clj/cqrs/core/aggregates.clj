(ns cqrs.core.aggregates
  (:require [immutant.caching :as caching]
            [taoensso.timbre :refer [log trace debug info warn error]]
            [com.stuartsierra.component :as component :refer [using]]
            [clojure.java.jdbc :as jdbc])
  (:import [org.h2.tools Server])
  )

(defonce db-server (atom nil))

(defn start-db
  "Starts H2 server including web console (available on localhost:8082)."
  []
  (when-not @db-server
    (println "Starting DB, web console is available on localhost:8082")
    (reset! db-server {:tcp (Server/createTcpServer (into-array String []))
                       :web (Server/createWebServer (into-array String []))})
    (doseq [s (vals @db-server)] (.start s))))


(defn stop-db
  "Stops H2 server including web console."
  []
  (when-let [s @db-server]
    (println "Stopping DB")
    (doseq [s (vals s)] (.stop s))
    (reset! db-server nil)))


(def db-spec {:classname "org.h2.Driver"
              :subprotocol "h2"
              :subname "tcp://localhost/~/test"
              :user "sa"
              :password ""})

(def db-con (delay
              {:connection (jdbc/get-connection db-spec)}))


;================================================================================
; Aggregate Repository
;================================================================================

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


;TODO load aggregate, keep a pointer to its repository
;Add apply methods to Events -> update state directly on agg or on agg repo?
