(ns user
 (:require [com.stuartsierra.component :as component]
           [clojure.tools.namespace.repl :refer (refresh)]
           [clojure.repl :refer [pst]]
           [clojure.pprint :refer [pprint]])
 ;(:use ns-tracker.core)
 )

;TODO insert commands via REPL?

;(def modified-namespaces
; (ns-tracker ["dev" "src" "checkouts" "test"]))
;
;(defonce freshstart true)
;
;(def system nil)
;
;(defn init []
; (alter-var-root #'system
;                 (constantly (app/api-system (:config @env)))))
;
;(defn start []
; (alter-var-root #'system component/start))
;
;(defn stop []
; (alter-var-root #'system
;                 (fn [s] (when s (component/stop s)))))
;
;(defn go! []
; ; (delete-recursively "logs") ;clean up logs e.g. for jenkins jobs - ignore if restart in dev from repl
; ; (log/info "Cleaning logs")
; (try
;  (init)
;  (start)
;  nil
;  (catch Throwable e
;   (pst))))
;
;(defn restart! []
; (init)
; (start)
; )
;
;(defn reload []
; (stop)
; (doseq [ns-sym (modified-namespaces)]
;  (log/info (str "Reloading " ns-sym))
;  (if (seq? ns-sym)
;   (require (last ns-sym) :reload)
;   (require ns-sym :reload))
;  )
; (restart!))
