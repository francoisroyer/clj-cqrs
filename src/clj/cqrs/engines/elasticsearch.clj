(ns cqrs.engines.elasticsearch
  "Elasticsearch store component
  Implements EventService - bulk load when reseeding, query etc...
  Calls IRecord functions from events retrieved from an event-bus topic
  "
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [com.stuartsierra.component :as component :refer [using]]
    [clojurewerkz.elastisch.rest :as esr]
    [clojurewerkz.elastisch.rest.index :as esi]
    [clojurewerkz.elastisch.rest.document :as esd]
    [clojurewerkz.elastisch.rest.response :as esrsp]
    [clojurewerkz.elastisch.rest.bulk :as esb])
  (:import org.elasticsearch.node.NodeBuilder)
  )

(defrecord ElasticsearchIndexEngine [options event-bus]
  component/Lifecycle
  (start [this]
    (info (str "Starting ElasticsearchIndexStore with options " options) )
    (if-let [node (:node this)] ; already started
      this
      (if (:local options) ;FIXME local only works - otherwise component returns nil
        (let [node (.node (.. NodeBuilder nodeBuilder))
              conn (esr/connect (:path options))]
            ;(esi/create conn "dev" :mappings mapping-types) ;add mappings to "dev" index
          (assoc this :node node
                      :conn conn)))
      ))
  (stop [this]
    (if-let [node (:node this)]
      (do
        (info "Stopping ElasticsearchIndexStore.")
        (.close node)
        (dissoc this :conn :node))
      this))
  )

(defn build-index-engine [config]
  (map->ElasticsearchIndexEngine {:options (:elasticsearch config) })
  )


(defn index-event [store event]
       (let [index "stats"
             conn (:conn store)
             ]
         (assert (not (nil? conn)))
         ;Create index if not exists
         (when-not (esi/exists? conn index)
           (info "Creating index")
           (esi/create conn index :mappings {"record" {:properties {:dataset-uuid {:type "string" :analyzer "keyword"}}}})
           )
         ;(let [collection (for [record collection]
         ;                   (assoc record :dataset-uuid (:uuid dataset)))
         ;      results (esb/bulk-with-index-and-type conn index "record" (esb/bulk-index collection) :refresh true)]
         ;  (dissoc results :items) ;remove items from result set from ES
         ;  )
         (info (esd/create conn index "record" event))
         )
       )
