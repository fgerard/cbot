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
            ;;[ring.middleware.json-params :as rjson]
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
  
  (GET "/apps/:app-name/:inst-name" [app-name inst-name cmd uuid timeout msg json]
       (send-cmd app-name inst-name cmd {:uuid uuid
                                         :timeout (to-long timeout)
                                         :msg msg
                                         :json (if (and json (.equalsIgnoreCase json "true")) true false)}))

  (POST "/apps/:app-name/:inst-name" [app-name inst-name cmd uuid timeout msg json]
        (send-cmd app-name inst-name cmd {:uuid uuid
                                          :timeout (to-long timeout)
                                          :msg msg
                                          :json (if (and
                                                     json
                                                     (.equalsIgnoreCase json "true")) true false)}))
  
  (GET "/cbotimg/:app" [app]
       {:status 200
        :headers {"Content-type" "image/jpeg"}
        :body (java.io.ByteArrayInputStream. (ui/create-jpg (keyword app))) })

  (GET "/operations" []
       (get-operations))

  (POST "/store/:app-name" [app-name conf]
        (json/json-str (app-save-conf app-name conf)))
  
  (GET "/log" []
       (report-log))
  
  (route/resources "/")

  (route/not-found "Page not found"))

(comment (rjson/wrap-json-params))

(def app
  (-> (session/wrap-session main-routes)
      (handler/site)
      (wrap-base-url)))
 
