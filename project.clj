(defproject cbot "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "[0.2,)"]
                 [org.clojure/java.jdbc "[0,)"]
                 [org.clojure/data.json "0.1.1"]
                 [javax.mail/mail "[1.4.1,)"
                  :exclusions [javax.activation/activation]]
                 [log4j "[1.2.16,)"]
                 ;[ring/ring-core "[0.3.11,)"]
                 ;[ring/ring-devel "[0.3.7,)"]
                 ;[ring/ring-jetty-adapter "[0.3.7,)"]
                 ;[ring/ring-servlet "[0.3.7,)"]
                 [compojure "0.6.5"]
                 [clj-json "[0.4,)"]
                 [hiccup "0.3.6"]
                 [com.h2database/h2 "[1.3.153,)"]
                 [serializable-fn "[1.1.0,)"]
                 [seesaw "[1.1.0,)"]
                ]

  :dev-dependencies [[swank-clojure "[1.3.1,)"]
                     [lein-clojars "[0.6.0,)"]
                     [lein-ring "[0.4.5,)"]
                     ]
  :ring {:handler mx.interware.cbot.web.routes/app}
)
