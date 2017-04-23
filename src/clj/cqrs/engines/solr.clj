(ns cqrs.engines.solr
  "
  REFERENCES
  https://cwiki.apache.org/confluence/display/solr/Other+Parsers
  "
  (import [org.apache.solr.client.solrj SolrResponse]
          [org.apache.solr.common.util NamedList SimpleOrderedMap]
          [org.apache.solr.common SolrDocumentList SolrDocument]
          [org.apache.solr.common SolrInputDocument]
          [java.util ArrayList]
          [org.apache.solr.client.solrj SolrClient]
          [org.apache.solr.client.solrj.impl HttpSolrClient]
          [java.io File]
          [org.apache.solr.client.solrj.embedded EmbeddedSolrServer]
          [org.apache.solr.core CoreContainer]
          [java.nio.file Paths]
          [java.net URI]
          [org.apache.solr.common.params MultiMapSolrParams]
          [org.apache.solr.client.solrj.request QueryRequest]
          [org.apache.solr.client.solrj SolrRequest$METHOD]))


;TODO time series engine with chronix?

;================================================================================
; Embedded Solr server
;================================================================================

(defn- str->path [str-path]
  (-> str-path File. .toURI Paths/get))

(defn create-core-container
  "Creates a CoreContainer from a solr-home path and solr-config.xml path
   OR just a solr-home path.
   If the latter is used, $home/$solr.xml works well, as the new
   core.properties discovery mode.
   See: org.apache.solr.core.CoreLocator
        and
        http://wiki.apache.org/solr/Core%20Discovery%20(4.4%20and%20beyond)
   Note: If using core.properties only, it is required to call (.load core-container)
         before creating the EmbeddedSolrServer instance."
  ([^String solr-home]
   (CoreContainer. solr-home))
  ([^String solr-home-path ^String solr-config-path]
   (CoreContainer/createAndLoad
     (str->path solr-home-path)
     (str->path solr-config-path))))


(defn create [^CoreContainer core-container core-name]
  {:pre [(some #(% core-name) [string? keyword?])]}
  (EmbeddedSolrServer. core-container (name core-name)))



(defn http-create [base-url core-name]
  (HttpSolrClient. (str base-url "/" (name core-name))))


;================================================================================
; Query
;================================================================================

(def method-map
  {:get SolrRequest$METHOD/GET
   :post SolrRequest$METHOD/POST})

(defn- format-param [p]
  (if (keyword? p) (name p) (str p)))

(defn- format-values [v]
  (into-array (mapv format-param (if (coll? v) v [v]))))

(defn- create-solr-params [m]
  (MultiMapSolrParams.
    (reduce-kv (fn [^java.util.HashMap hm k v]
                 (doto hm
                   (.put (format-param k)
                         (format-values v))))
               (java.util.HashMap.) m)))

(defn create-query [query options]
  (create-solr-params (assoc options :q query)))

(defn create-query-request
  ([params]
   (create-query-request nil params))
  ([path params]
   (create-query-request :get path params))
  ([method path params]
   {:pre [(or (nil? path) (re-find #"^\/" (str path)))
          (get method-map method)]}
   (doto
     (QueryRequest. (create-solr-params params) (get method-map method))
     (.setPath path))))

;(create-query-request {:q "*:*"})

;================================================================================
; Convert
;================================================================================

(defmulti ->clojure class)

(defmethod ->clojure NamedList [^NamedList obj]
  (into {} (for [[k v] obj] [(keyword k) (->clojure v)])))

(defmethod ->clojure ArrayList [obj]
  (mapv ->clojure obj))

(defmethod ->clojure SolrDocumentList [^SolrDocumentList obj]
  (merge
    {:numFound (.getNumFound obj)
     :start (.getStart obj)
     :docs (mapv ->clojure (iterator-seq (.iterator obj)))}
    (when-let [ms (.getMaxScore obj)]
      {:maxScore ms})))

(defmethod ->clojure SolrDocument [^SolrDocument obj]
  (reduce
    (fn [acc f]
      (assoc acc (keyword f) (->clojure (.getFieldValue obj f))))
    {}
    (.getFieldNames obj)))

(defmethod ->clojure SolrResponse [^SolrResponse obj]
  (->clojure (.getResponse obj)))

(defmethod ->clojure SolrInputDocument [^SolrInputDocument obj]
  (reduce
    (fn [acc o]
      (assoc acc (keyword o) (.getFieldValue obj o)))
    {}
    (.getFieldNames obj)))

(defmethod ->clojure java.util.LinkedHashMap [obj]
  (into {} (for [[k v] obj] [(keyword k) (->clojure v)])))

(defmethod ->clojure :default [obj]
  obj)

;No matching ctor found for class org.apache.solr.common.SolrInputDocument,
'(defn create-doc ^SolrInputDocument [document-map]
  (reduce-kv (fn [^SolrInputDocument doc k v]
               (if (map? v)
                 (let [m (doto (java.util.HashMap.)
                           (.put (name (key (first v))) (val (first v))))]
                   (doto doc (.addField (name k) m))
                   doc)
                 (doto doc (.addField (name k) v))))
             (SolrInputDocument.) document-map))

;================================================================================
; Client
;================================================================================

(defn query [^SolrClient solr-client query & [options]]
  (->clojure (.query solr-client (create-query query options))))


;(defn filter-query [^SolrClient solr-client query & [options]]
;  (->clojure (.query solr-client (create-query query options))))

(defn request [^SolrClient solr-server request]
  (->clojure (.request solr-server request)))

(defmulti add
          (fn [_ input & _]
            (cond
              (map? input) :one
              :else :default)))

(defmethod add :one [^SolrClient client doc & {:as opts}]
  (->clojure (.add client (create-doc doc))))

(defmethod add :default [^SolrClient client docs & {:as opts}]
  (->clojure (.add client ^java.util.Collection (map create-doc docs))))

(defn commit [^SolrClient client & {:as opts}]
  (->clojure (.commit client)))


;bin/solr create -c collection1 -d basic_configs
;OR
;bin/solr start -e schemaless ;creates a gettingstarted collection
;(def conn (http-create "http://localhost:8983/solr" :gettingstarted))
;;
;(add conn [{:id 1 :price 1.0} {:id 2 :price 2.0} {:id 3 :price 3.0}] )
;(add conn [{:id 4 :name "France"} {:id 5 :name "vendorA" :parent_id "4"} {:id 6 :name "product1" :parent_id "5"}])
;(add conn [{:id 7 :name "USA"} {:id 8 :name "vendorB" :parent_id "7"} {:id 9 :name "product2" :parent_id "8"}])
;(commit conn)
;(query conn "*:*")
;(query conn "*:*" {:facet true :facet.field ["price"] })
;(query conn "*:*" {:fq "id:[5 TO *]"} )
;(query conn "*:*" {:fq "{!graph from=id to=parent_id}id:\"6\"" :facet true :facet.field ["price"] } )
;(query conn "*:*" {:fq "{!graph from=parent_id to=id}id:\"4\"" :facet true :facet.field ["price"] } )
;
;
;(add conn [{:id "customer/1" :type "Customer" :name "Cdiscount"}
;           {:id "quote/1" :type "Quote" :customer "customer/1" :date "2016/1/1" :fullprice 5000.0}
;           {:id "invoice/1" :type "Invoice" :quote "quote/1" :date "2016/1/15" :amount 1500.0}
;           {:id "invoice/2" :type "Invoice" :quote "quote/1" :date "2016/2/15" :amount 3500.0}
;           ])
;(commit conn)
;(query conn "*:*")
;(query conn "*:*" {:fq "{!graph from=quote to=id}id:\"quote/1\""})
;(query conn "*:*" {:fq "{!graph from=quote to=id}id:\"quote/1\""
;                   :facet.query "{!graph from=quote to=id}id:\"quote/1\""
;                   :facet true :facet.field ["amount" "quote"] } )
;
;(query conn "*:*" {:fq "{!graph from=quote to=id}id:\"quote/1\""
;                   :facet.query "{!graph from=quote to=id}id:\"quote/1\""
;                   :facet true :facet.pivot ["customer,id" "quote,amount"] } ) ;quote-invoice hierarchy
