(ns mx.interware.util.basic
  (:import 
    (java.net URL URLConnection) 
    (java.io PrintWriter OutputStreamWriter OutputStream 
             InputStream BufferedReader InputStreamReader
             File FileWriter)
    (java.util.concurrent Future TimeUnit TimeoutException ExecutionException)
    (javax.mail Flags Flags$Flag Folder Message Session Store)
    (java.util Properties))
  (:require [clojure.java.io :as io])
  (:use clojure.contrib.logging clojure.test))

(defn try-times*
  "Executes thunk. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n delay thunk]
  (loop [n n]
    (debug (str "n:" n))
    (if-let [result (try
		      (thunk)
		      (catch Exception e
			(when (zero? n)
			  (throw e))
			(when (> delay 0)
			  (Thread/sleep delay))))]
      result
      (recur (dec n)))))

(defmacro try-times
  "Executes body. If an exception is thrown, will retry. At most n retries
  are done. If still some exception is thrown it is bubbled upwards in
  the call chain."
  [n & body]
  `(try-times* ~n 0 (fn [] ~@body)))

(defmacro try-times-with-delay
  [n delay & body]
  `(try-times* ~n ~delay (fn [] ~@body)))

(defn mail-it [mail]
  (let [props (java.util.Properties.)]
    (doto props
      (.put "mail.smtp.host" (:host mail))
      (.put "mail.smtp.port" (:port mail))
      (.put "mail.smtp.socketFactory.port"  (:port mail))
      (.put "mail.smtp.auth" "true"))

    (if (= (:ssl mail) true)
      (doto props
        (.put "mail.smtp.socketFactory.class" 
              "javax.net.ssl.SSLSocketFactory")))
    (doseq [p props] (info p))
    (info "--------------")
    (doseq [p mail] (info p))
    (let [authenticator (proxy [javax.mail.Authenticator] [] 
                          (getPasswordAuthentication 
                           []
                           (javax.mail.PasswordAuthentication. 
                            (:user mail) (:password mail))))
          session (javax.mail.Session/getInstance props authenticator)
          msg     (javax.mail.internet.MimeMessage. session)] 

      (.setFrom msg (javax.mail.internet.InternetAddress. (:user mail)))
      (doseq [to (:to mail)] 
        (.setRecipients msg 
                        (javax.mail.Message$RecipientType/TO)
                        (javax.mail.internet.InternetAddress/parse to)))
      (.setSubject msg (:subject mail))
      (.setText msg (:text mail))
      (javax.mail.Transport/send msg))))

(defn mail [& m]
  (mail-it (apply hash-map m)))

(defn receive-mail [{host :host port :port email :email protocol :protocol passwd :password :as info}]
  (let [props (Properties.)
	session (Session/getInstance props nil)]
    (with-open [store (.getStore session protocol)]
      (.setDebug session true)
      (.connect store host port email passwd)
      (let [folder (.getFolder store "INBOX")]
	(try
          (.open folder Folder/READ_WRITE)
          (let [count (.getMessageCount folder)]
	    (debug (str "count=" count))
	    (doseq [i (range 1 (inc count))]
	      (let [msg (.getMessage folder i)
		    subject (str (.getSubject msg))]
	        (debug (str "Subject: " subject))
                subject)))
	  (finally
	   (.close folder true)))))))

(defn rmail [& param]
  (receive-mail (apply hash-map param)))

(defn- peel-reply [subject]
  (if (.startsWith subject "Re: ")
    (subs subject 4)
    subject))

(defn get-mail-with-subject-in-set
  [host port protocol email password  subject-set]
  (let [props (Properties.)
	session (Session/getInstance props nil)]
    (with-open [store (.getStore session protocol)]
      (.setDebug session true)
      (.connect store host port email password)
      (let [folder (.getFolder store "INBOX")]
	(try
          (.open folder Folder/READ_WRITE)
          (if-let [d-mail (some #(subject-set (% 0))
                             (for [idx (range (.getMessageCount folder))]
                               (let [msg (.getMessage folder idx)
                                     subject (peel-reply (str (.getSubject msg)))]
                                 [subject msg])))]
            (do
              (.setFlag (d-mail 1) Flags$Flag/DELETED true)
	      (str (d-mail 0) ":" (.getContent (d-mail 1))))
            "NO-MAIL")
          (finally
           (.close folder true)))))))

(defn get-mail-with-subject-in-set-old
  [host port protocol email password  subject-set]
  (let [props (Properties.)
	session (Session/getInstance props nil)]
    (with-open [store (.getStore session protocol)]
      (.setDebug session true)
      (.connect store host port email password)
      (let [folder (.getFolder store "INBOX")]
	(try
          (.open folder Folder/READ_WRITE)
          (let [count (.getMessageCount folder)]
	    (debug (str "count=" count))
	    (if (> count 0)
	      (loop [i 1 max count]
		(let [msg (.getMessage folder i)
		      subject (peel-reply (str (.getSubject msg)))]
                  (debug (str "subject-set=" subject-set " subject:" subject))
                  ;; subject-set debe ser un set de posibles subjects!
                  (if (subject-set subject)
		    (do
		      (.setFlag msg Flags$Flag/DELETED true)
		      (str subject ":" (.getContent msg)))
		    (if (>= i max)
		      "NO-MAIL"
		      (recur (inc i) max)))))
	      "NO-MAIL"))
	  (finally
	   (.close folder true)))))))

 


