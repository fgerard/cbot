(ns mx.interware.cbot.util
  (:import 
    (java.net URL URLConnection) 
    (java.io PrintWriter OutputStreamWriter OutputStream 
             InputStream BufferedReader InputStreamReader
             File FileWriter)
    (java.util.concurrent Future TimeUnit TimeoutException ExecutionException))
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defmacro warn-exception [fnk e & args]
  `(log/warn [~fnk (if-let [cause# (.getCause ~e)] (str (type cause#)) (str (type ~e))) (.getMessage ~e) ~@args]))

(defmacro warn-exception-and-throw [fnk e & args]
  `(do
     (warn-exception ~fnk ~e ~@args)
     (throw ~e)))

(defmacro debug-info [fnk & args]
  `(log/debug [~fnk ~@args]))

(defmacro log-info [level fnk & args]
  `(log/log ~level [~fnk ~@args]))

(defn str-trunc2len [p max]
  (let [s (str p)
        l (.length s)]
    (if (< l max) s (str (subs s 0 max) "..."))))

(defn contextualize [param context]
  (if (and (keyword? param) (param context)) 
    (param context)
    param))

(defn contextualize-int [param context]
  (let [val (str (contextualize param context))]
    (try
      (Integer/parseInt val)
      (catch NumberFormatException nfe
        0))))

(defn token2key [token]
  (if (.startsWith token ":")
    (keyword (subs token 1))
    token))

(defn contextualize-text [param context]
  (reduce #(str %1 " " %2)
          (map #(contextualize (token2key %) context)
               (seq (.split (str param) " ")))))


(defn wrap-with-timeout
  {:doc "Create a function with a maximum execution time, else throws TimoutExcetion"}
  [op & millis]
  (if-let [mls (first millis)]
    (fn [context]
      (try
        (let [env (get-thread-bindings)
	      env-op #(with-bindings* env op context)
	      fut (future-call env-op)]
	  (let [result (.get fut mls TimeUnit/MILLISECONDS)]
	    (if (isa? (type result) ExecutionException)
	      (throw (.getCause result))
	      result)))
	(catch TimeoutException e
	  (let [name (.toString op)
		arb (.lastIndexOf name "@")
		info-str (if (neg? arb) name (subs name 0 arb))
                exception (TimeoutException. (str "Timeout@" info-str "["  mls "]ms"))]
            (warn-exception :wrap-with-timeout exception name)
            (throw exception)))))
    op))

(defn try-times-opr
  [oprf n delay]
  (fn [context]
    (loop [m n]
       (debug-info :try-time-opr :retry (- n m) :max n)
       (let [result (try
		      (oprf context)
		      (catch Exception e
                        (warn-exception :try-times-opr e :retry (- n m) :max n)
			(when (zero? m) (throw e))
			(when (> delay 0) (Thread/sleep delay))
			::fail))]
	 (if-not (= result ::fail)
	   result
	   (recur (dec m)))))))

(defn wrap-with-catch-to-string
  {:doc "Evita que la funcion mande una excepcion convirtiendola a string"}
  [op]
  (fn [context]
    (try
      (op context)
      (catch Exception e
        (warn-exception :wrap-with-catch-to-string e (str op) context)
	(str (type e) ":" (.getMessage e))))))

(defn wrap-with-delay
  {:doc "Esta funcion crea una funcion que espara 'delay' ms antes de ejecutar la funcion 'f', f debe se una funcion que recibe un mapa y regresa un mapa ideal para ser enviada a un 'agent' cbot"}
  [oprf delay]
  (fn [context]
    (Thread/sleep delay)
    (oprf context)))


