(ns cqrs.widgets.map
  (:require [reagent.core :as r]
            [ajax.core :as ajax]
            cljsjs.leaflet)
  )

(def geomjson "{}")

(defn draw-map [id]
      (let [m (.setView (.map js/L id) #js [43.60 1.44] 13)]

           (.addTo (.tileLayer js/L
                               ;"http://a.tile.openstreetmap.org/{z}/{x}/{y}.png"
                               ;"http://tile.stamen.com/toner/{z}/{x}/{y}.png"
                               "http://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
                               (clj->js {:attribution "&copy; <a href='http://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors, &copy; <a href='http://cartodb.com/attributions'>CartoDB</a>"
                                         :maxZoom 18}))
                   m)
           m
           ))

;(def query (r/atom (apply str (map #(str % "\n") q1 ) ) ))

(defn geomap []
      (r/create-class
        {:component-did-mount (fn [this]
                                  (let [m (draw-map "map-container")]
                                       ;(ajax/GET "/data/bornes-wi-fi.geojson"
                                       ;          {:handler         (fn [_ resp]
                                       ;                                (let [features (js/jQuery.parseJSON resp)]
                                       ;                                     (.addTo (.geoJson js/L features) m)))
                                       ;           :error-handler   #(println %1)
                                       ;           :response-format :json
                                       ;           ;:keywords?       true
                                       ;           })
                                       )
                                  )
         :render              (fn [this]
                                  [:div.box
                                   [:div.box-body
                                    [:div#map-container {:class "widget"
                                                         :style {"height" "100%" "width" "100%"}
                                                         }]
                                    ]]
                                  )
         }
        )
      )

;TODO subscribe to events
;TODO when mounting component, insert leaflet CSS in document
;TODO When unmounting, remove CSS