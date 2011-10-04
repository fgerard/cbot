(ns mx.interware.cbot.store
  (:require [clojure.java.io :as io]
            [clojure.contrib.duck-streams :as duck]
            [clojure.contrib.logging :as log] ))

(println "loading mx.interware.cbot.store")
(org.apache.log4j.xml.DOMConfigurator/configureAndWatch "log4j.xml")
(def date-format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS"))

(def STORE-FILE-NAME "app-store2.clj")

(def STORE-FILE (java.io.File. STORE-FILE-NAME))

(defprotocol store
  (version [this])
  (updated [this])
  (raw-updated [this])
  (inc-version [this])
  (configuration [this])
  (rm [this id]))

(defrecord app-store-rec [version updated configuration]
  store
  (version [this] version)
  (inc-version [this] (app-store-rec. (inc version) (System/currentTimeMillis) configuration))
  (updated [this] (.format date-format updated))
  (raw-updated [this] updated)
  (configuration [this] configuration)
  (rm [this id] (app-store-rec. version updated (dissoc configuration id))))

(def app-store (ref (app-store-rec. 0 (System/currentTimeMillis) (sorted-map))))

(defn app-store-save []
  (log/info (str "saving application configuration to " (.getAbsolutePath STORE-FILE)))
  (dosync
   (alter app-store #(inc-version %))
   (duck/write-lines STORE-FILE-NAME @app-store)))

(defn get-app-names []
  (into [] (keys (configuration @app-store))))

(defn get-app [id]
  (assert (keyword? id))
  ((configuration @app-store) id))

(defn- alter-fun [id conf]
  (fn [store]
    (app-store-rec.
     (version store)
     (raw-updated store)
     (assoc (configuration store) id conf))))

(defn set-app [id conf]
  (assert (keyword? id))
  (dosync
   (alter app-store (alter-fun id conf))
   (app-store-save)))

(defn set-apps [apps-vec]
  (println apps-vec)
  (dosync
   (doseq [vv apps-vec]
     (alter app-store (alter-fun (vv 0) (vv 1))))
   (app-store-save)))

(defn rm-app [id]
  (assert (keyword? id))
  (dosync
   (alter app-store (fn [store] (rm store id)))
   (app-store-save)))


(defn app-store-load []
  (dosync
   (let [store (into (sorted-map) (map load-string (duck/read-lines STORE-FILE-NAME)))]
     (log/debug (str "loading :" store))
     (ref-set app-store (app-store-rec. (:version store) (:updated store) (:configuration store))))))

(defn get-apps []
  (configuration @app-store))

(if-not (.exists STORE-FILE)
  (app-store-save)
  (app-store-load))
