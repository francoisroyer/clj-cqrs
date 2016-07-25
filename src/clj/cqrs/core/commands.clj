(ns cqrs.core.commands
  (:require
    [taoensso.timbre :refer [log trace debug info warn error]]
    [immutant.messaging :refer [publish]]
    [schema.core :as s]
    )
  (:gen-class)
  )

;(defmulti apply-event (fn [state event] (keyword (.getSimpleName (type event))) ))

(defprotocol IEvent
  (apply-event [event state])
  )

(s/defrecord CommandAccepted [id :- s/Num
                              message :- s/Str
                              _links :- {:status s/Str} ])

(defprotocol ICommand
  (get-aggregate-id [this])
  (perform [command state aggid version]))

(defn sch [r] (last (last (schema.utils/class-schema r))))

(defn accept-command [q cmd]
  (let [uuid (str (java.util.UUID/randomUUID))]
    (publish q (assoc cmd :uuid uuid) :encoding :fressian)
    ;Return error 503 if queue unavailable
    {:id uuid :message "Command accepted" :_links {:status (str "/command/" uuid "/status")}}
    ))

