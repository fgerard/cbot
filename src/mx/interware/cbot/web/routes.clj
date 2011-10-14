(ns mx.interware.cbot.web.routes
  (:use compojure.core
        mx.interware.cbot.web.views
        mx.interware.cbot.core
        mx.interware.cbot.ui
        [hiccup.middleware :only (wrap-base-url)])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [ring.middleware.session :as session]))

(defroutes main-routes

  (GET "/" []
       (index-page))
  
  (GET "/apps" [] (apps-page))

  (GET "/test" {prams :params :as request}
              (str "<<<<>>>>> " (class request) " " request))

  (GET "/apps/:app-name" [app-name]
       (apps-admin app-name))
  
  (GET "/apps/:app-name/:inst-name" [app-name inst-name cmd]
       (send-cmd app-name inst-name cmd))
  
  (GET "/prueba/:page" [page id name] (str "page=" page " id:" id " name:" name))

  (GET "/cbotimg" [app]
       {:status 200
        :headers {"Content-type" "image/jpeg"}
        :body (java.io.ByteArrayInputStream. (create-jpg (keyword app))) }
       )
  
  (route/resources "/")

  (route/not-found "Page not found"))

(def app
  (-> (session/wrap-session main-routes)
      (handler/site)
      (wrap-base-url)))
 
