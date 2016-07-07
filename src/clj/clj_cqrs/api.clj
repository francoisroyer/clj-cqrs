(ns clj-cqrs.api
  "
  DESCRIPTION
  REST api to drive model learning, operation and maintenance.
  Embeds an H2 instance in dev mode.
  Use Event-sourcing to maintain state.
  TODO deployable as Immutant app or stand-alone?

  REFERENCES
  http://www.jayway.com/2013/04/02/event-sourcing-in-clojure/
  "
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [schema.core :as s]
    [compojure.api.sweet :refer :all]
    [immutant.messaging :refer [publish]]
    [immutant.codecs :refer [make-codec register-codec]]
    [immutant.codecs.fressian :refer [register-fressian-codec]]
    [clj-cqrs.domains.model :refer [model-routes]]
    )
  )

(register-fressian-codec)


;================================================================================
; API
;================================================================================

;Create: datasources, datasets, models, jobs (batch/stream)
;Use CQRS + event sourcing

;TODO http-kit + channel to return async response from Command - insert uuid in Command? Sent to command queue.
;OR have client open channel to server and subscribe to all Events for his topics (owned resources?) or current resources

;async=false => Generate uuid for command, go block => listen to result of command?

;(defroutes-cqrs model-routes
; Model
; "models"
; :get Model  ;for put and post
; :create CreateModelCommand
; :update UpdateModelCommand
; :train ModelTrainCommand
; :copy ModelCopyCommand
; )

;Entity
;Event
;Property
;Relationship/Link
;Document/Record

;Datasource

(defn app-routes [cmdqueue db]
   (api
     {:swagger
      {:ui   "/api-docs"
       :spec "/swagger.json"
       :data {:info {:title       "Sample API"
                     :description "Compojure Api example"}
              :tags [{:name "api", :description "some apis"}]}}}
     (model-routes cmdqueue db)
     ;ui/routes
     ))


(defrecord ApiComponent [options command-queue state-cache]
  component/Lifecycle
  (start [this]
    (info (str "Starting API with options " options))
    (let [app (app-routes command-queue nil)
          ]
      (assoc this :handler (:handler app) )))
  (stop [this]
    (info "Stopping API")
    this))


(defn build-api [config]
  (map->ApiComponent {:options config}))

;Models
;Lifecycles
;Workflows
;Datasets/Datasources/Connectors
;Schemas
;Topologies
;Tools
;Apps

;TODO POST to create model -> redirect? 202? Give feedback on entity/aggregate affected -> in progress etc...
;TODO anglican calculator? Bayesian changepoint? BRL?


