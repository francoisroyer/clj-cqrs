(ns cqrs.domains.model
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [schema.core :as s]
    [compojure.api.sweet :refer :all]
    [ring.swagger.upload :as upload]
    [clj-time.coerce :as c]
    [clj-time.core :as t]
    [cqrs.core.commands :refer :all]
    )
  (:import
    [cqrs.core.commands CommandAccepted]
    [cqrs.core.events IEvent])
  )


(s/defrecord Model [id :- s/Num
                    owner :- s/Str
                    name :- s/Str
                    created-at :- s/Str])

(defrecord ModelCreatedEvent [aggid
                              version
                              id
                              owner
                              name
                              created-at]
  IEvent
  (apply-event [event state]
    (update-in state [:models] assoc (:id event) (dissoc event :aggid)))

  )


(s/defrecord CreateModelCommand [owner :- s/Num
                                 name :- s/Str]
             ICommand
             (get-aggregate-id [this] [:users owner])
             (perform [command state aggid version]
                      (when (< 3 (count (:models state)))
                        (throw (Exception. "Model quota reached.")))
                      (println "CreateModelCommand executed.")
                      (let [new-version (inc version)
                            created-at (c/to-long (t/now))
                            id 0]
                        [(->ModelCreatedEvent aggid new-version
                                              id owner name created-at)]) ))


(defn model-routes [cmdqueue]
  (context "/api" []
           :tags ["Models"]
           (POST "/models/CreateModel" []
                 :responses {202 {:schema (sch CommandAccepted) :description "Command accepted"}}
                 :body [cmd (describe (sch CreateModelCommand) "A new model spec")]
                 :summary "Creates a new model"
                 (accept-command cmdqueue (map->CreateModelCommand cmd) )
                 )
           ;(GET "/models" []
           ;     :query-params [name :- s/Str]
           ;     :return [sch/Model]
           ;     :summary "List or find models"
           ;     (handle-resource db :model))
           ;(GET "/models/:id" []
           ;     :path-params [id :- s/Str]
           ;     :return sch/Model
           ;     :summary "Returns a model"
           ;     (handle-resource db :model))
           ;(POST "/models/:id/TrainModel" []
           ;      :multipart-params [file :- upload/TempFileUpload]
           ;      :middleware [upload/wrap-multipart-params]
           ;      :path-params [id :- s/Str]
           ;      :return sch/CommandStatus
           ;      :body [cmd (describe sch/TrainModelCommand "Train a model from a Dataset or a file")]
           ;      :summary "Train a model against a dataset or datasource"
           ;      (handle-command cmdqueue TrainModelCommand cmd)
           ;      )
           ;(POST "/models/:id/DeployModel" []  ;deploy to spark, onyx, riemann etc...
           ;     :path-params [id :- s/Str]
           ;     :body [cmd (describe sch/DeployModelCommand "A model spec")]
           ;     :summary "Deploy a model"
           ;     (handle-command cmdqueue DeployModelCommand cmd id)
           ;     )
           ;(POST "/models/SearchModels" []
           ;      :return [sch/Model] ;or QueryStatus
           ;      :body [cmd (describe sch/SearchQuery "A query spec")]
           ;      :summary "Search for models"
           ;      (handle-command cmdqueue SearchCommand cmd)
           ;      )
           ;(POST "/models/:id/CopyModel" []
           ;      :path-params [id :- s/Str]
           ;      :return sch/CommandStatus
           ;      :path-params [id :- s/Str]
           ;      :body [cmd (describe sch/CopyModelCommand "A model copy spec")] ;add relationship?
           ;      :summary "Copy an existing model" ;Generates a CreateModelCommand + LinkResourceCommand
           ;      (handle-command cmdqueue SearchCommand cmd)
           ;      )
           ;(GET "/commands/:cmdid/status" []
           ;     :path-params [cmdid :- s/Str
           ;                   ]
           ;     :return sch/CommandStatus
           ;     :summary "Status of command"
           ;     (handle-command db cmdqueue :model id cmd cmdid)  ;check events emitted by command - if error, should not mutate resource - store an CommandErrorEvent
           ;     )
           ))

