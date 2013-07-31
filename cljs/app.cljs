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

(defn vector-as-array
  "Creates JS Proxy around Clojure vector to make it look like JS array,
  without copying data"
  [v]
  (.create js/Proxy
           (js-obj
             "get" 
               (fn [_ prop]
                 (case prop
                   "length" (count v)
                   (get v prop)))
             "getPropertyDescriptor"
               (fn [obj prop]
                 (.getOwnPropertyDescriptor js/Object js/Array prop)))))

(defn observable-ref [r]
  (let [state (ko/observable (vector-as-array @r))]
    (add-watch r state (fn [obs _ _ new] (obs (vector-as-array new))))
    state))

(def view-model (atom []))

(ko/applyBindings (observable-ref view-model))

(aset (aget [] "__proto__")
      "get"
      (fn [index]
        (this-as this
                 (nth this index))))

(go
  (log "Calling get-edn ...")
  (let [headers (->> "/api/echo" get-edn <! :headers (sort-by first) vec)]
    (log headers)
     (doseq [h headers]
       (<! (timeout 500))
       (swap! view-model conj h))
     (<! (timeout 1000))
     (while (seq @view-model)
       (swap! view-model pop)
       (<! (timeout 500)))
     (<! (timeout 1000))
    (reset! view-model headers))
  (log "Finished"))
