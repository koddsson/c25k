(ns c25k.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [goog.dom :as dom]
            [goog.events :as events]
            [cljs.core.async :refer [put! chan <! >! timeout ]]
            [alandipert.storage-atom :refer [local-storage]]))

; c25k webapp.
;
; TODO: [description]

; Makes so that `(print ..)` commands get sent to `console.log(..)`.
(enable-console-print!)

; Create a audio player
(def player (.createElement js/document "audio"))

; Define a shorthand for interop with javascript to play audio
(defn play [sound]
  (set! (.-src player) sound)
  (.play player))

(def warmup {:type "walk" :time (* 5 60)})
(def cooldown {:type "walk" :time (* 5 60)})

(def week 
  ;; Week 1
  [(repeat 3 (flatten [warmup
                      (repeat 6 [{:type "jog" :time 60} {:type "walk" :time 90}])
                      cooldown]))
  ;; Week 2
  (repeat 3 (flatten [warmup
                      (repeat 6 [{:type "jog" :time 90} {:type "walk" :time 120}])
                      cooldown]))
  ;; Week 3
  (repeat 3 (flatten [warmup
                      (repeat 2 [{:type "jog" :time 90} {:type "walk" :time 90}
                                 {:type "jog" :time (* 3 60)} {:type "walk" :time (* 3 60)}])
                      cooldown]))])

(defn flip [f] 
  "A helper function that will call a function with it's arguments list reverted.
   
  TODO: Refactor;
   <amalloy> koddsson: no, probably not. it's kinda unusual to have a ->> chain
     where you want something to go in at the front; usually it's better to
     have a -> chain, possibly with a -> inside it
   <amalloy> eg, instead of (->> x (map whatever) ((flip blah) y)), you can 
     write (-> x (->> (map whatever)) (blah y))"
  (fn [& args] (apply f (reverse args))))

(def sum 
  "The number of minutes a week's run is gonna take"
  (->> week
       (map :time)
       (reduce +)
       ((flip /) 60)))

(defn listen [el type]
  "Event listener implementation from http://swannodette.github.io/2013/11/07/clojurescript-101/"
  (let [out (chan)]
    (events/listen el type
      (fn [e] (put! out e)))
    out))

(defn start [plan]
  ""
  (let [c (chan)]
    (go (while true
          (let [v (<! c)]
            (if (= v "walk") (play "walking.m4a"))
            (if (= v "jog") (play "running.m4a"))
            (println "Read: " v))))
    (go (loop [acc 0 [curr & rest] plan]
          (when (not (nil? curr))
            (let [time (* 10 (:time curr))]
              (>! c (:type curr))
              (<! (timeout (+ acc time)))
              (recur (+ acc time) rest)))))))

(defonce prefs (local-storage (atom {}) :prefs))

; Event handler for button clicks. 
(let [clicks (listen (dom/getElement "button") "click")]
  (go (while true (<! clicks)
                  (->> (dom/getElement "weekSelector")
                       (.-value)
                       (js/parseInt)
                       (nth week)
                       ((flip nth) (js/parseInt (.-value (dom/getElement "daySelector"))))
                       (start)))))
