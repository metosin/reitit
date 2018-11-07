(ns reitit.ring.middleware.dev
  (:require [lambdaisland.deep-diff :as ddiff]
            [lambdaisland.deep-diff.printer :as printer]
            [puget.color :as color]
            [reitit.core :as r]))

(def printer
  (-> (printer/puget-printer)
      (assoc :width 70)
      (update :color-scheme merge {:middleware [:blue]})))

(defn diff-doc [x y z]
  [:group
   [:span "--- Middleware " (if z (color/document printer :middleware (str z " "))) "---" :break :break]
   [:nest (printer/format-doc (if x (ddiff/diff x y) y) printer)]
   :break])

(defn polish [request]
  (dissoc request ::r/match ::r/router ::original ::previous))

(defn print-diff-middleware
  ([]
   (print-diff-middleware nil))
  ([{:keys [name]}]
   {:name ::debug
    :wrap (fn [handler]
            (fn [{:keys [::original ::previous] :as request}]
              (printer/print-doc (diff-doc (polish previous) (polish request) name) printer)
              (handler (-> request
                           (update ::original (fnil identity request))
                           (assoc ::previous request)))))}))

(defn print-request-diffs
  "A middleware chain transformer that adds a request-diff printer between all middleware"
  [chain]
  (reduce
    (fn [chain mw]
      (into chain [mw (print-diff-middleware (select-keys mw [:name]))]))
    [(print-diff-middleware)] chain))
