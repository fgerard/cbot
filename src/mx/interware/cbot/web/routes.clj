(ns mx.interware.cbot.web.routes
  (:use compojure.core
        mx.interware.cbot.web.views
        mx.interware.cbot.core
        [hiccup.middleware :only (wrap-base-url)])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]))

(defroutes main-routes
  (GET "/" [] (index-page)) 

  (GET "/apps/:app-name" [app-name]
       (apps-admin app-name))
  
  (GET "/apps/:app-name/:inst-name" [app-name inst-name cmd]
       (send-cmd app-name inst-name cmd))


  
  (GET "/prueba/:page" [page id name] (str "page=" page " id:" id " name:" name))
  (route/resources "/")
  (route/not-found "Page not found"))

(def app
  (-> (handler/site main-routes)
      (wrap-base-url)))
 
