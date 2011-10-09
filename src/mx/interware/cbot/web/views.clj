(ns mx.interware.cbot.web.views
  (:use [hiccup core page-helpers]
        mx.interware.cbot.core))

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
    [:a {:href (str dir d-name (break-param param-map)) :class "mediano"} d-name]))

(defn index-page []
  (let [a (map (partial link "/apps/") (apps))]
    (html5
    [:head
      [:title "CBot 1.0 console"]
      (include-css "/css/style.css")]
    [:body
     [:h1 "CBot 1.0 console (1)"]
     a
     ])))

(defn make-row [app inst-k]
  (let [name (name inst-k)
        app-link (fn [cmd] [:a {:href (str "/apps/" app "/" name "?cmd=" cmd)} cmd])]
    [:tr
     [:td name] [:td (app-link "start")] [:td (app-link "stop")] [:td (app-link "status")]]))

(defn apps-admin [app]
  (let [factory (get-app-factory (keyword app))
        inst-ks (into [] (factory nil))
        insts (map (partial make-row app) inst-ks)]
    (html5
     [:head
      [:title app]
      (include-css "/css/style.css")]
     [:body
      [:h1 (str app " admin console.")]
      [:table
       insts]])))

(defn row [result fld]
  [:tr [:td (name fld)] [:td (fld result)]])

(defn table4 [result & flds]
  (let [flds (if (and flds (= (first flds) ::all)) (keys result) flds)]
    (loop [table [:table {:border "1"}] flds flds]
      (if (> (count flds) 0)
        (recur (conj table (row result (first flds))) (rest flds))
        table))))

(defn send-cmd [app-name inst-name cmd]
  (let [result (apply-cmd (keyword app-name) (keyword inst-name) cmd)]
    (cond
     (string? result) (html5 [:h1 result])
     :otherwise (html5
                 [:h1 (str "Status of:" app-name " -> " inst-name)]
                 (table4 result :stop? :awaiting? :current :state-count :last-ended)
                 ;;(table4 (:states result) ::all)
                 (table4 (:state-values result) ::all)))))

(comment [id template? current stop? awaiting?
                          states state-values state-count
                          last-ended exec-func])
