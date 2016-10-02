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

;Aggregate Root - entity - should hold child entities too (or subsets if paginated)
;TODO avoid another record?? Use multimethods instead - one method per invariant/command?
(s/defrecord Test [id :- s/Num
                   name :- s/Str
                   created-at :- s/Str])
;TODO Aggregate should refer to AggregateRepository, use save method from repo to persist itself

;Add business logic here (defmulti my-logic-function [aggrepo aggid agg])
;Dispatch on aggrepo - default is in-memory - for jdbc: insert/update/delete OR append/transform/delete

(defn create-test
  "Create a simple test"
  [agg])

;Multimethod? load TestAggregateRoot from command perform method??
;NO
(defn load-aggregate-root [agg-name agg-repo])

;Invariants should be implemented here - and use aggrepo methods (on disk / remote)
; or aggregate root values directly if they fit in memory

;may or may not load full aggregate from repo to enforce invariant
;(defmethod check-test-ok :default [agg-repo]
; (let [agg (get agg-repo add id)] ;do something on agg - or query/work on child entity
;))

;TODO implement pagination on child entities for queries on aggregates?


;================================================================================
; Commands and events
;================================================================================

(defrecord TestEvent [aggid version
                      id created-at message]
  IEvent
  (apply-event [this agg] ;check agg version increment ;should be given aggrepo instead - return a function? arity for in-memory/jdbc?
    ;TODO handle InfinispanAggregateRepository, H2AggregateRepository etc...
    (assoc agg :state :tested
               :last-tested-at created-at)
    )
  )

;(defmethod apply-event [:TestEvent :h2] [event agg-with-repo])
;(defmethod apply-event [:TestEvent :atom] [event agg-with-repo])

;TODO defmethod instead -> default provided (remove keys aggid and version)

(def TestCommandView {:fields [{:name "message"
                                :label "Your message"
                                :description "Type your message here."}]
                      :prompt "Test your deployment by sending a message"
                      :links [{:rel "event" :href "/events/TestEvent"}]})

(s/defrecord TestCommand [message :- s/Str]
  ICommand
  (get-aggregate-id [this] :test) ;should be moved out?
  ;(is-available [this agg]) ;if available, will be inserted in :links attribute for entity
  (perform [command agg aggid version]  ;change state to agg object - may be on disk!!! - add custom queries here, e.g. count max projects
    ;(when (:state agg)
    ;  (throw (Exception. "Already started")))
    ;(when (check-test-ok agg-with-repo-pointer etc...) ...)
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
           (GET "/tests" []
                :query-params [{page :- s/Num 1}
                               {pagesize :- s/Num 10}]
                :return {:tests [(sch Test)] :links [{:rel s/Str :href s/Str}]}
                :summary "Browse tests"
                (ok {:tests [] :links [{:rel "createTest" :href "/tests/createTest"}]}))
           (GET "/tests/:id" []
                :path-params [id :- s/Str]
                :return {:links [{:rel s/Str :href s/Str}]}
                :summary "Returns requested entity and its available commands."
                (ok {:links [{:rel "cancelTest" :href (str "/test/" id "/cancelTest")}]}))
           (GET "/tests/createTest" []
                :summary "Returns info on how to create a test."
                (ok TestCommandView))  ;TODO handle text/html and text/plain types?
           (POST "/tests/createTest" []
                 :responses {202 {:schema (sch CommandAccepted) :description "Command accepted"}}
                 :body [cmd (describe (sch TestCommand) "Simple system test")]
                 :summary "Create a simple test to make sure all components work"
                 (accept-command cmd-bus (map->TestCommand cmd))
                 )
           ))

(defn build-test-domain
  "Builds entity tables according to schema and exposes domain routes to main system"
  [config])

;BUXFER
;/api/login?userid=john@doe.com&password=dohdoh

;/api/records/download

;/api/records/addRecord  ;rate limited - otherwise use ingest service
;POST PARAMS: format=sms&text=paycheck +4000 acct:Checking status:pending

;/api/records/uploadStatement ;embed file
;/api/records/importStatement ; point to file URL
;accountID, statement, dateFormat="MM/DD/YYYY"

;/api/transactions
;accountId OR accountName
;tagId OR tagName
;startDate AND endDate OR month: date can be specified as "10 feb 2008", or "2008-02-10". month can be specified as "feb08", "feb 08", or "feb 2008".
;budgetId OR budgetName
;contactId OR contactName
;groupId OR groupName
;links: addTransaction, uploadStatement, addTags, removeTags, markAs...

;/api/transactions/addTags {:ids [] :tags ["Food"]}  ;Create Tag if not exist, insert data events
;/api/transaction/:id/addTag {:tags ["Food"]}
;/api/transaction/:id/markAsTransfer {:from nil :to nil}
;/api/transaction/:id/markAsRecurring {:until nil}
;/api/account/:id/addTag?transactionId=uuid
;{:tag "Food" :rule true :match ""}

;Lambda architecture for transactions??
;Query service merges query to lists or aggregates in ES (inserts in batch) AND domain events (tags, rules)

;/api/accounts
;/api/loans
;/api/tags
;/api/budgets
;/api/reminders
;/api/groups

;/api/contacts
;/api/contacts/inviteNewContact
;/api/contact/:id/remove
