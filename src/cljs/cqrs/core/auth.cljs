(ns cqrs.core.auth
  (:require [taoensso.sente :as sente]
            [cqrs.core.ws :refer [chsk-state chsk]]
            )
  )

(defn login [user-id]
  (sente/ajax-lite "/login"
                   {:method  :post
                    :headers {:X-CSRF-Token (:csrf-token @chsk-state)}
                    :params  {:user-id (str user-id)}}

                   (fn [ajax-resp]
                     (println "Ajax login response: %s" ajax-resp)
                     (let [login-successful? true ; Your logic here
                           ]
                       (if-not login-successful?
                         (println "Login failed")
                         (do
                           (println "Login successful")
                           (sente/chsk-reconnect! chsk))))))
  )
