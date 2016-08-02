(ns cqrs.core.events
  "TODO defevent macro including ser/deser in Avro"
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [immutant.messaging :refer :all]
    [immutant.messaging :refer [publish]]
    [schema.core :as s]
    )
  )

(defprotocol IEvent
  (apply-event [event state])
  )

;================================================================================
; CQRS COMPONENTS
;================================================================================

;TODO EventStore - file or DB (PG-hstore, mapDB, H2, Dynamo...)



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

