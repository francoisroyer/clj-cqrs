(ns cqrs.domains.test
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [schema.core :as s]
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [cqrs.core.api :refer :all]
    [cqrs.core.commands :refer :all]
    [cqrs.core.events :refer :all]
    ))

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

;TODO should call relevant methods on state / agg! Should agg emit events? Here -> call (test) method?
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


;TODO represent TestCommand with GET -> what fields are needed, what events are emitted
