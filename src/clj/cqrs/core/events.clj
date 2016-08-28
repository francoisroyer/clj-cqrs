(ns cqrs.core.events
  "TODO defevent macro including ser/deser in Avro"
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer :all]
    [immutant.messaging :refer [publish]]
    [schema.core :as s]
    [abracad.avro :as avro]
    )
  )

(defprotocol IEvent
  (apply-event [event state])
  )

(defn apply-events
  "Reverse method args to be used in reduce by CommandHandler"
  [state events]
  (reduce #(apply-event %2 %1) state events))

;================================================================================
; EVENT STORE COMPONENTS
;================================================================================

;TODO EventStore - file or DB (PG-hstore, mapDB, H2, Dynamo...)
;TODO Avro? Nippy?


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

(def schema
  (avro/parse-schema
    {:type :record
     :name "LongList"
     :aliases ["LinkedLongs"]
     :fields [{:name "value", :type :long}
              {:name "next", :type ["LongList", :null]}]}))

;TODO get schema from all events - build app schema - store in S3
;TODO store with nippy in SQL DB? Use nippy instead of fressian?

;(with-open [adf (avro/data-file-writer "snappy" schema "example.avro")]
;  (.append adf {:value 0, :next nil})
;  (.append adf {:value 8, :next {:value 16, :next nil}}))

;(with-open [adf (avro/data-file-reader "example.avro")]
;  (doall (seq adf)))

;(s/defrecord TestRecord [a :- s/Str b :- s/Str])

;TODO build schema from all events namespaces? or do it dynamically on write?
;TODO defevent and defcommand to also define serde?

(def h2-event-schema
  "CREATE TABLE session_store (\n
session_id VARCHAR(36) NOT NULL,\n
idle_timeout BIGINT DEFAULT NULL,\n
absolute_timeout BIGINT DEFAULT NULL,\n
value BINARY(10000),\n
PRIMARY KEY (session_id)\n
)")

;app-id user-id agg agg-id event tx-id dt data


;Should implement IEventStore with store, retrieve-events etc...
(defrecord EventStore [options]
  ;IEventStore
  ;(setup [this) ;setup schema / thread daemon
  ;(store-events [this events])
  ;(get-events [this agg agg-id tx-id & max-tx-id])
  component/Lifecycle
  (start [this]
    (info (str "Starting dev EventStore component with options " options))
    (assoc this :store (atom {})))
  (stop [this]
    (info "Stopping EventStore")
    (dissoc this :store))
  )

(defn build-eventstore
  "Build EventStore in dev or prod mode"
  [config]
  (map->EventStore {:options config}))



(defrecord EventStream [version transactions])


;Enables replaying of events from event-store
;Handles also loading of fixtures if they are given in options -> inserts them in EventStore and EventQueue
;Create fixtures?
;sync / update method: At start-up, asks state-cache version-id - if behind, will replay events
;add a synchronous update! method?
(defrecord EventRepository [options event-store event-bus state-cache]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventRepository component with options " options) )
    (assoc this :event-store event-store
                :event-bus event-bus))
  (stop [this]
    (info "Stopping EventRepository")
    this))

(defn insert-events
  "Insert events in event-store and publish them via event-bus"
  [event-repository aggid events]
  ;Save events
  (swap! (get-in event-repository [:event-store :store])
         update-in [aggid] concat events)
  ;Publish events
  (doseq [event events]
    (publish (-> event-repository :event-bus :topic) event :encoding :fressian))
  )

(defn load-events
  "Load events from event repository for given aggregate, since given version"
  [event-repo aggid version]
  (filter #(>= version (:version %)) (get @(get-in event-repo [:event-store :store]) aggid))
  )

(defn build-eventrepo [config]
  (map->EventRepository {:options config}))

;TODO EventStore schema
;application-id agg agg-id version received-at created-at cmd-id user-id event (nippy?)

;================================================================================
; Event pub/sub
;================================================================================

(defrecord EventBus [options]
  component/Lifecycle
  (start [this]
    (info (str "Starting EventBus component with options " options) )
    (assoc this :topic (topic "events"))
    )
  (stop [this]
    (info "Stopping EventBus")
    (stop (:topic this))
    this))

(defn build-eventbus [config]
  (map->EventBus {:options config}))

