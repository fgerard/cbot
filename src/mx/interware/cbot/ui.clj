(ns mx.interware.cbot.ui
  (:import
   (javax.swing DefaultListModel DefaultComboBoxModel JFrame JPanel JButton)
   (java.awt.event ActionEvent MouseEvent)
   (java.io PrintWriter BufferedReader)
   (javax.swing.event ListSelectionListener))
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [seesaw.core :as ss] 
            [seesaw.mig :as mig]
            [seesaw.event :as evt]
            [seesaw.bind :as bnd]
            [mx.interware.cbot.store :as store]))

(def IMG-PATH "resources/public/images/")

(defprotocol KeyedObject
  (get-key [this])
  (get-key-name [this])
  (get-rest [this]))

(defrecord KeyVal [key val]
  KeyedObject
  (toString [this] (if key (str (name key) " = " val) ""))
  (get-key [this] key)
  (get-key-name [this] (if key (name key) ""))
  (get-rest [this] (if (and (string? val) (.startsWith val ":"))
                     (keyword (.substring val 1))
                     val)))

(defn listbox-model-factory []
  (proxy [DefaultListModel] []
    (contains [obj]
              (let [ks (into #{} (map #(get-key (.elementAt this %)) (range 0 (.size this))))]
                (contains? ks (get-key obj))))
    (indexOf [obj]
             (if-let [elem (first (filter #(= (get-key obj) (% 0))
                                          (for [x (range (.size this))] [(.elementAt this x) x])))]
               (elem 1)
               -1))))

(defmulti panel class)
(defmulti success-fn class)

(defmethod panel KeyVal [key-val]
  (mig/mig-panel
   :items [["Name" "right"]
           [(ss/text :id :key :columns 20 :text (get-key-name key-val)) "wrap"]
           ["Value" "right"]
           [(ss/text :id :value :columns 20 :text (str (:val key-val)))]]))

(defmethod success-fn KeyVal [key-val]
  (fn [p]
    (let [k (.trim (ss/text (ss/select (ss/to-root p) [:#key])))]
      (if (and k (> (.length k) 0))
        (let [result (KeyVal. (keyword k)
                              (str (ss/text (ss/select (ss/to-root p) [:#value]))))]
          (log/debug (str (into {} (map (fn [k] [k (k result)]) (keys result)))))
          result)))))

(defn- clicks [e]
  (let [cls (class e)
        cc (cond (isa? cls ActionEvent) 1
                 (isa? cls MouseEvent) (.getClickCount e)
                 :otherwise 0)]
    cc))

(defn input [obj]
  (let [diag (ss/dialog
              :content (panel obj)
              :option-type :ok-cancel
              :success-fn (success-fn obj))]
    (ss/show! (ss/pack! diag))))

(defn add-model [list default-val]
  (fn [e]
    (if (and (= 1 (clicks e)) (isa? (class e) ActionEvent))
      (let [model (.getModel list)]
        (if-let [val (input default-val)]
          (if-not (.contains model val)
            (.add model (.getSize model) val)
            (ss/show! (ss/pack! (ss/dialog :content "Duplicated key!" :type :error)))))))))

(defn modify-model []
  (fn [e]
    (let [list (.getSource e)
          selected (.getSelectedIndex list)
          model (.getModel list)]
      (if (and (= 2 (clicks e)) (>= selected 0))
        (if-let [val (input (.get model selected))]
          (let [sel (.getSelectedIndex list)
                pos (.indexOf model val)]
            (if (or (= -1 pos) (= sel pos))
              (.set model (.getSelectedIndex list) val)
              (ss/show! (ss/pack! (ss/dialog :content "Duplicated key!" :type :error))))))))))

(defn delete-model [list]
  (fn [e]
    (let [selected (if list (.getSelectedIndex list) -1)]
      (if (and (= 1 (clicks e)) (>= selected 0))
        (.remove (.getModel list) selected)))))

(defrecord InstanceInfo [key param-map]
  KeyedObject
  (toString [this] (if key (str (name key)) ""))
  (get-key [this] key)
  (get-key-name [this] (if key (name key) ""))
  (get-rest [this] {:param-map (into {} (map
                                          (fn [kv] [(get-key kv) (get-rest kv)])
                                          (vals param-map)))}))

(defmethod panel InstanceInfo [instance]
  (let [name (ss/text
              :id :name
              :columns 20
              :text (name (get-key-name instance)))
        model (listbox-model-factory)
        ctx-list (ss/listbox
                  :id :list
                  :model model
                  :listen [:mouse-clicked (modify-model)])
        ctx (ss/scrollable ctx-list)
        add-ctx-btn (ss/button
                     :text "Add"
                     :listen [:action (add-model ctx-list (KeyVal. nil nil))])
        del-ctx-btn (ss/button
                     :text "Delete"
                     :listen [:action (delete-model ctx-list)])
        diag (mig/mig-panel
              :items [["Name:" "right"] [name "wrap"]
                      [ctx "span 2,grow, height 200,wrap"]
                      [(ss/flow-panel :items [add-ctx-btn del-ctx-btn]) "center,span 2"]])]
    (if-let [params (:param-map instance)] 
      (doseq [[k v] params] (.addElement model v)))
    diag))

(defn- model2map [model]
  (into {} (map #(let [kv (.elementAt model %)]
                   {(get-key kv) kv}) ;(get-rest kv)
                (range 0 (.getSize model)))))

(defn- model2vec [model]
  (into [] (map #(.elementAt model %) (range 0 (.getSize model)))))

(defmethod success-fn InstanceInfo [instance]
  (fn [instance-panel]
    (let [k (.trim (ss/text (ss/select (ss/to-root instance-panel) [:#name])))]
      (if (and k (> (.length k) 0))
        (let [model (.getModel (ss/select (ss/to-root instance-panel) [:#list]))
              result (InstanceInfo.
                      (keyword k)
                      (model2map model))]
          (log/debug (str (into {} (map (fn [k] [k (k result)]) (keys result)))))
          result)))))

(defrecord StateInfo [key conf-map flow]
  KeyedObject
  (toString [this] (if key (str (name key) " [" (:opr conf-map) "]") ""))
  (get-key [this] key)
  (get-key-name [this] (if key (name key) ""))
  (get-rest [this] {:conf-map conf-map
                    :flow flow}))

(def opr-panels
  {:log-opr
   (let [model (doto (DefaultComboBoxModel.)
                 (.addElement :trace)
                 (.addElement :debug)
                 (.addElement :info)
                 (.addElement :warn)
                 (.addElement :error)
                 (.addElement :fatal)
                 (.setSelectedItem :debug))]
     (mig/mig-panel :items [["Level:" "right"] [(ss/combobox :id :level :model model) "wrap"]
                          ["Text:" "right"] [(ss/text :id :text :columns 25 :text "") "wrap"]]))
   :print-msg-opr
   (mig/mig-panel :items [["Message:" "right"] [(ss/text :id :msg :columns 25 :text "") "wrap"]])
   :print-context-opr
   (mig/mig-panel :items [["Filter-re:" "right"] [(ss/text :id :re :columns 30 :text ".*") "wrap"]])
   :send-mail-opr
   (mig/mig-panel :items [["Timeout:" "right"]
                          [(ss/text :id :timeout :columns 6 :text "0") "wrap"]
                          ["Retry count:" "right"]
                          [(ss/text :id :retry-count :columns 3 :text "0") ""]
                          ["Retry delay:" "right"]
                          [(ss/text :id :retry-delay :columns 6 :text "0") "wrap"]
                          ["Mail-server:" "right"]
                          [(ss/text :id :host :columns 40 :text "smtp.gmail.com") "span 3,wrap"]
                          ["Port:" "right"]
                          [(ss/text :id :port :columns 4 :text "465") "left"]
                          ["Secure:" "right"]
                          [(ss/checkbox :id :ssl) "left,wrap"]
                          ["User:" "right"]
                          [(ss/text :id :user :columns 20 :text "") "span 2,wrap"]
                          ["Password:" "right"]
                          [(ss/password :id :passwd :columns 10)"wrap"]
                          ["To:" "right"]
                          [(ss/scrollable (ss/text :id :to-vec :multi-line? true :columns 45 :rows 5)) "span 3,wrap"]
                          ["Subject:" "right"]
                          [(ss/text :id :subject :columns 45 :text "") "span 3,wrap"]
                          ["Body:" "right"]
                          [(ss/scrollable (ss/text :id :text-vec :multi-line? true :columns 45 :rows 10)) "span 3,wrap"]])
   :get-http-opr
   (mig/mig-panel :items [["Timeout:" "right"]
                          [(ss/text :id :timeout :columns 6 :text "0") "wrap"]
                          ["Retry count:" "right"]
                          [(ss/text :id :retry-count :columns 3 :text "0") ""]
                          ["Retry delay:" "right"]
                          [(ss/text :id :retry-delay :columns 6 :text "0") "wrap"]
                          ["URL:" "right"] [(ss/text :id :url :multi-line? true :columns 25 :rows 4 :text "http://") "wrap"]])
   :post-http-opr
   (mig/mig-panel :items [["Timeout:" "right"]
                          [(ss/text :id :timeout :columns 6 :text "0") "wrap"]
                          ["Retry count:" "right"]
                          [(ss/text :id :retry-count :columns 3 :text "0") ""]
                          ["Retry delay:" "right"]
                          [(ss/text :id :retry-delay :columns 6 :text "0") "wrap"]
                          ["URL:" "right"] [(ss/text :id :url :multi-line? true :columns 25 :rows 4 :text "http://") "wrap"]
                          ["Params:" "right"] [(ss/text :id :params :multi-line? true :columns 25 :rows 8 :text "") "wrap"]])
   :sleep-opr
   (mig/mig-panel :items [["Delta:" "right"] [(ss/text :id :delta :columns 7 :text "") "wrap"]])
   :socket-opr
   (mig/mig-panel :items [["Timeout:" "right"]
                          [(ss/text :id :timeout :columns 6 :text "0") "wrap"]
                          ["Retry count:" "right"]
                          [(ss/text :id :retry-count :columns 3 :text "0") ""]
                          ["Retry delay:" "right"]
                          [(ss/text :id :retry-delay :columns 6 :text "0") "wrap"]
                          ["Host:" "right"] [(ss/text :id :host :columns 20 :text "") "wrap"]
                          ["Port:" "right"] [(ss/text :id :port :columns 6 :text "") "wrap"]])
   :human-opr
   (mig/mig-panel :items [["No info needed" "center"]])
   :switch-bad-opr
   (mig/mig-panel :items [["Minimum minutes to wait to resend:" "right"] [(ss/text :id :minutes2wait :columns 7 :text "") "wrap"]])
   :switch-good-opr
   (mig/mig-panel :items [["No info needed" "center"]])
   :no-opr
   (mig/mig-panel :items [["No info needed" "center"]])
   :date-time-opr
   (mig/mig-panel :items [["Format:" "right"] [(ss/text :id :format :columns 25 :text "yyyy/MM/dd HH:mm:ss") "wrap"]])
   :clojure-opr
   (mig/mig-panel :items [["Code:" "right"]
                          [(ss/text :id :code
                                    :multi-line? true
                                    :columns 50
                                    :rows 10
                                    :text "(fn [context] {:result <result> :otros <valores> ...)")
                           "wrap"]])})

(defn get-state-opr [state]
  (:opr (:conf-map state)))

(defmulti set-in-ui
  "Este multi-metodo dependiendo del la operacion, determina el panel apropiado, le coloca los valores almacenados en el parametro que recibe, regresa el panel ya actualizado"
  get-state-opr :default "no-opr")

(defmethod set-in-ui "no-opr" [state])

(defmethod set-in-ui "human-opr" [state])

(defmethod set-in-ui "switch-good-opr" [state])

(defmethod set-in-ui "log-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        level (ss/select (ss/to-root panel) [:#level])
        text (ss/select (ss/to-root panel) [:#text])]
    (.setSelectedItem (.getModel level) (:level (:conf (:conf-map state))))
    (.setText text (:text (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "print-msg-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        msg (ss/select (ss/to-root panel) [:#msg])]
    (.setText msg (:msg (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "clojure-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        code (ss/select (ss/to-root panel) [:#code])]
    (.setText code (:code (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "date-time-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        format (ss/select (ss/to-root panel) [:#format])]
    (.setText format (:format (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "print-context-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        reg (ss/select (ss/to-root panel) [:#re])]
    (.setText reg (:filter (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "get-http-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])
        url (ss/select (ss/to-root panel) [:#url])]
    (.setText timeout (:timeout (:conf-map state)))
    (.setText retry-count (:retry-count (:conf-map state)))
    (.setText retry-delay (:retry-delay (:conf-map state)))
    (.setText url (:url (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "post-http-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])
        url (ss/select (ss/to-root panel) [:#url])
        params (ss/select (ss/to-root panel) [:#params])]
    (.setText timeout (:timeout (:conf-map state)))
    (.setText retry-count (:retry-count (:conf-map state)))
    (.setText retry-delay (:retry-delay (:conf-map state)))
    (.setText url (:url (:conf (:conf-map state))))
    (.setText params (:params (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "send-mail-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])
        host (ss/select (ss/to-root panel) [:#host])
        port (ss/select (ss/to-root panel) [:#port])
        ssl (ss/select (ss/to-root panel) [:#ssl])
        user (ss/select (ss/to-root panel) [:#user])
        passwd (ss/select (ss/to-root panel) [:#passwd])
        to-vec (ss/select (ss/to-root panel) [:#to-vec])
        subject (ss/select (ss/to-root panel) [:#subject])
        text-vec (ss/select (ss/to-root panel) [:#text-vec])]
    (.setText timeout (:timeout (:conf-map state)))
    (.setText retry-count (:retry-count (:conf-map state)))
    (.setText retry-delay (:retry-delay (:conf-map state)))
    (.setText host (:host (:conf (:conf-map state))))
    (.setText port (:port (:conf (:conf-map state))))
    (.setSelected ssl (:ssl (:conf (:conf-map state)))) ;;;;; (= "true" )
    (.setText user (:user (:conf (:conf-map state))))
    (.setText passwd (:passwd (:conf (:conf-map state))))
    (.setText to-vec (reduce #(str %1 "\n" %2) (:to-vec (:conf (:conf-map state)))))
    (.setText subject (:subject (:conf (:conf-map state))))
    (.setText text-vec (reduce #(str %1 "\n" %2) (:text-vec (:conf (:conf-map state)))))
    panel))

(defmethod set-in-ui "sleep-opr" [state]
  (log/debug (str "state-opr:" (get-state-opr state) " ->" state))
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        delta (ss/select (ss/to-root panel) [:#delta])]
    (.setText delta (:delta (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "switch-bad-opr" [state]
  (log/debug (str "switch-bad-opr:" (get-state-opr state) " ->" state))
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        delta (ss/select (ss/to-root panel) [:#minutes2wait])]
    (.setText delta (:minutes2wait (:conf (:conf-map state))))
    panel))

(defmethod set-in-ui "socket-opr" [state]
  (let [panel ((keyword (get-state-opr state)) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])
        host (ss/select (ss/to-root panel) [:#host])
        port (ss/select (ss/to-root panel) [:#port])]
    (.setText timeout (:timeout (:conf-map state)))
    (.setText retry-count (:retry-count (:conf-map state)))
    (.setText retry-delay (:retry-delay (:conf-map state)))
    (.setText host (:host (:conf (:conf-map state))))
    (.setText port (:port (:conf (:conf-map state))))
    panel))

(defn- opr-panel-gen [model sp]
  (fn [e]
    (let [cur-opr (.getSelectedItem model)
          opr-key (keyword cur-opr)
          vp (.getViewport sp)
          opr-panel (opr-key opr-panels)]
      (if-not (nil? opr-panel)
        (do
          (.removeAll vp)
          (.add vp opr-panel)
          (ss/pack! (.getParent sp)))))))

(defmethod panel StateInfo [state]
  (log/debug state)
  (let [curr-opr (:opr (:conf-map state))
        model (doto (DefaultComboBoxModel.)
                (.addElement "clojure-opr")
                (.addElement "date-time-opr")
                (.addElement "get-http-opr")
                (.addElement "get-mail-opr")
                (.addElement "human-opr")
                (.addElement "log-opr")
                (.addElement "no-opr")
                (.addElement "post-http-opr")
                (.addElement "print-context-opr")
                (.addElement "print-msg-opr")
                (.addElement "send-mail-opr")
                (.addElement "sleep-opr")
                (.addElement "socket-opr")
                (.addElement "sql-read-opr")
                (.addElement "switch-bad-opr")
                (.addElement "switch-good-opr")
                (.setSelectedItem curr-opr))
        sp (ss/scrollable  (ss/label :text "NO-OP"))
        opr-combo (ss/combobox :id :operation :model model :listen [:action (opr-panel-gen model sp)])
        panel (mig/mig-panel
               :items [["State name:" "right"]
                       [(ss/text :id :name :columns 20 :text (get-key-name state)) "span 4,wrap"]

                       ["Operation:" "right"]
                       [opr-combo "span 2, wrap"]
                       [sp "span 4"]])]
                                        ;(opr-panel-action nil);;falta set-in-uirlo !
    (if-not (nil? curr-opr)
      (do
        (.add (.getViewport sp) ((keyword (get-state-opr state)) opr-panels))
        (ss/invoke-later (do (Thread/sleep 100) (set-in-ui state))))
      ) ;(set-in-ui state)     
    panel))

(defmulti get-from-ui
  "Este multi-metodo arma un mapa que representa una operacion sacando el contenido del panel de captura"
  str :default "no-opr")

(defmethod get-from-ui "log-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)
        model (.getModel (ss/select (ss/to-root diag) [:#level]))]
    {:opr operation-name :conf {:level (.getSelectedItem model)
                                :text (ss/text (ss/select (ss/to-root diag) [:#text]))}}))

(defmethod get-from-ui "print-msg-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:msg (ss/text (ss/select (ss/to-root diag) [:#msg]))}}))

(defmethod get-from-ui "clojure-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:code (ss/text (ss/select (ss/to-root diag) [:#code]))}}))

(defmethod get-from-ui "date-time-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:format (ss/text (ss/select (ss/to-root diag) [:#format]))}}))

(defmethod get-from-ui "print-context-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:filter (ss/text (ss/select (ss/to-root diag) [:#re]))}}))

(defmethod get-from-ui "get-http-opr" [operation-name]
  (let [panel ((keyword operation-name) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])]
    {:opr operation-name
     :timeout (.getText timeout)
     :retry-count (.getText retry-count)
     :retry-delay (.getText retry-delay)
     :conf {:url (ss/text (ss/select (ss/to-root panel) [:#url]))}}))

(defmethod get-from-ui "post-http-opr" [operation-name]
  (let [panel ((keyword operation-name) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])]
    {:opr operation-name
     :timeout (.getText timeout)
     :retry-count (.getText retry-count)
     :retry-delay (.getText retry-delay)
     :conf {:url (ss/text (ss/select (ss/to-root panel) [:#url]))
                                :params (ss/text (ss/select (ss/to-root panel) [:#params]))}}))

(defmethod get-from-ui "send-mail-opr" [operation-name]
  (let [panel ((keyword operation-name) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])
        host (ss/select (ss/to-root panel) [:#host])
        port (ss/select (ss/to-root panel) [:#port])
        ssl (ss/select (ss/to-root panel) [:#ssl])
        user (ss/select (ss/to-root panel) [:#user])
        passwd (ss/select (ss/to-root panel) [:#passwd])
        to-vec (ss/select (ss/to-root panel) [:#to-vec])
        subject (ss/select (ss/to-root panel) [:#subject])
        text-vec (ss/select (ss/to-root panel) [:#text-vec])
        cosa (into [] (.split (.getText text-vec) "[\n\t ,]"))]
    {:opr operation-name
     :timeout (.getText timeout)
     :retry-count (.getText retry-count)
     :retry-delay (.getText retry-delay)
     :conf {:host (.getText host)
                                :port (.getText port)
                                :ssl (.isSelected ssl)
                                :user (.getText user)
                                :passwd (.getText passwd)
                                :to-vec (into [] (.split (.getText to-vec) "[\n\t ,]+"))
                                :subject (.getText subject)
                                :text-vec (into [] (.split (.getText text-vec) "[\n]+"))}}))

(defmethod get-from-ui "sleep-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:delta (ss/text (ss/select (ss/to-root diag) [:#delta]))}}))

(defmethod get-from-ui "switch-bad-opr" [operation-name]
  (let [diag ((keyword operation-name) opr-panels)]
    {:opr operation-name :conf {:minutes2wait (ss/text (ss/select (ss/to-root diag) [:#minutes2wait]))}}))

(defmethod get-from-ui "socket-opr" [operation-name]
  (let [panel ((keyword operation-name) opr-panels)
        timeout (ss/select (ss/to-root panel) [:#timeout])
        retry-count (ss/select (ss/to-root panel) [:#retry-count])
        retry-delay (ss/select (ss/to-root panel) [:#retry-delay])]
    {:opr operation-name
     :timeout (.getText timeout)
     :retry-count (.getText retry-count)
     :retry-delay (.getText retry-delay)     
     :conf {:host (ss/text (ss/select (ss/to-root panel) [:#host]))
                                :port (ss/text (ss/select (ss/to-root panel) [:#port]))}}))

(defmethod get-from-ui "no-opr" [operation-name]
  {:opr operation-name :conf {}})

(defmethod success-fn StateInfo [state]
  (fn [p]
    (let [k (.trim (ss/text (ss/select (ss/to-root p) [:#name])))]
      (if (and k (> (.length k) 0))
        (let [state-key (keyword k)
              combo (ss/select (ss/to-root p) [:#operation])
              model (.getModel combo)
              operation (.getSelectedItem model)]
          (if-not (nil? operation)
            (let [opr-key (keyword operation)
                  opr-panel (opr-key opr-panels)
                  result (StateInfo. state-key 
                                     (get-from-ui operation)
                                     (:flow state))]
              (log/debug (str (into {} (map (fn [k] [k (k result)]) (keys result)))))
              result)))))))

(defn fill-model [app model key-id]
  (doseq [kobj (vals (key-id app))]
    (.addElement model kobj))
  model)

(defn fill-model-vec [app model key-id]
  (doseq [obj (key-id app)] (.addElement model obj))
  model)

(defrecord ApplicationInfo [key interstate-delay states parameters instances stats-cache-len]
  KeyedObject
  (toString [this] (if key (str (name key)) ""))
  (get-key [this] key)
  (get-key-name [this] (if key (name key) ""))
  (get-rest [this] {:interstate-delay interstate-delay
                    :parameters (into {} (map
                                          (fn [kv] [(get-key kv) (get-rest kv)])
                                          (vals parameters)))
                    :instances (into {} (map
                                         (fn [inst] [(get-key inst) (get-rest inst)])
                                         (vals instances)))
                    :states states
                    :stats-cache-len stats-cache-len})) ;;fg

(def finish (atom {:x 0 :y 0}))
(def current-btn (atom nil))

(defn- in-range [val lim1 lim2]
  (and (>= val (min lim1 lim2)) (<= val (max lim1 lim2))))

(defn- get-coord [comp]
  (let [x (.getX comp)
        y (.getY comp)
        w (.getWidth comp)
        h (.getHeight comp)
        cx (+ x (/ w 2))
        cy (+ y (/ h 2))]
    [x cx (+ x w) y cy (+ y h)]))

(defn- to-do [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)
        razon (/ (- ax2 ax1) (- ay2 ay1))
        traslape-x (or (in-range ax1 bx1 bx2) (in-range ax2 bx1 bx2)
                       (in-range bx1 ax1 ax2) (in-range bx2 ax1 ax2))
        traslape-y (or (in-range ay1 by1 by2) (in-range ay2 by1 by2)
                       (in-range by1 ay1 ay2) (in-range by2 ay1 ay2))
        result (cond (and traslape-x traslape-y) :centro-centro
                     traslape-x (if (< acy bcy)
                                  :centro-abajo-centro-arriba
                                  :centro-arriba-centro-abajo)
                     traslape-y (if (< acx bcx)
                                  :centro-derecho-centro-izquierdo
                                  :centro-izquierdo-centro-derecho)
                     :otherwaise (if (> (- (max acx bcx) (min acx bcx)) (* 2 (- (max acy bcy) (min acy bcy))))
                                   (if (> acx bcx)
                                     :centro-izquierdo-centro-derecho
                                     :centro-derecho-centro-izquierdo)
                                   (if (> acy bcy)
                                     :centro-arriba-centro-abajo
                                     :centro-abajo-centro-arriba)))]
    result))

(defn arrow [x0 y0 x1 y1]
  (let [S 6
        dx (- x1 x0)
        dy (- y1 y0)
        hipo (Math/sqrt (+ (* dx dx) (* dy dy)))
        cos (/ dy hipo)
        sin (/ dx hipo)
        p1 [x1 y1]
        p2 [(- (+ x1 (* S cos)) (* S sin)) (- y1 (* S cos) (* S sin))]
        p3 [(- (p2 0) (* S cos) (* S sin)) (+ (- (p2 1) (* S cos)) (* S sin))]
        p4 [(- x1 (* S cos) (* S sin)) (+ (- y1 (* S cos)) (* S sin))]
        pointsR [p1 p2 p3 p4]
        points (into [] (map (fn [[x y]] [(Math/round (double x)) (Math/round (double y))]) pointsR))
        poly (java.awt.Polygon.)]
    (doseq [[x y] points]
      (.addPoint poly x y))
    (let [result [x0 y0 x1 y1 poly]]
      (log/debug (str "result:" result))
      result)))

(defmulti connect-arrow to-do :default :nada)
(defmethod connect-arrow :nada [cmp1 cmp2]
  (log/error "NADA !!"))
(defmethod connect-arrow :centro-centro [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)]
    (arrow acx acy bcx bcy)))
(defmethod connect-arrow :centro-arriba-centro-abajo [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)]
    (arrow acx ay1 bcx by2)))
(defmethod connect-arrow :centro-abajo-centro-arriba [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)]
    (arrow acx ay2 bcx by1)))
(defmethod connect-arrow :centro-izquierdo-centro-derecho [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)]
    (arrow ax1 acy bx2 bcy)))
(defmethod connect-arrow :centro-derecho-centro-izquierdo [cmp1 cmp2]
  (let [[ax1 acx ax2 ay1 acy ay2] (get-coord cmp1)
        [bx1 bcx bx2 by1 bcy by2] (get-coord cmp2)]
    (arrow  ax2 acy bx1 bcy)))

(defn- connect-points [g cmp]
  (.setColor g java.awt.Color/gray)
  (let [[x1 cx x2 y1 cy y2] (get-coord cmp)
        curpos @finish
        drawing? (:drawing? curpos)]
    (if drawing? 
      (.drawLine g cx cy (:x curpos) (:y curpos)))))

;;el vector connect tiene un numero non de elementos
;; en las pocisiones nones tiene los keyword de los otros
;; estados a los que connecta, en las pares tiene los reg-exp
;; que debe cumplir el valor resultado del estado para ir al
;;estado inmediato ANTERIOR, el ultimo no tiene reg-exp porque
;; es el otherwise !!

(defn- create-connect-vector [old-v keyw]
  ;;si es el primero no pregunta re-exp si no entonces
  ;; pregunta con un dialogo el reg-exp y lo inserta por la izq
  ;; si ya existe elimina el anterior y reinserta el nuevo
  (log/debug (str "create-connect-vector: " old-v " --- " keyw))
  (if (= 0 (count old-v))
    [keyw]
    (if (some #(= keyw %) old-v)
      (let [dialog (ss/dialog
                    :type :question
                    :option-type :ok-cancel
                    :content "Eliminate link?")
            delete? (ss/show! (ss/pack! dialog))]
        (if (= delete? :success )
          (loop [result [] v old-v]
            (let [n (count v)]
              (cond
               (= n 0) (into [] result)
               (= n 1) (if (= (first v) keyw)
                         (recur (butlast result)  (rest v))
                         (recur (conj result (first v)) (rest v)))
               :otherwise (if (= (first v) keyw)
                            (recur result (rest (rest v)))
                            (recur (conj result (first v) (second v)) (rest (rest v)))))))
          old-v))
      (let [re-texto (ss/text :id :re :columns 20 :text ".*")
            diag (ss/dialog
                  :type :question
                  :content re-texto
                  :option-type :ok-cancel
                  :success-fn (fn [p] (.getText re-texto)))]
        (if-let [reg (ss/show! (ss/pack! diag))]
          (loop [result [keyw reg] inp old-v]
            (if (= 1 (count inp))
              (let [last (first inp)]
                (if (= last keyw)
                  (into [] (butlast result))
                  (into [] (conj result (first inp)))))
              (let [nk (first inp)
                    re (second inp)
                    resto (rest (rest inp))]
                (if (= nk keyw)
                  (recur result resto)
                  (recur (conj result nk re) resto))))))))))

(defn- get-btn-with-id [btns kid]
  (first (filter #(= (keyword (.getText %)) kid) btns)))

(defn- connect-btns [g b1 b2 idx]
  (let [[x1 y1 x2 y2 poly] (connect-arrow b1 b2)]
    (doto g
      (.drawLine  x1 y1 x2 y2)
      (.fillPolygon  poly))
    (if (.hasFocus b1)
      (.drawString g (str idx) (int (/ (+ x1 x2) 2)) (int (/ (+ y1 y2) 2))))))

(def icons (atom {}))

(defn icon-of [opr]
  (if-let [icon (@icons opr)]
    icon
    (let [icon (javax.swing.ImageIcon. (str IMG-PATH "/" opr ".gif"))]
      (swap! icons assoc opr icon)
      icon)))

(def icon-of2
  (memoize (fn [opr]
             (javax.swing.ImageIcon. (str IMG-PATH "/" opr ".gif")))))

(defn state-button [model idx]
  (let [state (.elementAt model idx)
        lbl (name (get-key state))
        btn (proxy [JButton] [lbl]
              (paint [g]
                     (proxy-super paint g))
              (setLocation [x y]
                           (let [x (max 0 x)
                                 y (max 0 y)]
                             (proxy-super setLocation x y)
                             (if-let [panel (.getParent this)]
                               (let [pw (.getWidth panel)
                                     ph (.getHeight panel)
                                     nx (max pw (+ x (.getWidth this)))
                                     ny (max ph (+ y (.getHeight this)))]
                                 (if (or (> nx pw) (> ny ph))
                                   (do
                                     (doto panel
                                       (.setSize nx ny)
                                       (.setPreferredSize (java.awt.Dimension. nx ny))
                                       (.setMinimumSize (java.awt.Dimension. nx ny))
                                       (.invalidate panel))
                                     (doto (.getParent panel)
                                       (.invalidate)
                                       (.repaint))))))
                             (let [state (.getElementAt model idx)]
                               (.setElementAt
                                model
                                (assoc state :flow {:x x :y y :connect (:connect (:flow state))})
                                idx)))))
        dim (.getPreferredSize btn)]
    (doto btn
      (.setSize dim)
      (.setIcon (icon-of2 (:opr (:conf-map state))))
      (.setLocation (-> state :flow :x) (-> state :flow :y))
      (ss/listen
       :mouse-pressed (fn [e]
                        (reset! current-btn (.getSource e))
                        (swap! finish assoc :drawing? (= (.getButton e) 3)))
       :mouse-released (fn [e]
                         (reset! current-btn nil)
                         (let [orig (.getComponent e)
                               state (.getElementAt model idx)
                               panel (.getParent orig)
                               other (.getComponentAt panel (+ (.getX orig) (.getX e))
                                                            (+ (.getY orig) (.getY e)))]
                           (if (and (not (nil? other)) (isa? (class other) javax.swing.JButton))
                             (do
                               (if (= 3 (.getButton e))
                                 (.setElementAt
                                  model
                                  (assoc state :flow {:x (-> state :flow :x) :y (-> state :flow :y)
                                                      :connect (create-connect-vector
                                                                (-> state :flow :connect)
                                                                (keyword (.getText other)))})
                                  idx))))
                           (.repaint panel))) 
       :mouse-dragged (fn [e]
                        (let [c (.getComponent e)
                              x (.getX c)
                              y (.getY c)
                              w (- 0 (/ (.getWidth c) 2)) 
                              h (- 0 (/ (.getHeight c) 2)) 
                              dx (.getX e)
                              dy (.getY e)
                              btn-modif (.getButton e)
                              grph (.getGraphics c)]
                          (cond (= btn-modif 1) (.setLocation c (+ x dx w) (+ y dy h))
                                (= btn-modif 3) (do
                                                  (swap! finish assoc :x (+ x dx) :y (+ y dy))
                                                  (.repaint (.getParent c))))))))))


(defn fix-connect-vector [v valid-ks]
  (loop [result [] v v]
    (let [n (count v)]
      (cond
       (= n 0) (into [] result)
       (= n 1) (if (valid-ks (first v))
                 (recur (conj result (first v)) (rest v))
                 (recur (butlast result)  (rest v)))
       :otherwise (if (valid-ks (first v))
                    (recur (conj result (first v) (second v)) (rest (rest v)))
                    (recur result (rest (rest v))))))))

(defn fix-states-model [model]
  (let [ks (into #{} (map #(get-key %) (for [i (range 0 (.getSize model))] (.elementAt model i))))]
    (doseq [i (range 0 (.getSize model))]
      (let [state (.elementAt model i)]
        (.setElementAt
         model
         (assoc state :flow {:x (-> state :flow :x) :y (-> state :flow :y)
                             :connect (fix-connect-vector (-> state :flow :connect) ks)})
         i)))))

(defn add-all-btns [model panel]
  (doseq [idx (range 0 (.getSize model))]
      (let [btn (state-button model idx)]
        (.add panel btn))))

(defn listen-to [model panel]
  (let [listener (proxy [javax.swing.event.ListDataListener] []
                   (intervalAdded [e]
                                  (let [btn (state-button model (.getIndex0 e))]
                                    (.add panel btn)))
                   (intervalRemoved [e]
                                    (fix-states-model model)
                                    (.removeAll panel)
                                    (add-all-btns model panel)
                                    (.repaint panel))
                   (contentsChanged [e]))]
    (.addListDataListener model listener)))

(defn max-x-y-comp [[max-x max-y] comp]
  (let [point (.getLocation comp)
        dim (.getSize comp)
        x (+ (.getX point) (.getWidth dim))
        y (+ (.getY point) (.getHeight dim))
        result [(max max-x x) (max max-y y)]]
    result))

(defn minimum-size [panel]
  (let [comps (.getComponents panel)
        min-size (reduce max-x-y-comp [0 0] comps)
        dim (java.awt.Dimension. (min-size 0) (min-size 1))]
    (doto panel
      (.setMinimumSize dim)
      (.setMaximumSize dim)
      (.setPreferredSize dim))))


(defn state-panel [model]
  (let [px (proxy [JPanel] []
             (paint [g]
                    (proxy-super paint g)
                      (if (or (nil? @current-btn) (:drawing? @finish))
                        (doseq [idx (range 0 (.getSize model))]
                          (let [state (.getElementAt model idx)
                                connect (-> state :flow :connect)
                                x0 (-> state :flow :x)
                                y0 (-> state :flow :y)
                                btns (into []
                                      (filter #(isa? (class %) JButton)
                                              (.getComponents this)))
                                xthis (get-btn-with-id btns (get-key state))]
                            (log/debug (str (keys state) " >> " (vals state)))
                            (if (> (count connect) 0)
                              (loop [conct connect idx 1]
                                (if (= 1 (count conct))
                                  (do
                                    (.setColor g java.awt.Color/red)
                                    (connect-btns g xthis (get-btn-with-id btns (first conct)) idx))
                                  (let [id-other (first conct)
                                        reg-exp (second conct)]
                                    (.setColor g java.awt.Color/blue)
                                    (connect-btns g xthis (get-btn-with-id btns id-other) (str idx ") \"" reg-exp "\""))
                                    (recur (rest (rest conct)) (inc idx)))))))))
                      (doseq [cm (filter #(= @current-btn %) (.getComponents this))]
                        (connect-points g cm))))]
    (listen-to model px)
    (doto px
      (.setLayout nil)
      (.setBackground java.awt.Color/white)
      (.setPreferredSize (java.awt.Dimension. 2000 2000))
      (.setMinimumSize (java.awt.Dimension. 2000 2000))
      (.setSize 2000 2000))
    (add-all-btns model px)
    (minimum-size px)))

(defn up-model [list]
  (fn [e]    
    (let [model (.getModel list)
          idx (.getSelectedIndex list)]
      (if (> idx 0)
        (let [obj (.getElementAt model idx)]
          (.insertElementAt model obj (dec idx))
          (.removeElementAt model (inc idx))
          (.setSelectedIndex list (dec idx)))))))

(defn dn-model [list]
  (fn [e]
    (let [model (.getModel list)
        idx (.getSelectedIndex list)]
    (if (< idx (dec (.size model)))
      (let [obj (.getElementAt model idx)]
        (.insertElementAt model obj (inc (inc idx)))
        (.removeElementAt model idx)
        (.setSelectedIndex list (inc idx)))))))

(defmethod panel ApplicationInfo [app]
  (let [ctx-list (ss/listbox
                  :id :param-list
                  :model (fill-model app (listbox-model-factory) :parameters)
                  :listen [:mouse-clicked (modify-model)])
        context (ss/scrollable ctx-list)
        add-ctx-btn (ss/button
                     :text "Add"
                     :listen [:action  (add-model ctx-list (KeyVal. nil nil))])
        del-ctx-btn (ss/button
                     :text "Delete"
                     :listen [:action (delete-model ctx-list)])

        inst-list (ss/listbox
                   :id :instance-list
                   :model (fill-model app (listbox-model-factory) :instances) 
                   :listen [:mouse-clicked (modify-model)]) ;;;;;
        instances (ss/scrollable inst-list)
        add-inst-btn (ss/button
                      :text "Add"
                      :listen [:action (add-model inst-list (InstanceInfo. nil nil))])
        del-inst-btn (ss/button
                      :text "Delete"
                      :listen [:action (delete-model inst-list)])
        
        st-list (doto (ss/listbox
                 :id :state-list
                 :model (fill-model-vec app (listbox-model-factory) :states)  
                 :listen [:mouse-clicked (modify-model)])
                  (.setSelectionMode javax.swing.ListSelectionModel/SINGLE_SELECTION)) 
        states (ss/scrollable st-list)
        up-st-btn (doto
                      (ss/button :text ""
                                 :listen [:action (up-model st-list)])
                    (.setIcon (javax.swing.ImageIcon. (str IMG-PATH "/arrow-1.gif")))
                    (.setPreferredSize (java.awt.Dimension. 20 20))) 
        dn-st-btn (doto
                      (ss/button :text ""
                                 :listen [:action (dn-model st-list)])
                    (.setIcon (javax.swing.ImageIcon. (str IMG-PATH "/arrow-3.gif")))
                    (.setPreferredSize (java.awt.Dimension. 20 20))
                    ) 
        add-st-btn (ss/button
                    :text "Add"
                    :listen [:action (add-model st-list (StateInfo. nil {} {:x 0 :y 20 :connect []}))])
        del-st-btn (ss/button
                    :text "Delete"
                    :listen [:action (delete-model st-list)])
        st-btn-panel (doto (JPanel.) (.add add-st-btn) (.add up-st-btn) (.add dn-st-btn) (.add del-st-btn))
        state-info (ss/scrollable (state-panel (.getModel st-list)) :hscroll :always :vscroll :always) 

        panel (mig/mig-panel
               :constraints ["" "" ""]
               :items [["Name:" "right"]
                       [(ss/text
                         :id :name
                         :text (get-key-name app)
                         :columns 15) "wrap, span 2 1"]
                       ["Interstate delay:" "right"]
                       [(ss/text
                         :id :interstate-delay
                         :text (:interstate-delay app) :columns 6) ""]
                       ["ms" "left, wrap"]
                       ["Statistics cache:" "right"]
                       [(ss/text
                         :id :stats-cache-len
                         :text (:stats-cache-len app) :columns 6) ""]
                       ["states" "left, wrap"]
                       ["Context" "left,span 2"] ["Instance" "left,wrap"]
                       [context "span 2,width 250"]
                       [instances "span 2,width 250,wrap"]
                       [add-ctx-btn "right"] [del-ctx-btn "left"]
                       [add-inst-btn "right"] [del-inst-btn "left,wrap"]                       
                       
                       ;;["States" "left,span 2,wrap"]
                       [states "height 400, width 250,span 2"]
                       [state-info "span 2,grow,wrap"]
                       ;[state-info "width 400,height 150,span 2,wrap"]
                                        ;[add-st-btn "right"] [del-st-btn "left,wrap"]
                       [st-btn-panel "span 2,wrap"]
                       ]
               )]
    panel))

(defmethod success-fn ApplicationInfo [app]
  (fn [app-panel]
    (let [k (.trim (ss/text (ss/select (ss/to-root app-panel) [:#name])))]
      (if (and k (> (.length k) 0))
        (let [param-model (.getModel (ss/select (ss/to-root app-panel) [:#param-list]))
              instance-model (.getModel (ss/select (ss/to-root app-panel) [:#instance-list]))
              state-model (.getModel (ss/select (ss/to-root app-panel) [:#state-list]))
              interstate-delay (ss/text (ss/select (ss/to-root app-panel) [:#interstate-delay]))
              stats-cache-len (ss/text (ss/select (ss/to-root app-panel) [:#stats-cache-len]))
              result (ApplicationInfo.
                      (keyword  k)
                      interstate-delay
                      ;;fg (model2map state-model)
                      (model2vec state-model)
                      (model2map param-model)
                      (model2map instance-model)
                      stats-cache-len)]
          (log/debug (str (into {} (map (fn [k] [k (k result)]) (keys result)))))
          result)))))

(defn- get-parts [kobj]
  (if (= {} kobj)
    {}
    (loop [kseq (remove #(= % :key) (keys kobj)) result {}]
      (log/debug "result :" result)
      (let [k (first kseq)
            r (rest kseq)]
        (if-not k
          result
          (let [v (k kobj)
                sub-map (if (coll? v)
                          (get-parts v)
                          v)]
            (recur r (conj result [k sub-map]))))))))

(defn- str2key [s]
  (if (and (string? s) (.startsWith s ":")) 
    (keyword (.substring s 1))
    s))

(defn- convert2keys [m]
  (log/debug (str "convert2keys 1) " m))
  (let [mm (into {} (map (fn [k]
                  (let [v (k m)]
                    (log/trace (str (string? v) "," (vector? v) "," (map? v) ":" k "->" v))
                    [k (cond
                        (string? v) (str2key v)
                        (vector? v) (into [] (map #(if (map? %) (convert2keys %) (str2key %)) v))
                        (map? v) (convert2keys v)
                        :otherwise v)]))
                         (keys m)))]
    (log/debug (str "convert2keys 2) " mm))
    mm))

(defn app-model2vec [model]
  (into [] (map
            (fn [idx]
              (let [app (.elementAt model idx)]
                [(get-key app) (convert2keys (get-rest app))]))
            (range (.size model)))))

(defn save [app-list]
  (fn [e]
    (let [model (.getModel app-list)
          apps (app-model2vec model)]
      (log/debug (str "++++++SAVE  " apps))
      (store/set-apps apps))))

(defn- map-into-keyval [m]
  (into {} (map (fn [[k v]] [k (KeyVal. k (str v))]) m)))

(defn- map-key2str [m]
  (into {} (map (fn [k]
                  (let [v (k m)]
                    (log/debug (str (string? v) "," (vector? v) "," (map? v) ":" k "->" v))
                    [k (cond
                        (vector? v) (into [] (map str v))
                        (map? v) (map-key2str v)
                        (keyword? v) (str v)
                        :otherwise v)]))
                (keys m))))

(defn create-app-from-map [k-id v-map]
  (ApplicationInfo. k-id
                    (str (:interstate-delay v-map))
                    (into []
                          (map (fn [m]
                                 (StateInfo. (:key m) (map-key2str (:conf-map m)) (:flow m)))
                               (:states v-map)))
                    (map-into-keyval (:parameters v-map))
                    (into {} (map (fn [[k v]]
                                    [k (InstanceInfo. k (map-into-keyval (:param-map v)))])
                                  (:instances v-map)))
                    (str (:stats-cache-len v-map))))

(defn- map2model-APP [mapa model]
  (doseq [[k v] mapa]
    (let [app (create-app-from-map k v)]
      (.addElement model app)))
  model)

(defn create-cbot-panel []
  (let [model (map2model-APP (store/get-apps) (listbox-model-factory)) 
        
        app-list (ss/listbox
                  :model model ;(listbox-model-factory)
                  :listen [:mouse-clicked (modify-model)])
        app (ss/scrollable app-list)
        add-app-btn (ss/button
                     :text "Add"
                     :listen [:action  (add-model app-list (ApplicationInfo. nil nil nil nil nil nil))])
        del-app-btn (ss/button
                     :text "Delete"
                     :listen [:action (delete-model app-list)])
        save-app-btn (ss/button
                      :text "Save"
                      :listen [:action (save app-list)])

        panel (mig/mig-panel
               :constraints ["" "" ""]
               :items [[app "span 2,grow,height 400,wrap"]
                       [add-app-btn "right"] [del-app-btn "left,wrap"]
                       [save-app-btn "center,span 2"]])]
    panel))

(defn start-ui []
  (org.apache.log4j.xml.DOMConfigurator/configureAndWatch "/Users/fgerard/clojure/cbot2/log4j.xml")
  (ss/invoke-later
     (let [panel (create-cbot-panel)]
       (-> (ss/frame :title "CBot 1.0",
                     :content panel,
                     :on-close :exit)
           ss/pack!
           ss/show!))))



(defn create-jpg [k-app]
  (let [app-map (store/get-app k-app)
        app (create-app-from-map k-app app-map)
        model (fill-model-vec app (listbox-model-factory) :states)
        panel (minimum-size (state-panel model))
        dim (.getPreferredSize panel)
        w (+ 20 (.getWidth dim)) 
        h (+ 20 (.getHeight dim)) 
        buffimg (java.awt.image.BufferedImage. w h java.awt.image.BufferedImage/TYPE_INT_RGB)
        graphics (doto (.getGraphics buffimg)
                   (.setColor java.awt.Color/WHITE)
                   (.fillRect 0 0 w h)
                   )
        baos (java.io.ByteArrayOutputStream.)
        encoder (com.sun.image.codec.jpeg.JPEGCodec/createJPEGEncoder baos)]
    (.paint panel graphics)
    
    (.encode encoder buffimg)
    (.toByteArray baos)))

(def states-coords
  (memoize
   (fn [k-app]
    (let [app (create-app-from-map k-app (store/get-app k-app))]
      (into {}
            (map
             (fn [state]
               ;;(println state)
               ;;(println (get-key state))
               ;;(println (:x (:flow state)))
               {(get-key state) {:x (:x (:flow state)) :y (:y (:flow state))}})
             (:states app)))))))

(defn state-coord [k-app k-state]
  ((states-coords k-app) k-state))
