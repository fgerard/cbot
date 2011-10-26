(ns mx.interware.cbot.web.routes
  (:use compojure.core
        mx.interware.cbot.web.views
        mx.interware.cbot.core
        
        [hiccup.middleware :only (wrap-base-url)])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [clojure.data.json :as json]
            [ring.middleware.session :as session]
            [mx.interware.cbot.ui :as ui]))

(defn to-long [n]
  (try
    (Long/parseLong (str n))
    (catch NumberFormatException e 0)))

(defroutes main-routes

  (GET "/" []
       (index-page))
  
  (GET "/apps" [] (json/json-str (apps)))

  (GET "/apps/:app-name" [app-name]
       (app-instances app-name))

  (GET "/conf/:app-name" [app-name]
       (app-conf app-name))
  
  (GET "/apps/:app-name/:inst-name" [app-name inst-name cmd uuid timeout msg]
       (send-cmd app-name inst-name cmd {:uuid uuid :timeout (to-long timeout) :msg msg}))
  
  (GET "/cbotimg/:app" [app]
       {:status 200
        :headers {"Content-type" "image/jpeg"}
        :body (java.io.ByteArrayInputStream. (ui/create-jpg (keyword app))) })

  (GET "/operations" []
         (get-operations))
  
  (GET "/log" []
       (report-log))
  
  (route/resources "/")

  (route/not-found "Page not found"))

(def app
  (-> (session/wrap-session main-routes)
      (handler/site)
      (wrap-base-url)))
 
