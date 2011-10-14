(ns mx.interware.cbot.web.views
  (:use [hiccup core page-helpers]
        mx.interware.cbot.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [mx.interware.cbot.ui :as ui]
            [ring.util.response :as ring]))

(defn index-page []
  (html5 [:head
          [:title "CBot 1.0 main"]
          (include-css "/css/style.css")]
         [:body
          [:h1 [:a {:href "/apps"} "Applications"]]]))

(defn break-param [pm]
  (println pm " " (class pm))
  (if-not pm
    ""
    (str "?"
         (reduce
          str
          (map (fn [p]
                 (str (p 0) "=" (p 1))) (first pm))))))

(defn link [dir kw & param-map]
  (let [d-name (if (keyword? kw) (name kw) (str kw))]
    [:tr [:td [:a {:href (str dir d-name (break-param param-map)) :class "mediano"} d-name]]]))

(defn apps-page []
  (let [a (map (partial link "/apps/") (apps))]
    (html5
    [:head
      [:title "CBot 1.0 console"]
      (include-css "/css/style.css")]
    [:body
     [:h1 "CBot 1.0 console (1)"]
     [:table {:border "1"} a] 
     ])))

(defn make-row [app inst-k]
  (let [name (name inst-k)
        app-link (fn [cmd] [:a {:href (str "/apps/" app "/" name "?cmd=" cmd)} cmd])]
    [:tr 
     [:td name]
     ;;[:td (app-link "start")]
     ;;[:td (app-link "stop")]
     [:td (app-link "status")]]))

(defn apps-admin [app]
  (let [factory (get-app-factory (keyword app))
        inst-ks (into [] (factory nil))
        insts (map (partial make-row app) inst-ks)]
    (html5
     [:head
      [:title app]
      (include-css "/css/style.css")
      (comment (include-js "/js/jsbot.js")) ]
     [:body
      [:h1 (str app " admin console.")]
      (comment
        [:div {:id "info"} "_________________"]
      [:div {:id "states" :style "position: relative;"}
       (image (str "/cbotimg?app=" app))
       [:div {:id "mark" :style "position: absolute; left: 0; top: 0"}
        (image "/images/atom.gif")]]
      [:select {:id "seleccion" :onchange "move(this);"}
       [:option {:value "start"} "start"]
       [:option {:value "ssh?"} "ssh?"]
       [:option {:value "switch-good"} "switch-good"]
       ])
      
      
      ;;[:img {:src (str "/cbotimg?app=" app) }]
      [:table {:border "1"}
       insts]])))

(defn row [result fld]
  [:tr [:td (name fld)] [:td (fld result)]])

(defn table4 [result & flds]
  (let [flds (if (and flds (= (first flds) ::all)) (keys result) flds)]
    (loop [table [:table {:border "1" :class "tabla"}] flds flds]
      (if (> (count flds) 0)
        (recur (conj table (row result (first flds))) (rest flds))
        table))))

(defn table-stats [stats]
  (let [data (map (fn [{state :state when :when delta :delta-micro}]
                    [:tr [:td state] [:td when] [:td delta]])
                  (reverse stats))]
    [:table {:border "1"} data]))

(declare session)

(defn current-pos [app-name inst-name]
  (log/debug (str "entrando a current-pos " app-name
                  " " inst-name
                  " " (:current @(get-cbot (keyword app-name) (keyword inst-name)))))
  (let [current (:current @(get-cbot (keyword app-name) (keyword inst-name)))
        result (ui/state-coord (keyword app-name) current)]
    result))

(defn html-simple [msg back]
  (html5 [:head
          [:title "STATUS"]
          (include-css "/css/style.css")]
         [:body
          [:h1 msg]
          [:br [:a {:href back} "regresar"]]]))

(defn send-cmd [app-name inst-name cmd]
  (let [result (apply-cmd (keyword app-name) (keyword inst-name) cmd)]
      (log/debug (str "RESULT:" result))
      (cond
        (= cmd "start") result ;(html-simple result (str "/apps/" app-name)) 
        (= cmd "stop") result ;(html-simple result (str "/apps/" app-name))
        (= cmd "status") (ring/redirect "/instance.html")
        
        (= cmd "current-pos") (json/json-str result))))

(comment (html5
                          [:h1 (str "Status of:" app-name " -> " inst-name)]
                          (table4 result :stop? :awaiting? :current :state-count :last-ended)
                          (table4 (:state-values result) ::all)
                          (table-stats (:info @(:stats result)))))

(comment [id template? current stop? awaiting?
                          states state-values state-count
                          last-ended exec-func])
