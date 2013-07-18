(ns server
  (:use ring.adapter.jetty
        ring.middleware.file
        ring.middleware.file-info
        ring.middleware.format-response
        [ring.util.response :exclude (not-found)]
        compojure.core
        compojure.route
  ))

(defroutes handler
  (GET "/" [] (file-response "default.html" {:root "html"}))
  (GET "/api/echo" request
       {:status 200
        :body (dissoc request :body)})
  (files "" {:root "html"})
  (not-found "<h1>404 Page not found</h1>")
)

(def app (-> handler
             wrap-clojure-response))
  
(defonce server
  (run-jetty #'app {:port 3000 :join? false}))
