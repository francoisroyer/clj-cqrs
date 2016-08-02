(ns cqrs.core.api
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
    [net.cgrand.enlive-html :as html :refer [substitute attr-has add-class remove-class
                                             do-> before append prepend html deftemplate content last-child
                                             set-attr nth-child nth-of-type]]
    [ring.util.http-response :refer :all]
    [ring.middleware.assets :refer [wrap-webjars wrap-rename-webjars wrap-cljsjs]]
    [ring.middleware.resource :refer [wrap-resource]]
    [immutant.messaging :refer [publish]]
    [immutant.codecs :refer [make-codec register-codec]]
    [immutant.codecs.fressian :refer [register-fressian-codec]]
    [cqrs.core.commands :refer :all]
    [cqrs.domains.model :refer [model-routes]]
    )
  (:gen-class)
  )

(register-fressian-codec)

;(.listAssets (WebJarAssetLocator.))

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

;================================================================================
; SPA UI
;================================================================================

(defn include-encoding-metas []
  (map html [[:meta {:content "text/html;charset=utf-8" :http-equiv "Content-Type"}]
             [:meta {:content "utf-8" :http-equiv "encoding"}]]))

(defn include-js [src]
  (html [:script {:src src}]))

(defn include-script [script]
  (html [:script script]))

(defn include-css [href]
  (html [:link {:type "text/css" :href href :rel "stylesheet"}]))


(def page-info {:title ""
                :logo ""})

(def menu [{:label "Apps"
            :icon "fa fa-cubes"
            :href "#"}
           {:label "Data hub"
            :icon "fa fa-database"
            :submenu [
                      {:label "Sources" :href "#"}
                      {:label "Catalogs" :href "#"}
                      {:label "Feeds" :href "#"}
                      ]}
           {:label "Data lab"
            :icon "fa fa-flask"
            :submenu [
                      {:label "My projects" :href "#"}
                      {:label "Shared projects" :href "#"}
                      {:label "Notebooks" :href "#"}
                      ]}
           {:label "Academy"
            :icon "fa fa-graduation-cap"
            :submenu [
                      {:label "News" :href "#"}
                      {:label "Documentation" :href "#"}
                      {:label "Tutorials" :href "#"}
                      {:label "API" :href "#"}
                      ]}
           {:label "Dashboards" :icon "fa fa-dashboard" :href "#"} ;stats
           ])


(deftemplate basetpl
            "META-INF/resources/webjars/adminlte/2.3.3/index2.html"
             []
             [:head] (append (include-css "css/app.css"))
             ;(content
                       ;(include-encoding-metas)
                       ;(html [:meta {:content "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" :name "viewport"}])
                       ;(html [:title  "CQRS" ])
                       ;(include-favicons)
                       ;(include-js "js/app.js")
                      ; )
             [:body] (do->
                       (remove-class "skin-blue")
                       (add-class "skin-black")
                       (add-class "fixed")
                       ;(content
                       ;  (html [:div#app-loading-img])
                       ;  (html [:div#app])
                       ;  ;(include-libs)
                       ;  ;;(include-script "$(window).load(function() {$(\"#app-loading-img\").fadeOut(\"slow\");});")
                       ;  ;(include-js "js/app.js")
                       ;  ;(include-script "cqrs.core.init();")
                       ;  )
                       (append (include-js "js/dev.js"))
                       )
             ;[:header.header] (content nil)
             [:ul.sidebar-menu] (content (html (for [item menu] [:li [:a {:href "#"}
                                                                      [:i {:class (:icon item)}]
                                                                      [:span (:label item) ]]] ) ))
             [:section.content] (content ;nil
                                      (html [:div.row
                                             [:div.col-md-6
                                              [:div#app-container]
                                              ]])
                                             )
             )

(defn basepage []
  (apply str (basetpl)))

(def ui-routes
    (->
      (routes
        (GET "/app/home" req (basepage))
        (ANY "/*" []
             :responses {404 String}
             (not-found "These aren't the droids you're looking for."))
        )
      (wrap-rename-webjars "/app" "adminlte") ;if request starts with app, route towards adminlte webjar
      ;(wrap-webjars "/assets")
      (wrap-cljsjs)
      (wrap-resource "public")
      )
  )

;TODO any call fo /app/* should be routed to webjar /adminlte


(defn api-routes [cmd-queue cmd-handler]
  (api
    {:swagger
     {:ui   "/api-docs"
      :spec "/swagger.json"
      :data {:info {:title       "Sample API"
                    :description "Compojure Api example"}
             :tags [{:name "api", :description "some apis"}]}}}
    (model-routes cmd-queue)
    (commands-routes cmd-handler)
    ))


(defrecord ApiComponent [options command-queue command-handler]
  component/Lifecycle
  (start [this]
    (info (str "Starting API with options " options))
    (let [app (routes (api-routes command-queue command-handler)
                      ui-routes)
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

;Org: PwC, ext-*
;Groups
;User
;Project


;DATA HUB
;connectors - connectors to remote sources - can query/import - emit datasets or documents in a catalog > collection (or just staging)
;catalogs -> contains collections
;collections have taxonomies/categories + tags, contain datasets and documents
;datasets have records and a schema - map/emit to Entities/Events/Properties...
;documents - may have fields - no schema but mime-type, must be parsed to extract information THEN yield records
;THEN map to Entities/Events/Relationships/Properties

;Taxonomy/ontology fo tagging/labeling

;Catalogue -> images, doc, pdf ... + annotation
;Excel etc -> modification/tag/label
;time series -> flagging / add to case or workbook

;For bots, AI/decision layer etc...
;Rules
;Intents
;Concepts

;Case/workbook/study/focus
;add references, referees, info, tags...

;APPS
;workflows - document or dataset annotation / case handling => inbox with PJ, comments, refs...
;reference documents/datasets
;connectors
;services for publishing etc

;Catalog has collections
;collection has root path/resource and tags and datasets and documents
;a dataset has a type and a resource path, tags and record type/schema, (and parent workbook or job or document)?
;-> could be a record dataset or semantic dataset (Avro)
;a document has a type and a resource path, and tags


;Aggregates - define their load/save methods from cache!
(defrecord Catalog [name owner created-at collectionIds])

(defrecord Collection [name owner created-at connectors resourceIds]) ;==bucket - resources/connectors
;collection can belong to a catalog or a project
;methods/functions: sync, add-dataset, fetch dataset...

(defrecord Project [name owner created-at])

(defrecord Dataset [name owner created-at type schema resource])
;moveToCollection
;get-records
;Avro, time series, 2D image...
;Has dimensions

(defrecord Document [name owner created-at type resource])
;pdf, html ...
;createWorkbook or startSession
;Can refer to a given dimension in dataset


;Input: Command
;Domain object/Aggregate
;output: views

;Ontology
;Taxonomy

;Extract => yields Records
;Mapping => Records to Objects

;Objects: Entities/Properties/Events/Relationships/Documents
;Datasets: Records - mapping onto platform Objects
;Also extract Datasources from Documents (if validated by user?)

;catalogs/createCatalog
;catalog/:id/createCollection  ;remote collection, bucket etc...

;datasources/createUrlDatasource ;emit documents (html)
;datasources/createRestApiDatasource ;emit datasets
;datasource/:id/updateSchedule

;A COLLECTION HAS A COMMON TAXONOMY/ONTOLOGY
;collection/:id/addDatasource ;an API, database...
;collection/:id/syncFromSource
;collection/:id/updateOntology
;collection/:id/importOntology
;collection/:id/setTaxonomy ;{:dataset id}
;collection/:id/createDataset  ;DatasetCreated + DatasetAddedToCollection
;collection/:id/createDocument

;A PROJECT HAS WORKFLOWS AND PROCESSES - AND A SPECIFIC TAXONOMY/ONTOLOGY
;projects/createProject
;project/:id/createDataset
;project/:id/createDocument
;project/:id/importDatasets
;project/:id/importDocuments
;project/:id/createProcess {:started-at x :analyst y :datasets [] :documents []}
;project/:id/activate ;give timestamp to activate it later
;project/:id/deactivate ;give timestamp to deactivate it later

;TODO add mapping/run mapping on dataset: produce objects from records - must validate ontology!

;TODO start process steps for docs in the correct start step
;document/:id/startProcess {:started-at x :analyst y :type :review}
;dataset/:id/startProcess {:started-at x :analyst y :type :analysis}

;TODO alert/rules on objects in project - automatic, unless to auto-commit -> will wait for operator input
;project/:id/addRules
;project/:id/updateRules
;project/:id/removeRules

;notebooks/createNotebook {:started-at x :analyst y :type :notebook}

;analysis/:id/addNote
;notebook/:id/makePublic

;dataset/:id/addNote
;document/:id/addNote
;document/:id/runExtractor ;run or test ETL to generate a dataset OR new Objects
;dataset/:id/applyMappings


;services -> views, saga, stats, coverage, similarity, crawl etc...

;In project:
;CreateDocument -> add Extractor, or refer to parentProject Extractor -> extract new documents from found Resources/Datasources
;DocumentCreated -> run sync/crawler, extract new documents

