(ns cqrs.core.ws
  (:require [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)] ; <--- Add this
            [taoensso.sente.packers.transit :as sente-transit]
    )
  (:require-macros [taoensso.encore :as encore]
                   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
  ))



(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   :packer (sente-transit/get-transit-packer)
                                   })]
     (def chsk       chsk)
     (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
     (def chsk-send! send-fn) ; ChannelSocket's send API fn
     (def chsk-state state)   ; Watchable, read-only atom
     )

;(defn event-msg-handler [event] (println event) )

;(sente/start-chsk-router! ch-chsk event-msg-handler)
(defmulti -event-msg-handler
          "Multimethod to handle Sente `event-msg`s"
          :id ; Dispatch on event-id
          )

(defn event-msg-handler
      "Wraps `-event-msg-handler` with logging, error catching, etc."
      [{:as ev-msg :keys [id ?data event]}]
      (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
           :default ; Default/fallback case (no other matching handler)
           [{:as ev-msg :keys [event]}]
           (println "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
           [{:as ev-msg :keys [?data]}]
           (let [[old-state-map new-state-map] (encore/have vector? ?data)]
                (if (:first-open? new-state-map)
                  (println "Channel socket successfully established!: %s" new-state-map)
                  (println "Channel socket state change: %s"              new-state-map))))

(defmethod -event-msg-handler :chsk/recv
           [{:as ev-msg :keys [?data]}]
           (println "Push event from server: %s" ?data))

(defmethod -event-msg-handler :chsk/handshake
           [{:as ev-msg :keys [?data]}]
           (let [[?uid ?csrf-token ?handshake-data] ?data]
                (println "Handshake: %s" ?data)))

(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! []
      (stop-router!)
      (reset! router_
              (sente/start-client-chsk-router!
                ch-chsk event-msg-handler)))

(defn ^export test-ws []
      (chsk-send! ; Using Sente
        ;[:taoensso.sente/nil-uid {:name "Rich Hickey" :type "Awesome"}] ; Event
        [:example/test-rapid-push]
        1000 ; Timeout
        ;; Optional callback:
        (fn [reply] ; Reply is arbitrary Clojure data
            (if (sente/cb-success? reply)
              (println reply)
              (println (str "error: " reply))
              )))
      )
