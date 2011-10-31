(ns mx.interware.cbot.core
  (:gen-class)
  (:import 
    (java.net URL URLConnection) 
    (java.io PrintWriter OutputStreamWriter OutputStream 
             InputStream BufferedReader InputStreamReader
             File FileWriter)
    (java.util UUID)
    (java.util.concurrent Future TimeUnit TimeoutException ExecutionException LinkedBlockingQueue))
    
  (:require [clojure.java.io :as io]
            [mx.interware.cbot.store :as store]
            [mx.interware.cbot.operations :as opr]
            [mx.interware.cbot.util :as util]
            [mx.interware.util.basic :as basic]
            [mx.interware.cbot.ui :as ui]
            [clojure.tools.logging :as log]))

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
                    (let [a (str (class opr))]
                      (.matches a ".*human_opr.*"))))

(defn trunc-to [s len]
  (let [ss (str s)]
    (if (< len (.length ss))
      (.substring ss 0 len)
      ss)))

(defn- frx [n frac]
  (int (- n (* frac (Math/floor (double (/ n frac)))))))

(defn rds [n frac]
  (Math/floor (double (/ n frac))))

(def FRONTERA (* 1000 3600 24 365 10)) ;diez anos!!

(def formato-fecha (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS"))

(defn- to-zz [i len]
  (let [istr (str i)]
    (str (subs "0000000000000" 0 (- len (.length istr))) istr)))

(defn- format-delta [delta]
  (if (> FRONTERA delta)
    (let [milis (to-zz (frx delta 1000) 3) 
          resto (rds delta 1000)
          secs (to-zz (frx resto 60) 2) 
          resto (rds resto 60)
          mins (to-zz (frx resto 60) 2) 
          resto (rds resto 60)
          hras (to-zz (frx resto 24) 2) 
          dias (int (Math/floor (rds resto 24)))]
      (str dias " " hras ":" mins ":" secs "." milis))
    (.format formato-fecha delta)))

(defn store-stats [{max-len :max-len info :info :as stats} state val now delta]
  (let [result (conj info {:state (:id state) :result (str "<![CDATA[" (trunc-to val 80) "]]>") :when (format-delta now) :delta-micro (format-delta delta)})]
    (if (> (count result) max-len)
      (assoc stats :info (butlast result))
      (assoc stats :info result))))

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


(defn get-cbot-value [cbot d-uuid d-timeout]
  (let [{uuid :uuid semaphore :semaphore} @cbot]
    (if (= (str uuid) d-uuid)
      (do
        (log/debug "timeout:" d-timeout " semaphore queue length:" (.getQueueLength semaphore))
        (let [acquired? (.tryAcquire semaphore d-timeout TimeUnit/MILLISECONDS)]
          (log/debug "value acquired? " acquired?))))
    (let [result @cbot]
      result)))

(defn- delta-human [label]
  (if-let [prefix (re-find #".*-opr@" label)]
    (try
      (let [at (Long/parseLong (subs label (.length prefix)))]
        (- (System/currentTimeMillis) at))
      (catch NumberFormatException e
        (System/currentTimeMillis)))    
    (System/currentTimeMillis)))

(defrecord Cbot-template [id template? current stop? awaiting?
                          states state-values state-count
                          last-ended exec-func stats]
  CbotP
  (is-template? [cbot]
                template?)
  (create-with-id-and-context [cbot id instance-context]
    (if template?
      (agent (assoc cbot
                          :template? false
                          :id id
                          :state-values (into (:state-values cbot) (:param-map instance-context))
                          :stats (agent {:max-len stats
                                         :info (list)})
                          :uuid (UUID/randomUUID)
                          :semaphore (java.util.concurrent.Semaphore. 0 true))) 
      (util/warn-exception-and-throw
       :Cbot-template.create-with-id-and-context
       (RuntimeException. "Only templates should be cloned !")
       :id id)))
  (stop [cbot]
        (if stop?
          (throw (java.lang.RuntimeException. (str "Cbot:" id " is stopped!")))
          (assoc cbot :stop? true)))
  (start [cbot]
        (if-not stop?
          (throw (java.lang.RuntimeException. (str "Cbot:" id " is running!")))
          (assoc cbot :stop? false)))
  (exec [cbot]
        (util/debug-info :Cbot-template.exec :current current :stop? stop? :keyword? (keyword? current) (keys states))
        (assert (and current states))
        (let [state (states current)
              now (. System currentTimeMillis)]
          (util/debug-info :Cbot-template.exec2 :state state :current current)
          (if (and (not stop?) (not awaiting?))
            (let [t0 (System/currentTimeMillis)
                  opr-result (execute state state-values)
                  delta (- (System/currentTimeMillis) t0)  
                  new-state-vals (into state-values (result2map current opr-result))
                  next-state (get-next state
                                       (if (map? opr-result)
                                         (:result opr-result) opr-result))
                  uuid (UUID/randomUUID)]
              (util/debug-info :Cbot-template.exec :opr-result opr-result :delta-micro delta :next-state next-state)
              (send-off
               stats store-stats state (current new-state-vals) now delta)
              (if (or (is-long-running? state) (nil? next-state)) 
                (do
                  (if (nil? next-state)
                    (util/log-info
                     :info :Cbot-template.exec :id id :current current :msg
                     "State dosen't have a satisfying exit rule, send 'resume' with name of next state to continue!")
                    (util/log-info :info :Cbot-template.exec :id id :current current :msg "Awaiting restart for long-running operation"))
	          (assoc cbot
	            :state-values new-state-vals
	            :awaiting? true
    	            :last-ended now
                    :uuid uuid))
                (do
	          (assoc cbot
	            :state-values new-state-vals
	            :current next-state
	            :last-ended now
	            :state-count (inc state-count)
                    :uuid uuid))))
            cbot)))
  (resume [cbot response]
          (assert (and current states))
          (let [state (states current)
                now (. System currentTimeMillis)
                long-running? (is-long-running? state)]
            (if (and response awaiting? (not stop?))
              (let [started-at (current state-values)
                    new-state-vals (assoc state-values current response)
                    next-current (get-next state response)
                    uuid (UUID/randomUUID)]
                (send-off stats store-stats state (current new-state-vals) now (delta-human started-at))
                (if (nil? next-current)
                  (let [next-state (keyword response)]
                    (if (next-state states)
                      (assoc cbot
                        :state-values new-state-vals
                        :current next-state
			:awaiting? false
		        :last-ended now
	     	        :state-count (inc state-count)
                        :uuid uuid)
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
		    :state-count (inc state-count)
                    :uuid uuid)))
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

(defn- unlock-waiting-threads [d-cbot]
  (send-off d-cbot (fn [cbot]
                        (if-let [semaphore (:semaphore cbot)]
                          (let [q-len (.getQueueLength semaphore)]
                            (log/debug "releasing " q-len " waiting threads from queue of " (:id cbot) (:stop? cbot) (:awaiting? cbot))
                            (.release semaphore q-len)))
                        cbot)))

(defn- exec-cbot [cbot]
  (try
    (let [next-cbot (exec cbot)
          send? (and (not (:stop? next-cbot)) (not (:awaiting? next-cbot)))]
      (unlock-waiting-threads *agent*)
      (if send?
        (send *agent* (get-exec next-cbot)))
      next-cbot)
    (catch Exception e
      (.printStackTrace e)
      (util/warn-exception :exec-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn- resume-cbot [cbot result]
  (try
    (let [next-cbot (resume cbot result)
          send? (and (not (:stop? next-cbot)) (not (:awaiting? next-cbot)))]
      (unlock-waiting-threads *agent*)
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
      (log/debug "stop-cbot !!! " (:stop? next-cbot) (:stop? cbot))
      (unlock-waiting-threads *agent*)
      (util/log-info :info :stop-cbot :msg "cbot stoped !")
      next-cbot)
    (catch Exception e
      (util/warn-exception :stop-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn- start-cbot [cbot]
  (try
    (let [next-cbot (start cbot)]
      (unlock-waiting-threads *agent*)      
      (util/log-info :info :start-cbot :id (:id next-cbot)
                     :current (:current next-cbot) :msg "cbot started !!!!")
      (if (not (.awaiting? next-cbot))
        (send *agent* (get-exec next-cbot)))
      next-cbot)
    (catch Exception e
      (util/warn-exception :start-cbot e :id (:id cbot) :current (:current cbot))
      cbot)))

(defn re-flow [vec-rule]
  (fn [value]
    (let [ult (last vec-rule)]
      (if-let [seleccion (first
                          (first
                           (filter
                            (fn [par]
                              (log/debug (str "rule (<exit-state> <reg-exp) " par " value:" value))
                              (re-matches (re-pattern (second par)) value))
                            (partition 2 vec-rule))))]
        (do
          (log/debug (str "re-flow next state:" seleccion))
          seleccion)
        (do
          (log/debug (str "re-flow next state:" ult))
          ult)))))

(defn state-name-flow [kname]
  (fn [_]
    (log/debug (str "state-name-flow next state:" kname))
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

(defn create-cbot-template [id current states inter-state-delay context stats]
  (Cbot-template.
   id true current true false states context 0 0
   (if (> inter-state-delay 0)
     (fn [cbot]
       (if-not (or (:stop? cbot) (:waiting? cbot)) (Thread/sleep inter-state-delay))
       (exec-cbot cbot))
     #'exec-cbot)
   stats))

(comment (#'util/wrap-with-delay #'exec-cbot inter-state-delay))

(defn build-cbot-factory [id inter-state-delay parameters instances states starting-state stats]
  (let [template (create-cbot-template
                  id starting-state states inter-state-delay parameters stats)]
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
  (cond
   (nil? n) 0
   (number? n) n
   (and (string? n) (re-matches #"[0-9]+" n)) (Long/parseLong n)
   :otherwise 0))

(defn- wrap-opr [opr timeout retry-count retry-delay conf]
  (let [timeout timeout
        retry-count (fix-number retry-count)
        retry-delay (fix-number retry-delay)
        f1 (if (not= (str timeout) "0")
             (util/wrap-with-timeout (opr conf) timeout)
             (opr conf)) 
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

(defmethod opr-factory "date-time-opr" [opr-name timeout retry-count retry-delay conf]
  (opr/date-time-opr conf))


(defn flow-factory [v]
  (cond (= 0 (count v)) nil
        (= 1 (count v)) (state-name-flow (first v))
        :otherwise (re-flow v)))

(defn state-factory [state-id {{opr-name :opr
                       timeout :timeout
                       retry-count :retry-count
                       retry-delay :retry-delay
                       conf :conf :as conf-map} :conf-map
                       {connect-vec :connect :as flow} :flow}]
  (log/debug (str "state-id:"  state-id))
  ;(Thread/sleep 5000)
  (State. state-id (opr-factory opr-name timeout retry-count retry-delay conf) (flow-factory connect-vec)))

(def app-ctrl (ref {}))

;; Este ref tiene los cbot ya creados, si no existen, se crean y se
;; meten a este ref !!, la llave es :app:instance como str
(def cbot-ctrl (ref {}))

(defn apps []
  (store/get-app-names))

(defn- transform-str [s]
  (if (string? s)
    (cond
     (re-matches #":[A-Za-z0-9\?\-]+" s) (keyword (subs s 1))
     (re-matches #"[0-9]+" s) (Long/parseLong s)
     :otherwise s)
    s))

(defn- transform2run [info]
  (cond
   (map? info) (into {} (map (fn [[k v]] {k (transform2run v)}) info))
   (vector? info) (into [] (map (fn [v] (transform2run v)) info))
   :otherwise (transform-str info)))

(defn create-app-factory [app-key]
  (let [app (store/get-app app-key)
        {interstate-delay :interstate-delay
         stats :stats-cache-len
         parameters :parameters
         instances :instances
         pre-states :states} app]
    (let [states (into {}
                       (map (fn [info]
                              (let [info-k (keyword (subs (info :key) 1))
                                    run-info (transform2run info)]
                                [info-k (state-factory info-k run-info)]))
                            pre-states))]
      (build-cbot-factory app-key
                          (Integer/parseInt interstate-delay)
                          parameters
                          instances
                          states
                          (keyword (subs (:key (first pre-states)) 1)) 
                          (if stats (fix-number stats) 100)))))
(defn get-app-factory [app-key]
  (if-let [factory (app-key @app-ctrl)]
    factory
    (dosync
     (let [factory (create-app-factory app-key)]
       (alter app-ctrl #(assoc % app-key factory))
       factory))))

(defn- get-cbot-old [app-key inst-key]
  (dosync
   (let [k (str app-key inst-key)]
     (if-let [cbot (@cbot-ctrl k)]
       cbot
       (let [factory (get-app-factory app-key)
             cbot (factory inst-key)]
         (alter cbot-ctrl assoc k cbot)
         cbot)))))

(def get-cbot
  (memoize
   (fn [app-key inst-key]
     (let [factory (get-app-factory app-key)
           cbot (factory inst-key)]
       cbot))))

(defmulti apply-cmd (fn [_ _ cmd & params] cmd))

(defmethod apply-cmd "start" [app-k inst-k cmd & _]
  (let [cbot (get-cbot app-k inst-k)]
    (cbot-start cbot)))

(defmethod apply-cmd "stop" [app-k inst-k cmd & _]
  (let [cbot (get-cbot app-k inst-k)]
    (cbot-stop cbot)))

;;
(defmethod apply-cmd "status" [app-k inst-k cmd & param]
  (if (and param app-k inst-k)
    (if-let [cbot (get-cbot app-k inst-k)]
      (let [{uuid :uuid timeout :timeout} (first param)
            cbot-value (get-cbot-value (get-cbot app-k inst-k) uuid timeout)]
        cbot-value)
      {:cbot-msg (str "No cbot for " app-k " application and " inst-k)})
    {:cbot-msg "Application key or instance key missing!"}))

(defmethod apply-cmd "current-pos" [app-k inst-k cmd & param]
  (if (and param app-k inst-k)
    (if-let [cbot (get-cbot app-k inst-k)]
      (let [{uuid :uuid timeout :timeout} (first param)
            cbot-value (get-cbot-value (get-cbot app-k inst-k) uuid timeout)
            current (:current cbot-value)
            result (ui/state-coord app-k current)]
        (if result
          (assoc result
            :app (name app-k)
            :inst (name inst-k)
            :uuid (str (:uuid cbot-value))
            :request-uuid uuid
            :id (:id cbot-value)
            :current (:current cbot-value)
            :stop? (:stop? cbot-value)
            :awaiting? (:awaiting? cbot-value)
            :state-count (:state-count cbot-value)
            :last-ended (:last-ended cbot-value)
            :stats @(:stats cbot-value)
            :status (:instance-status_ (:state-values cbot-value))) 
          {:cbot-msg (str "No state " current " in application " app-k) }))
      {:cbot-msg (str "No cbot for " app-k " application and " inst-k)})
    {:cbot-msg "Application key or instance key missing!"}))

(defmethod apply-cmd "resume" [app-k inst-k cmd & param]
  (if (and param app-k inst-k)
    (if-let [cbot (get-cbot app-k inst-k)]
      (let [{msg :msg} (first param)
            result (cbot-resume cbot msg)]
        result)
      {:cbot-msg (str "No cbot for " app-k " application and " inst-k)})
    {:cbot-msg "Application key or instance key missing!"}))

(defn -mainx [& args]
  (println "Iniciando el CBOT-P")
  (org.apache.log4j.xml.DOMConfigurator/configureAndWatch "/Users/fgerard/clojure/cbot2/log4j.xml")
  (log/info "Ya está configurado el log4j")
  ;(start-cbot cbot) 
  )

(org.apache.log4j.xml.DOMConfigurator/configureAndWatch "/Users/fgerard/clojure/cbot2/log4j.xml")

