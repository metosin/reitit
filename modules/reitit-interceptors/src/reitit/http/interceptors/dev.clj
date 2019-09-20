(ns reitit.http.interceptors.dev
  (:require [lambdaisland.deep-diff :as ddiff]
            [lambdaisland.deep-diff.printer :as printer]
            [puget.color :as color]
            [reitit.core :as r]))

(def printer
  (-> (printer/puget-printer)
      (assoc :width 70)
      (update :color-scheme merge {:name [:blue]})))

(defn- empty-context? [ctx]
  (-> ctx :request nil?))

(defn diff-doc [stage name previous current]
  [:group
   [:span "--- " (str stage) " " (if name (color/document printer :name (str name " "))) "---" :break :break]
   [:nest (printer/format-doc (if (empty-context? previous) current (ddiff/diff previous current)) printer)]
   :break])

(defn- polish [ctx]
  (-> ctx
      (dissoc ::original ::previous :stack :queue)
      (update :request dissoc ::r/match ::r/router)))

(defn- handle [name stage]
  (fn [{::keys [previous] :as ctx}]
    (let [current (polish ctx)
          previous (polish previous)]
      (printer/print-doc (diff-doc stage name previous current) printer)
      (-> ctx
          (update ::original (fnil identity ctx))
          (assoc ::previous ctx)))))

(defn diff-interceptor
  [stages {:keys [enter leave error name] :as interceptor}]
  (if (->> (select-keys interceptor stages) (vals) (keep identity) (seq))
    (cond-> {:name ::diff}
            (and enter (stages :enter)) (assoc :enter (handle name :enter))
            (and leave (stages :leave)) (assoc :leave (handle name :leave))
            (and error (stages :error)) (assoc :error (handle name :error)))))

(defn print-context-diffs
  "A interceptor chain transformer that adds a context diff printer between all interceptors"
  [interceptors]
  (reduce
    (fn [chain interceptor]
      (into chain (keep identity [(diff-interceptor #{:leave :error} interceptor)
                                  interceptor
                                  (diff-interceptor #{:enter} interceptor)])))
    [(diff-interceptor #{:enter :leave :error} {:enter identity})] interceptors))
