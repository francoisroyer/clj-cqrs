(ns cqrs.layout
  (:require [reagent.core :as r]
            [cljsjs.react-grid-layout]))

(defn onLayoutChange [on-change prev new]
      ;; note the need to convert the callbacks from js objects
      (on-change prev (js->clj new :keywordize-keys true)))

(defn GridItem [props data]
      [:div "hello"]
      )

(defn Grid
      [args]
      (r/create-class
        ;; probably dont need this..
        {:should-component-update
         (fn [this [_ old-props] [_ new-props]]
             ;; just re-render when data changes and width changes
             (or (not= (:width old-props) (:width new-props))
                 (not= (:data old-props) (:data new-props))))
         :reagent-render
         (fn [{:keys [id data width row-height cols item-props on-change empty-text] :as props}]
             [:div.grid-container
              ;(if (seq data)
                ;(into [:> js/ReactGridLayout {:id id
                ;                              :cols cols
                ;                              :initial-width width
                ;                              :row-height row-height
                ;                              :draggableHandle ".grid-toolbar"
                ;                              :draggableCancel ".grid-content"
                ;                              ;:onLayoutChange (partial onLayoutChange on-change data)
                ;                              }]
                ;      (mapv (partial GridItem item-props) data))
             [:> js/ReactGridLayout {:id id
                                              :cols cols
                                              :initial-width width
                                              :row-height row-height
                                              :draggableHandle ".grid-toolbar"
                                              :draggableCancel ".grid-content"
                                              ;:onLayoutChange (partial onLayoutChange on-change data)
                                              } [[:div "hello"]]]
                ;[EmptyGrid {:text empty-text}])
              ])}))

(def layout [{:i "a" :x 0 :y 0 :w 1 :h 2 :static true},
             {:i "b" :x 1 :y 0 :w 3 :h 2 :minW 2 :maxW 4}
             {:i "c" :x 4 :y 0 :w 1 :h 2}])

;(def dashboard-content
;  [:div#dashboard-content
;   (if-not (blank? dashboard)
;           ;; returns the seq of maps as described above
;           (let [widgets (grid-widgets widget-spec dashboard-ent)]
;                [SlidePanel {:width slide-width
;                             :on-toggle #(dispatch [:toggle-widget-selector])
;                             :toggler [IconButton {:text "Widgets"
;                                                   :icon (if @slide-opened?
;                                                           "fa fa-caret-right"
;                                                           "fa fa-caret-left")}]
;                             :opened? @slide-opened?}
;                 (when (seq widgets)
;                       ;; this is my custom wrapper component above with the props I needed
;                       [Grid {:id "dashboard-widget-grid"
;                              :cols 12
;                              :width width ;<determined dynamically>
;                              :row-height (/ height rows)
;                              :data widgets
;                              :on-change handle-layout-change ;; persistance to backend
;                              :item-props {:class "widget-component"}}])
;                 [WidgetSelector {:data widget-spec}]])
;           [NoDashboardSelected])
;  ]
;  )

'(defn dashboard-content []
  [:div#dashboard-content
   [Grid {:id "dashboard-widget-grid"
          :cols 12
          :width 300
          :row-height (/ 300 2)
          :data layout
          ;:on-change handle-layout-change ;; persistance to backend
          :item-props {:class "widget-component"}}]])

(def GridLayout (r/adapt-react-class js/ReactGridLayout))

(defn dashboard-content []
      [:div
       [GridLayout {:className "layout"
                    :layout layout
                    :cols 12
                    :draggableHandle ".grid-toolbar"
                    :draggableCancel ".grid-content"
                    :rowHeight 100 ;row-height
                    :width 1200
                    ;:onLayoutChange #(onLayoutChange state layout %)
                    }
        [:div.box.react-grid-item {:key "a"} [:div.box-header.with-border.grid-toolbar [:h3.box-title "Panel a"]] [:div.box-body.grid-content "This is content" [:span.react-resizable-handle]]]
        [:div.box.react-grid-item {:key "b"} [:div.box-header.with-border.grid-toolbar [:h3.box-title "Panel b"]] [:div.box-body.grid-content "This is content" [:span.react-resizable-handle] ]]
        ]])

