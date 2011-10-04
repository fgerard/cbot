(ns mx.interware.cbot.core
  (:gen-class)
  (:import 
    (java.net URL URLConnection) 
    (java.io PrintWriter OutputStreamWriter OutputStream 
             InputStream BufferedReader InputStreamReader
             File FileWriter)
    (java.util.concurrent Future TimeUnit TimeoutException ExecutionException))
  (:require [clojure.java.io :as io]
            [mx.interware.cbot.store :as store]
            [mx.interware.cbot.operations :as opr]
            [mx.interware.cbot.util :as util]
            [mx.interware.util.basic :as basic]
            [clojure.contrib.logging :as log]))

(declare exec-cbot)

(defprotocol StateP
  (execute [state context])
  (get-next [state result])
  (is-long-running? [state]))

(defrecord State [id opr next-func]
  StateP
  (execute [state context]
           (util/debug-info :State.execute)
           (opr (assoc context :state-name_ id)))
  (get-next [state result]
            (if (nil? next-func)
              nil
              (next-func result)))
  (is-long-running? [state]
                    (= opr opr/human-opr)))

(defprotocol CbotP
  "Protocol for a CBOT"
  (exec [cbot])
  (resume [cbot response])
  (start [cbot])
  (stop [cbot])
  (get-exec [cbot])
  (create-with-id-and-context [cbot id instance-context])
  (is-template? [cbot]))

(defn- result2map [current result]
  (if (map? result)
    (into {} (assoc (dissoc result :result) current (:result result)))
    {current result}))

(defrecord Cbot-template [id template? current stop? awaiting?
                          states state-values state-count
                          last-ended exec-func]
  CbotP
  (is-template? [cbot]
                template?)
  (create-with-id-and-context [cbot id instance-context]
    (if template?
      (agent (assoc cbot
               :template? false
               :id id
               :state-values (into (:state-values cbot) (:param-map instance-context))))
      (util/warn-exception-and-throw
       :Cbot-template.create-with-id-and-context
       (RuntimeException. "Only templates should be cloned !")
       :id id)))
  (stop [cbot]
        (if stop?
          cbot
          (assoc cbot :stop? true)))
  (start [cbot]
        (if-not stop?
          cbot
          (assoc cbot :stop? false)))
  (exec [cbot]
        (util/debug-info :Cbot-template.exec :current current)
        (assert (and current states))
        (let [state (states current)
              now (. System currentTimeMillis)]
          (if (and (not stop?) (not awaiting?))
            (let [opr-result (execute state state-values)
                  new-state-vals (into state-values (result2map current opr-result))
                  next-state (get-next state
                                       (if (map? opr-result)
                                         (:result opr-result) opr-result))]
              (util/debug-info :Cbot-template.exec :opr-result opr-result)
              (if (or (is-long-running? state) (nil? next-state)) 
                (do
                  (if (nil? next-state)
                    (util/log-info
                     :warn :Cbot-template.exec :id id :current current :msg
                     "State dosen't have a satisfying exit rule, send 'resume' with name of next state to continue!")
                    (util/log-info :info :Cbot-template.exec :id id :current current :msg "Awaiting restart for long-running operation"))
	          (assoc cbot
	            :state-values new-state-vals
	            :awaiting? true
    	            :last-ended now))
                (do
	          (assoc cbot
	            :state-values new-state-vals
	            :current next-state
	            :last-ended now
	            :state-count (inc state-count)))))
            cbot)))
  (resume [cbot response]
          (assert (and current states))
          (let [state (states current)
                now (. System currentTimeMillis)
                long-running? (is-long-running? state)]
            (if (and response awaiting? long-running? (not stop?))
              (let [new-state-vals (assoc state-values current response)
                    next-current (get-next state response)]
                (if (nil? next-current)
                  (let [next-state (keyword response)]
                    (if (next-state states)
                      (assoc cbot
                        :state-values new-state-vals
                        :current next-state
			:awaiting? false
		        :last-ended now
	     	        :state-count (inc state-count))
                      (do
                        (util/log-info :error :Cbot-template.resume
                                       :id id :current current
                                       :msg (str "State " next-state " dosen't exists!!"))
                        cbot)))
                  (assoc cbot
                    :state-values new-state-vals
		    :current next-current
                    :awaiting? false
	            :last-ended now
		    :state-count (inc state-count))))
              (throw
               (RuntimeException.
                (str "illegal state "
                     (when (nil? response) "[response missing] ")
		     (when-not awaiting? "[cbot is not waiting] ")
		     (when-not long-running? "[current operation is not long-running]")))))))
  (get-exec [cbot]
            (if exec-func
              exec-func
              exec-cbot)))

(defn- exec-cbot [cbot]
  (try
    (let [next-cbot (exec cbot)
          send? (and (not (:stop? next-cbot)) (not (:awaiting? next-cbot)))]
      (if send?
        (send *agent* (get-exec next-cbot)))
      next-cbot)
    (catch Exception e
      (util/warn-exception :exec-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn- resume-cbot [cbot result]
  (try
    (let [next-cbot (resume cbot result)
          send? (and (not (:stop? next-cbot)) (not (:awaiting? next-cbot)))]
      (if send?
        (send *agent* (get-exec next-cbot)))
      next-cbot)
    (catch Exception e
      (util/warn-exception :resume-cbot e :id (:id cbot)
                           :current (:current cbot) :result result)
      cbot)))

(defn- stop-cbot [cbot]
  (try
    (let [next-cbot (stop cbot)]
      (util/log-info :info :stop-cbot :msg "cbot stoped !")
      next-cbot)
    (catch Exception e
      (util/warn-exception :stop-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn- start-cbot [cbot]
  (try
    (let [next-cbot (start cbot)]
      (util/log-info :info :start-cbot :id (:id next-cbot)
                     :current (:current next-cbot) :msg "cbot started !!!!")
      (if (not (.awaiting? next-cbot))
        (send *agent* (get-exec next-cbot)))
      next-cbot)
    (catch Exception e
      (.printStackTrace e)
      (util/warn-exception :start-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn re-flow [vec-rule]
  (fn [value]
    (let [ult (last vec-rule)]
      (if-let [seleccion (first
                          (first
                           (filter
                            (fn [par]
                              (println "par>" par " value>" value)
                              (re-matches (re-pattern (second par)) value))
                            (partition 2 vec-rule))))]
        seleccion
        ult))))

(defn state-name-flow [kname]
  (fn [_]
    kname))

(defn cbot-start [cbot-agent]
  (send cbot-agent start-cbot)
  (str "start command sent to " (.id @cbot-agent)))

(defn cbot-stop [cbot-agent]
  (send cbot-agent stop-cbot)
  (str "stop command sent to " (.id @cbot-agent)))

(defn cbot-resume [cbot-agent external-response]
  (send cbot-agent resume-cbot external-response)
  (str "resume command sent to " (.id @cbot-agent) " with external response:" external-response))

(defn create-cbot-template [id current states inter-state-delay context]
  (println (str "create-cbot-template inter-state-delay:" inter-state-delay))
  (Cbot-template.
   id true current true false states context 0 0
   (if (> inter-state-delay 0)
     (#'util/wrap-with-delay #'exec-cbot inter-state-delay)
     #'exec-cbot)))

(defn build-cbot-factory [id inter-state-delay parameters instances states]
  (let [template (create-cbot-template id (first (keys states)) states inter-state-delay parameters)]
    (fn [instance-id]
      (if (nil? instance-id)
        (into [] (keys instances))
        (let [instance-context (instance-id instances)]
          (if (nil? instance-context)
            (util/log-info :warn :build-cbot-factory :msg (str "Instance:" instance-id " non existent in " id " cbot factory")))
          (create-with-id-and-context template instance-id instance-context))))))

(defn opr-dispatch [opr-name timeout retry-count retry-delay conf]
  opr-name)

(defmulti opr-factory opr-dispatch :default "no-opr")

(defmethod opr-factory "sleep-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/sleep-opr conf))

(defmethod opr-factory "switch-bad-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/switch-bad-opr conf))

(defmethod opr-factory "print-msg-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/print-msg-opr conf))

(defn- fix-number [n]
  (if (nil? n)
    0
    (try
      (let [nn (Integer/parseInt n)]
        nn)
      (catch NumberFormatException nfe
        0))))

(defn- wrap-opr [opr timeout retry-count retry-delay conf]
  (let [timeout (fix-number timeout)
        retry-count (fix-number retry-count)
        retry-delay (fix-number retry-delay)
        f1 (if (> timeout 0) (util/wrap-with-timeout (opr conf) timeout) (opr conf)) 
        f2 (if (> retry-count 0)
             (util/try-times-opr f1 retry-count retry-delay)
             f1)]
    (util/wrap-with-catch-to-string f2)))

(defmethod opr-factory "get-http-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/get-http-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "post-http-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/post-http-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "print-context-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/print-context-opr conf))

(defmethod opr-factory "log-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/log-opr conf))

(defmethod opr-factory "send-mail-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/send-mail-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "human-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/human-opr conf))

(defmethod opr-factory "switch-good-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/switch-good-opr conf))

(defmethod opr-factory "socket-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/socket-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "get-mail-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/get-mail-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "sql-read-opr" [opr-name timeout retry-count retry-delay conf]
  (wrap-opr opr/sql-read-opr timeout retry-count retry-delay conf))

(defmethod opr-factory "clojure-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/clojure-opr conf))



(defn flow-factory [v]
  (cond (= 0 (count v)) nil
        (= 1 (count v)) (state-name-flow (first v))
        :otherwise (re-flow v)))

(defn state-factory [state-name {{opr-name :opr
                       timeout :timeout
                       retry-count :retry-count
                       retry-delay :retry-delay
                       conf :conf :as conf-map} :conf-map
                      {connect-vec :connect :as flow} :flow}]
  (State. state-name (opr-factory opr-name timeout retry-count retry-delay conf) (flow-factory connect-vec)))

(defn do-it []
  (let [app-name (first (store/get-app-names))
        app (store/get-app app-name)
        id app-name
        {interstate-delay :interstate-delay
         parameters :parameters
         instances :instances
         pre-states :states} app]
    (let [states (into {} (map (fn [par] [(par 0) (state-factory (par 0) (par 1))]) pre-states))]
      (build-cbot-factory id (Integer/parseInt interstate-delay) parameters instances states))))


(defn -mainx [& args]
  (println "Iniciando el CBOT-P")
  (org.apache.log4j.xml.DOMConfigurator/configureAndWatch "/Users/fgerard/clojure/cbot2/log4j.xml")
  (log/info "Ya está configurado el log4j")
  ;(start-cbot cbot) 
  )

(org.apache.log4j.xml.DOMConfigurator/configureAndWatch "/Users/fgerard/clojure/cbot2/log4j.xml")

