(ns mx.interware.cbot.web.views
  (:use [hiccup core page-helpers]
        mx.interware.cbot.core)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [mx.interware.cbot.ui :as ui]
            [ring.util.response :as ring]))

(defn index-page []
  (ring/redirect "/index.html"))

(defn app-instances [app]
  (let [factory (get-app-factory (keyword app))
        inst-ks (into [] (factory nil))]
    (json/json-str inst-ks)))

(defn send-cmd [app-name inst-name cmd params]
  (let [result (apply-cmd (keyword app-name) (keyword inst-name) cmd params)]
      (log/debug (str "RESULT:" result))
      (cond
        (= cmd "start") result 
        (= cmd "stop") result 
        (= cmd "current-pos") (json/json-str result)
        (= cmd "resume") result)))

