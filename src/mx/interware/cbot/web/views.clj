(ns mx.interware.cbot.web.views
  (:use [hiccup core page-helpers]
        mx.interware.cbot.core
        )
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [mx.interware.cbot.ui :as ui]
            [mx.interware.cbot.store :as store]
            [ring.util.response :as ring]))

(defn index-page []
  (ring/redirect "/index2.html"))

(defn app-instances [app]
  (log/debug "app-instances -> " app)
  (let [factory (get-app-factory (keyword app))
        inst-ks (into [] (factory nil))]
    (json/json-str inst-ks)))

(defn app-conf [app]
  (json/json-str (store/get-app (keyword app))))

(defn app-save-conf [app conf]
  (println app)
  (println conf)
  (let [appk (keyword app)
        states-map (:states conf)
        states (into [] (for [i (range (count states-map))] ((keyword (str i)) states-map)))
        conf (assoc conf :states states)]
    (store/set-app (keyword app) conf)
    (stop-and-remove-old-app appk)
    {"result" "ok"}))

(defn get-operations []
  (json/json-str ui/operations))

(defn- key2str [info]
  (cond
   (map? info) (into {} (map (fn [[k v]] {k (key2str v)}) info))
   (vector? info) (into [] (map (fn [v] (key2str v)) info))
   :otherwise (if (keyword? info) (str info) info)))

(defn send-cmd [app-name inst-name cmd params]
  (let [result (apply-cmd (keyword app-name) (keyword inst-name) cmd params)]
      (log/debug (str "RESULT:" result))
      (cond
        (= cmd "start") (if (:json params) (json/json-str result) result)
        (= cmd "stop") (if (:json params) (json/json-str result) result)
        (= cmd "current-pos") (if (:json params)
                                (json/json-str (key2str result))
                                (str (assoc-in result [:stats :info]  (into [] (-> result :stats :info))))) 
        (= cmd "resume") (if (:json params) (json/json-str result) result))))


(defn report-log []
  (comment
    (html5 [:table
            (map (fn [l] [:tr [:td l]]) @debug-agent)])))


