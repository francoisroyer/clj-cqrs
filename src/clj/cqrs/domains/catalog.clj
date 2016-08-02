(ns cqrs.domains.catalog
  "Catalog domain - each catalog embeds all its child collections"
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


(s/defrecord Catalog [id :- s/Num
                      owner :- s/Str
                      name :- s/Str
                      created-at :- s/Str])

(defrecord CatalogCreatedEvent [aggid
                                version
                                id
                                owner
                                name
                                created-at]
  IEvent
  (apply-event [event state]
    (update-in state [:catalogs] assoc (:id event) (dissoc event :aggid)))
  ;IDataHubStore
  ;(persist-event [event state]
  ;  (update-in state [:catalogs] assoc (:id event) (dissoc event :aggid)))

  )

;CreateCollectionCommand -> generate CollectionCreated for collection aggregate (new id), and CollectionAddedToCatalog for Catalog agg id


;What should be the root??
(s/defrecord CreateCatalogCommand [owner :- s/Num
                                   name :- s/Str]
             ICommand
             (get-aggregate-id [this] [:users owner])
             (perform [command state aggid version]
                      (when (< 3 (count (:catalogs state)))
                        (throw (Exception. "Catalog quota reached.")))
                      (println "CreateCatalogCommand executed.")
                      (let [new-version (inc version)
                            created-at (c/to-long (t/now))
                            id 0]
                        [(->CatalogCreatedEvent aggid new-version
                                              id owner name created-at)]) ))


;Create/Update/Delete

(defn catalog-routes [cmdqueue db]
  (context "/api" []
           :tags ["Catalogs"]
           (POST "/catalogs/CreateCatalog" []
                 :responses {202 {:schema (sch CommandAccepted) :description "Command accepted"}}
                 :body [cmd (describe (sch CreateCatalogCommand) "A new catalog spec")]
                 :summary "Creates a new catalog"
                 (accept-command cmdqueue (map->CreateCatalogCommand cmd) )
                 )
           ;(GET "/catalogs" []
           ;     :query-params [name :- s/Str]
           ;     :return [sch/Catalog]
           ;     :summary "List or find catalogs"
           ;     (handle-resource db :catalog))
           ;(GET "/catalogs/:id" []
           ;     :path-params [id :- s/Str]
           ;     :return sch/Catalog
           ;     :summary "Returns a catalog"
           ;     (handle-resource db :catalog))
           ;(POST "/catalogs/:id/TrainCatalog" []
           ;      :multipart-params [file :- upload/TempFileUpload]
           ;      :middleware [upload/wrap-multipart-params]
           ;      :path-params [id :- s/Str]
           ;      :return sch/CommandStatus
           ;      :body [cmd (describe sch/TrainCatalogCommand "Train a catalog from a Dataset or a file")]
           ;      :summary "Train a catalog against a dataset or datasource"
           ;      (handle-command cmdqueue TrainCatalogCommand cmd)
           ;      )
           ;(POST "/catalogs/:id/DeployCatalog" []  ;deploy to spark, onyx, riemann etc...
           ;     :path-params [id :- s/Str]
           ;     :body [cmd (describe sch/DeployCatalogCommand "A catalog spec")]
           ;     :summary "Deploy a catalog"
           ;     (handle-command cmdqueue DeployCatalogCommand cmd id)
           ;     )
           ;(POST "/catalogs/SearchCatalogs" []
           ;      :return [sch/Catalog] ;or QueryStatus
           ;      :body [cmd (describe sch/SearchQuery "A query spec")]
           ;      :summary "Search for catalogs"
           ;      (handle-command cmdqueue SearchCommand cmd)
           ;      )
           ;(POST "/catalogs/:id/CopyCatalog" []
           ;      :path-params [id :- s/Str]
           ;      :return sch/CommandStatus
           ;      :path-params [id :- s/Str]
           ;      :body [cmd (describe sch/CopyCatalogCommand "A catalog copy spec")] ;add relationship?
           ;      :summary "Copy an existing catalog" ;Generates a CreateCatalogCommand + LinkResourceCommand
           ;      (handle-command cmdqueue SearchCommand cmd)
           ;      )
           ;(GET "/commands/:cmdid/status" []
           ;     :path-params [cmdid :- s/Str
           ;                   ]
           ;     :return sch/CommandStatus
           ;     :summary "Status of command"
           ;     (handle-command db cmdqueue :catalog id cmd cmdid)  ;check events emitted by command - if error, should not mutate resource - store an CommandErrorEvent
           ;     )
           ))

