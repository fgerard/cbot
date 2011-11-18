(ns mx.interware.cbot.operations
  (:import 
    (java.net URL URLConnection Socket) 
    (java.io PrintWriter OutputStreamWriter OutputStream 
             InputStream BufferedReader InputStreamReader
             File FileWriter)
    (java.util.concurrent Future TimeUnit TimeoutException ExecutionException))
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [mx.interware.cbot.util :as util]
            [clojure.java.jdbc :as sql]
            [mx.interware.util.basic :as basic]))

;; Las funciones siguientes "opr" son las ejecuciones de los estados del robot
;; estas reciben 2 mapas como parametros, el primero es lo especifico para la operacion
;; y está dentro de la configuración inicial asociado a la llave ":params", el segundo
;; es el mapa con llaves iguales a los estados y los valores asociados a la ultima
;; ejecucion de los mismos este ultimo es lo que definimos como contexto


;;demo conf {:delta 3000}
(defn sleep-opr
  {:doc "Crea una oparacion para hacer que la maquina de estados se detenga por un tiempo"
   :long-running false}
  [conf]
  (fn [context]
    (Thread/sleep (util/contextualize-int (:delta conf) context))
    (str "sleep-opr:" (System/currentTimeMillis))))

(defn date-time-opr
  {:doc "Crea una operacion que formatea la fecha/hora actual con un formato especificado"}
  [conf]
  (fn [context]
    (let [formatter (java.text.SimpleDateFormat. (util/contextualize (:format conf) context))]
      (.format formatter (java.util.Date.)))))

;;demo conf {:msg "Saludos"}
(defn print-msg-opr
  {:doc "Operacion para mandar un mensaje al sysout marcado con la hora en millis"
   :long-running false}
  [conf]
  (fn [context]
    (str "print-msg-opr:" (util/contextualize (:msg conf) context) "@" (System/currentTimeMillis))))

;;demo conf {:url "http://interware.com.mx?a=1&b=2"}
(defn get-http-opr
  {:doc "Operacion para hacer que la maquina de estados obtenga el contenido de una URL por http"
   :long-running false}
  [conf]
  (fn [context]
    (let [con (. (java.net.URL. (util/contextualize (:url conf) context)) openConnection)]
    (with-open [rdr (BufferedReader. (InputStreamReader. (. con getInputStream)))]
      (apply str (line-seq rdr))))))

(defn- create-http-params [mlStr context]
  (reduce #(str %1 "&" %2) (map (fn [lin]
                    (let [[k v] (.split lin "=")
                          k (.trim k)
                          v (util/contextualize-text (.trim v) context)]
                      (str k "=" v))) (.split mlStr "\n"))))

;;demo conf {:url "http://interware.com.mx" :post "a=1&b=2"} verificar!!!
(defn post-http-opr
  {:doc "Operacion para hacer que la maquina de estados obtenga el contenido de una URL por http"
   :long-running false}
  [conf]
  (fn [context]
    (let [con (. (java.net.URL. (util/contextualize (:url conf) context)) openConnection)]
      (do
	(. con setDoOutput true)
	(with-open [out (PrintWriter. (OutputStreamWriter. (. con getOutputStream)))]
	  (. out print (create-http-params (:params conf) context))
	  (. out flush))) 
    (with-open [rdr (BufferedReader. (InputStreamReader. (. con getInputStream)))]
      (apply str (line-seq rdr))))))


;; demo conf {:filter ".*"}
(defn print-context-opr
  {:doc "Operacion que imprime el mapa con los resultados de los estados de la maquina"
   :long-running false}
  [conf]
  (fn [context]
    (log/info (into
               (sorted-map)
               (map #(vector (first %) (util/str-trunc2len (second %) 100))
                    (filter #(re-matches
                      (re-pattern (util/contextualize (:filter-re conf) context))
                       (name (% 0))) context))))
    (str "print-context-opr @" (System/currentTimeMillis))))

;; demo conf {:level :info :text :nombre-otro-estado}
(defn log-opr
  {:doc "Operacion para mandar a log un mensaje con el nivel configurado en el param ':level'"
   :long-running false}
  ;; level es un keyword :debug,:warn,etc, state es un keyword de un
  ;; estado que se desea mandar al log
  [conf]
  (fn [context]
    (let [msg (util/contextualize-text (:text conf) context)]
    (log/log (:level conf) msg) ;; ojo level no se contextualiza!!
    (str "log-opr:[" msg "]@" (System/currentTimeMillis)))))

(defn- complete-mail-params [{text :text-vec subject :subject to :to-vec passwd :passwd :as param} context]
  (assoc param
    ;text es un vector de keywords a sacar del contexto!
    :text (apply str "" (map #(str (util/contextualize % context)) (into [] (.split text "[\n]+"))))
    :to (into [] (map #(str (util/contextualize % context)) (into [] (.split to "[\n\t ,]+"))))
    :password passwd
    :subject (util/contextualize-text subject context)))

;; demo conf {:host "mail.interware.mx" :port "463" :ssl "true"
;;            :user "fgerard" :passwd "xdesde"
;;            :to-vec ["agarcia@interware.com.mx"  // OJO AHORA SOLO
;;            ES UN STRING
;;                 "cibarra@interware.com.mx"]
;;            :subject "prueba"
;;            :text-vec ["Saludos:" "Estado dos:" :dos] //OJO ES UN STRING!!!
(defn send-mail-opr
  {:doc "Operación para mandar mail"
   :long-running false}
  [conf]
  (fn [context]
    (log/debug "Executing send-mail-opr \n" conf "\n" context)
    (basic/mail-it (complete-mail-params conf context))
    (str "send-mail-opr@" (System/currentTimeMillis))))

;; demo conf {}
(defn human-opr
  {:doc "Operacion que permite la intervencion de un humano que deberá rearrancar la máquina de estados"
   :long-running true}
  [ & _]
  (fn [context]
    (str "human-opr@" (System/currentTimeMillis))))

(def db {:classname "org.h2.Driver"
         :subprotocol "h2"
         :subname "/tmp/clojure.contrib.sql.test.db"
         :create true})
;jdbc:h2:~/test

;;demo conf {:db {:classname "org.h2.Driver" :subprotocol "h2"
;;                :subname "/tmp/test.db" :create true}
;;           :query "select count(*) from estado"}
(defn sql-read-opr
  [conf]
  (fn [context]
    (sql/with-connection (:db conf)
      (sql/with-query-results res
        [(util/contextualize (:query conf) context)]
          (reduce #(str %1 %2 "\n") "" res))))) 

;;demo conf {:host "10.1.1.23" :port 22}
(defn socket-opr
  [conf]
  (fn [context]
    (with-open [socket (Socket. (util/contextualize (:host conf) context)
                                (util/contextualize-int (:port conf) context))])
    (str "socket-opr@" (System/currentTimeMillis))))

(defn- contextualize-subject-set [subject-vec context]
  (into #{} (doseq [subject subject-vec] (util/contextualize-text subject context))))


(defmacro ctx-all
  ([fn p1 p2 k1]
     `[(~fn (~k1 ~p1) ~p2)])
  ([fn p1 p2 k1 & ks]
     `[(ctx-all ~fn ~p1 ~p2 ~k1)
       (ctx-all ~fn ~p1 ~p2 ~(first ks) ~@(rest ks))]))

;;demo conf {:host "pop.gmail.com" :port 995 :protocol "pop3s"
;;           :email "robot@interware.com.mx" :password "123456"
;;           :subject-set [:subject "TEXTO FIJO"]}
(defn get-mail-opr
  [conf]
  (fn [context]
;;    (debug (str "set:" sset))
    (let [[host port protocol email password]
          (flatten (ctx-all util/contextualize conf context
                            :host :port :protocol :email :password))]
      (basic/get-mail-with-subject-in-set
        host port protocol email password
        (contextualize-subject-set (:subject-set conf) context)))))

;;demo conf {:code (fn [context] (.....))}
(defn clojure-opr
  [conf]
  (let [code (:code conf)
        func (load-string (if (nil? code) "(fn [ctx] \"undefined clojure code!\")" code))]
    (fn [context]
      (log/debug "Entrando a clojure-opr, code: " (:code conf))
      (let [result (func context)]
        (if (map? result)
          (if (:result result)
              result
              (str "keyword :result is missing in this map " result " clojure-opr is WRONG!"))
          result)))))

(defn- to-js [context]
  (let [result
        (reduce
         (fn [s [k v]]
           (str s (if (> (.length s) 0) "," "") "\"" k "\": "  (if (map? v) v (str "\"" v "\"")))) ""
         (into {}
               (map
                (fn [[k v]]
                  (log/error "k" k "v" v)
                  [(name k) (cond
                              (keyword? v) (name v)
                              (.startsWith v "{") (json/json-str (load-string v)) 
                              :otherwise v)])
                context)))]
    (log/error "\n\n\n//////// " result)
    result))

;;demo conf {:code (function(context) {.....})}
(defn js-opr
  [conf]
  (let [ctx (org.mozilla.javascript.Context/enter)
        scope (.initStandardObjects ctx)
        code (:code conf)
        func (.compileFunction ctx scope (if (nil? code) "function(ctx) {return \"undefined javascript code!\";}" code) "src" 1 nil)]
    (org.mozilla.javascript.Context/exit)
    (fn [context]
      (try
        (let [ctx (org.mozilla.javascript.Context/enter)
              context-str (json/json-str context)
              jsonp (org.mozilla.javascript.json.JsonParser. ctx scope)
              jscontext (.parseValue jsonp context-str)
              jscontext-arr (to-array [jscontext])
              result (.call func ctx scope scope jscontext-arr)]
          (println (string? result) result)
          (cond
           (string? result) result
           (= (class result) org.mozilla.javascript.NativeObject)
           (let [cresult (into {} (map (fn [k] [(keyword k) (.get result k)]) (.keySet result)))]
             (if (:result cresult)
               cresult
               (str "keyword :result is missing in this map " cresult " js-opr is WRONG!")))
           :otherwise (str result)))
        (finally (org.mozilla.javascript.Context/exit)))))) 

(comment
  (use 'clojure.data.json)
  (def mm {:a 1 :b 2 :c 3})
  (def mms (json-str mm))
  (def ctx (org.mozilla.javascript.Context/enter))
  (def scope (.initStandardObjects ctx))
  (def jsonp (org.mozilla.javascript.json.JsonParser. ctx scope))
  (def param (.parseValue jsonp mms))
  (def params (to-array [param]))
  (def f (.compileFunction ctx scope "function f(map) {return map.a+map.b+map.c;}" "src" 1 nil))
  (println (.call f ctx scope scope params))
  (def ctx (org.mozilla.javascript.Context/exit))
  )

(comment
  (def script (.compileString ctx "1+2+3;" "" 1 nil))
  (.exec script ctx scope)
  (def f (.compileFunction ctx scope "function f(map) {return map;}" "src" 1 nil))
  (.call f ctx scope scope params)
  (def ctx (org.mozilla.javascript.Context/exit)))

(defn switch-good-opr [conf]
  (fn [context]
    (let [instance-status (:instance-status_ context)]
      (cond (= :good instance-status) "skip"
            :otherwise {:instance-status_ :good :result "send"}))))

;;demo conf {:minutes2wait 15}
(defn switch-bad-opr [conf]
  (fn [context]
    (let [instance-status (:instance-status_ context)]
      (cond (= :bad instance-status) (let [now (System/currentTimeMillis)
                                           last (:bad-timestamp_ context)
                                           delta (- now last)
                                           wait (* 60000 (util/contextualize-int (:minutes2wait conf) context))]
                                       (if (> delta wait)
                                         {:result "send"
                                          :bad-timestamp_ now}
                                         "skip"))
            :otherwise {:instance-status_ :bad
                        :result "send"
                        :bad-timestamp_ (System/currentTimeMillis)}))))
