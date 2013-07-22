(ns app
  (:require
    [goog.net.XhrIo :as xhr]
    [cljs.reader :refer [read-string]]
    [cljs.core.async :as async :refer [chan close! put! timeout]]
    [dommy.core  :as dom]
  )
  (:require-macros
    [cljs.core.async.macros :refer [go alt!]]
    [dommy.macros :refer [sel sel1]]
  )
)

(defn log [& s]
  (.log js/console (apply str s)))

(log "Started")

(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                  (put! ch (-> event .-target .getResponseText))
                  (close! ch)))
    ch))

(defn get-edn [url]
  (go 
    (-> (GET url) <! read-string)))

(defn observable [val]
  (ko/computed
    (let [state (ko/observable (clj->js val))]
      (js-obj
        "read"  (fn [] (state))
        "write" (fn [new] (state (clj->js new)))))))

(def view-model (observable []))

(ko/applyBindings view-model)
 
(go
  (log "Calling get-edn ...")
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first))]
    (log headers)
    (view-model headers))
  (log "Finished"))
