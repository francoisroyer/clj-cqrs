(ns cqrs.core
  (:require [reagent.core :as reagent :refer [atom]]
            [com.stuartsierra.component :as component]
            [secretary.core :as secretary :include-macros true]
            [datascript.core :as d]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react]
            [kioo.reagent :refer [content set-attr do-> substitute listen]]
            [re-frame.core :as re-frame]
            [ajax.core :as ajax]
            [cqrs.widgets.map :refer [geomap]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)] ; <--- Add this
            [cqrs.layout :refer [dashboard-content]]
            )
  (:require-macros [reagent.ratom  :refer [reaction]]
                   [taoensso.encore :as encore :refer-macros (have have?)]
                   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
                   [kioo.reagent :refer [defsnippet deftemplate]])
  (:import goog.History)
  )

(enable-console-print!)

(comment
  ;;================================================================================
  ;; Re-frame subscriptions
  ;;================================================================================

  (re-frame/register-sub
    :search-input
    (fn [db]
        (reaction (:search-input @db))))

  (re-frame/register-sub        ;; a new subscription handler
    :phones             ;; usage (subscribe [:phones])
    (fn [db]
        (reaction (:phones @db))))  ;; pulls out :phones

  ;;================================================================================
  ;; Re-frame handlers
  ;;================================================================================

  ;TODO client can now be a view to server event source!

  (re-frame/register-handler
    :process-phones-response
    (fn
      ;; store the response of fetching the phones list in the phones attribute of the db
      [app-state [_ response]]
      (assoc-in app-state [:phones] response)))

  (re-frame/register-handler
    :process-phones-bad-response
    (fn
      ;; log a bad response fetching the phones list
      [app-state [_ response]]
      app-state))

  (re-frame/register-handler
    :load-phones
    (fn
      ;; Fetch the list of phones and process the response
      [app-state _]
      (ajax/GET "phones/phones.json"
                {:handler         #(re-frame/dispatch [:process-phones-response %1])
                 :error-handler   #(re-frame/dispatch [:process-phones-bad-response %1])
                 :response-format :json
                 :keywords?       true})
      app-state))

  (re-frame/register-handler
    :set-active-panel
    (fn [db [_ active-panel]]
        (assoc db :active-panel active-panel)))

  ;;================================================================================
  ;; Routes
  ;;================================================================================

  (defroute "/" []
            (re-frame/dispatch [:set-active-panel :home-panel]))

  ;;================================================================================
  ;; Views
  ;;================================================================================

  (defn main-panel []
        (let [active-panel (re-frame/subscribe [:active-panel])]
             (fn []
                 [:div
                  [loading-throbber]
                  [user-name-and-avatar]
                  (panels @active-panel)
                  ])))

  (defn mount-root []
        (reagent/render [views/main-panel]
                        (.getElementById js/document "app")))




  ;;================================================================================
  ;; Initialize app
  ;;================================================================================

  (defn init! []
        (hook-browser-navigation!)
        (re-frame/dispatch [:initialise-db])
        (re-frame/dispatch [:load-phones])
        (reagent/render-component [current-page] (.getElementById js/document "app")))
  )

(deftemplate main-page "META-INF/resources/webjars/adminlte/2.3.3/index2.html"
             []
             {[:.logo-lg] (content "fruit")})

(defsnippet direct-chat "META-INF/resources/webjars/adminlte/2.3.3/pages/widgets.html"
            [:.direct-chat-primary]
             []
             {})


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
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

;(chsk-send! ; Using Sente
;  [:some/request-id {:name "Rich Hickey" :type "Awesome"}] ; Event
;  1000 ; Timeout
;  ;; Optional callback:
;  (fn [reply] ; Reply is arbitrary Clojure data
;      (if (sente/cb-success? reply)
;        (println reply)
;        (println (str "error: " reply) )
;        )))

;init database from all loaded components - register their handlers
;connect to remote API and channels if config ready
(defn ^:export init []
      ;(routes/app-routes)
      ;(re-frame/dispatch-sync [:initialize-db])
      ;(mount-root)
      ;(reagent/render-component [main-page] (.getElementById js/document "app"))
      ;(reagent/render-component [direct-chat] (.getElementById js/document "app-container"))
      (reagent/render-component [dashboard-content] (.getElementById js/document "app-container"))
      ;(reagent/render-component [geomap] (.getElementById js/document "app-container"))
      (start-router!)
      )


(defn reload []
      (println "Hello again app!!")
      (init)
      )

(init)
(println "Hello app!!")


;TODO apps datasets connectors lab observatory/dashboards analytics/stats help doc api

;matrix.pwc.com

;AUTH
;accounts
;users
;organizations
;groups
;permissions
;projects

;APPS
;workflows - document or dataset annotation / case handling => inbox with PJ, comments, refs...
;reference documents/datasets
;connectors
;services for publishing etc

;DATA HUB
;connectors - connectors to remote sources - can query/import
;catalogs -> contains collections
;collections have taxonomies/categories + tags, contain datasets and documents
;datasets have records and a schema
;documents - may have fields - no schema but mime-type, must be parsed to extract information

;DATASCIENCE LAB
;My projects
;Shared projects
;New notebook

;KNOWLEDGE CENTER
;wiki
;dashboards
;tutorials
;API


;see CKAN/OpenDataSoft, domino, beaker/jupyter,
;lateral.io, predix


