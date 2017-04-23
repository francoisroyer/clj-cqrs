(ns ring.middleware.assets
  "Middleware to route requests to webjars or cljsjs assets."
  (:import [org.webjars WebJarAssetLocator]
           [java.util.jar JarFile])
  (:require [ring.middleware.head :as head]
            [ring.util.codec :as codec]
            [ring.util.request :as req]
            [ring.util.response :as resp]
            [clojure.java.io :as io]
            ))

(def ^:private webjars-pattern
  #"META-INF/resources/webjars/([^/]+)/([^/]+)/(.*)")

(defn- new-asset-path [prefix webjar resource]
  (let [[_ name version path] (re-matches webjars-pattern resource)]
    (if (= name webjar)
      (str prefix "/" path)
      (str prefix "/" name "/" path))
    ))

(defn- new-asset-map [^WebJarAssetLocator locator prefix webjar]
  (->> (.listAssets locator "")
       (map (juxt (partial new-asset-path prefix webjar) identity))
       (into {})))

(defn- request-path [request]
  (codec/url-decode (req/path-info request)))


(defn wrap-rename-webjars
  [handler prefix webjar]
   (let [assets (new-asset-map (WebJarAssetLocator.) prefix webjar)]
     (fn [request]
       (if (#{:head :get} (:request-method request))
         (if-let [path (assets (request-path request))]
           (-> (resp/resource-response path)
               (head/head-response request))
           (handler request))
         (handler request)))))

(defn- asset-path [prefix resource]
  (let [[_ name version path] (re-matches webjars-pattern resource)]
    (str prefix "/" name "/" path)))

(defn- asset-map [^WebJarAssetLocator locator prefix]
  (->> (.listAssets locator "")
       (map (juxt (partial asset-path prefix) identity))
       (into {})))

(defn wrap-webjars
  ([handler]
   (wrap-webjars handler "/assets"))
  ([handler prefix]
   (let [assets (asset-map (WebJarAssetLocator.) prefix)]
     (fn [request]
       (if (#{:head :get} (:request-method request))
         (if-let [path (assets (request-path request))]
           (-> (resp/resource-response path)
               (head/head-response request))
           (handler request))
         (handler request))))))

(def ^:private cljsjs-pattern
  #"cljsjs/([^/]+)/([^/]+)/(.*)")

(defn- cljsjs-asset-path [prefix resource]
  (if-let [[_ name type path] (re-matches cljsjs-pattern resource)]
    (str prefix "/" name "/" path)))

(defn cljsjs-asset-map
  "Build map of uri to classpath uri.
  Finds all resources in cljsjs classpath prefix and parses those paths
  to build the map."
  [prefix]
  (->> (.getResources (.getContextClassLoader (Thread/currentThread)) "cljsjs")
       enumeration-seq
       (mapcat
         (fn [url]
           (if (= "jar" (.getProtocol url))
             (let [[_ jar] (re-find #"^file:(.*\.jar)\!/.*$" (.getPath url))]
               (->> (enumeration-seq (.entries (JarFile. (io/file jar))))
                    (remove #(.isDirectory %))
                    (map #(.getName %))
                    (filter #(.startsWith % "cljsjs")))))))
       set
       (keep (juxt (partial cljsjs-asset-path prefix) identity))
       (into {})
       ))

(cljsjs-asset-map "/cljsjs")
(->> (.getResources (.getContextClassLoader (Thread/currentThread)) "cljsjs")
     enumeration-seq)

(defn wrap-cljsjs
  ([handler]
   (wrap-cljsjs handler nil))
  ([handler {:keys [prefix]
             :or {prefix "/cljsjs"}}]
   (let [assets (cljsjs-asset-map prefix)]
     (fn [request]
       (if (#{:head :get} (:request-method request))
         (if-let [path (assets (request-path request))]
           (-> (resp/resource-response path)
               (head/head-response request))
           (handler request))
         (handler request))))))