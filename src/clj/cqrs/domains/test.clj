(ns cqrs.domains.test
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [schema.core :as s]
    [compojure.api.sweet :refer :all]
    [aggregate.core :as agg]
    [ring.util.http-response :refer :all]
    [cqrs.core.commands :refer :all]
    [cqrs.core.events :refer :all]
    )
  (:import
    [cqrs.core.commands CommandAccepted]
    )
  )

;================================================================================
; Aggregate
;================================================================================

(def er
  "The complete entity-relationship model."
  (agg/make-er-config
    (agg/entity :customer
                (agg/->n :projects :project {:fk-kw :customer_id}))
    ))

;Aggregate entity - should hold child entities too (or subsets if paginated)
(s/defrecord Test [id :- s/Num
                   name :- s/Str
                   created-at :- s/Str])


;================================================================================
; Commands and events
;================================================================================

(defrecord TestEvent [aggid version
                      id created-at message]
  IEvent
  (apply-event [event state] ;should be given aggrepo instead - return a function? arity for in-memory/jdbc?
    (assoc state :state :tested
                 :last-tested-at (:created-at event)))
  )

(def TestCommandInfo {:fields [{:name "message"
                                 :label "Your message"
                                 :description "Type your message here."}]
                       :prompt "Test your deployment by sending a message"
                       :links [{:rel "event" :href "/events/TestEvent"}]})

(s/defrecord TestCommand [message :- s/Str]
  ICommand
  (get-aggregate-id [this] :test) ;should be moved out?
  (perform [command state aggid version]
    ;(when (:state state)
    ;  (throw (Exception. "Already in started")))
    (let [new-version (inc version)
          created-at (make-timestamp)
          id 0]
      (info "TestCommand executed.")
      [(->TestEvent aggid new-version id created-at message)]) ))


;================================================================================
; Routes
;================================================================================

(defn test-routes [cmd-bus]
  (context "/api" []
           :tags ["Tests"]
           (POST "/tests/createTest" []
                 :responses {202 {:schema (sch CommandAccepted) :description "Command accepted"}}
                 :body [cmd (describe (sch TestCommand) "Simple system test")]
                 :summary "Create a simple test to make sure all components work"
                 (accept-command cmd-bus (map->TestCommand cmd))
                 )
           (GET "/tests/createTest" []
                :summary "Returns info on how to create a test."
                (ok TestCommandInfo))
           (GET "/tests" []
                :query-params [{name :- s/Str nil}
                               {page :- s/Num 1}
                               {pagesize :- s/Num 10}]
                :return {:tests [(sch Test)] :links [{:rel s/Str :href s/Str}]}
                :summary "Browse tests"
                (ok {:tests [] :links [{:rel "createTest" :href "/tests/createTest"}]}))
           (GET "/tests/:id" []
                :path-params [id :- s/Str]
                :return {:links [{:rel s/Str :href s/Str}]}
                :summary "Returns requested entity and its available commands."
                (ok {:links [{:rel "cancelTest" :href (str "/test/" id "/cancelTest")}]}))
           ))
