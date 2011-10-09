(ns mx.interware.cbot.store
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log] ))

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

(defn write-lines [ostream col]
  (doseq [elem col]
    (.println ostream elem)))

(defn app-store-save []
  (log/info (str "saving application configuration to " (.getAbsolutePath STORE-FILE)))
  (dosync
   (println (str (Thread/currentThread)))
   (Thread/sleep 10000)
   (alter app-store #(inc-version %))
   (with-open [ostream (java.io.PrintWriter. (java.io.FileWriter. STORE-FILE-NAME))]
     (write-lines ostream @app-store))))

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

(comment
  (defn- seq-lines [istream]
  (let [line (.readLine istream)]
    (if-not line
      nil
      (lazy-seq (cons line (seq-lines istream)))))))

(comment
  (defn read-lines [file]
  (let [in (java.io.BufferedReader. (java.io.FileReader. file))]
    (seq-lines in)))) 

(defn read-lines [istream]
  ((fn seq-lines [istream]
     (let [line (.readLine istream)]
       (if-not line
         nil
         (lazy-seq (cons line (seq-lines istream))))))
   istream))

(defn app-store-load []
  (dosync
   (with-open [istream (java.io.BufferedReader. (java.io.FileReader. STORE-FILE-NAME))]
     (let [store (into (sorted-map) (map load-string (read-lines istream)))]
       (log/debug (str "loading :" store))
       (ref-set app-store
                (app-store-rec. (:version store) (:updated store) (:configuration store)))))))

(defn get-apps []
  (configuration @app-store))

(if-not (.exists STORE-FILE)
  (app-store-save)
  (app-store-load))

(defn fib-seq []
  ((fn rfib [a b]
     (println "-------------------")
     (println [a b])
     (cons a (lazy-seq (rfib b (+ a b)))))
   0 1))

(def fib-seq2 (lazy-cat [0N 1] (map + (rest fib-seq2) fib-seq2)))
