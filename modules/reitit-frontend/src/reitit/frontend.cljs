(ns reitit.frontend
  "Utilities to implement frontend routing using Reitit.

  Controller is way to declare as data the side-effects and optionally
  other data related to the route."
  (:require [reitit.core :as reitit]
            [clojure.string :as str]
            goog.Uri
            [reitit.coercion :as coercion]))

;;
;; Utilities
;;

(defn query-params
  "Parse query-params from URL into a map."
  [^goog.Uri uri]
  (let [q (.getQueryData uri)]
    (->> q
         (.getKeys)
         (map (juxt keyword #(.get q %)))
         (into {}))))

(defn get-hash
  "Given browser hash starting with #, remove the # and
  end slashes."
  []
  (-> js/location.hash
      (subs 1)
      (str/replace #"/$" "")))

;;
;; Controller implementation
;;

(defn get-params
  "Get controller parameters given match. If controller provides :params
  function that will be called with the match. Default is nil."
  [controller match]
  (if-let [f (:params controller)]
    (f match)))

(defn apply-controller
  "Run side-effects (:start or :stop) for controller.
  The side-effect function is called with controller params."
  [controller method]
  (when-let [f (get controller method)]
    (f (::params controller))))

(defn- pad-same-length [a b]
  (concat a (take (- (count b) (count a)) (repeat nil))))

(defn apply-controllers
  "Applies changes between current controllers and
  those previously enabled. Resets controllers whose
  parameters have changed."
  [old-controllers new-match]
  (let [new-controllers (map (fn [controller]
                               (assoc controller ::params (get-params controller new-match)))
                             (:controllers (:data new-match)))
        changed-controllers (->> (map (fn [old new]
                                        ;; different controllers, or params changed
                                        (if (not= old new)
                                          {:old old, :new new}))
                                      (pad-same-length old-controllers new-controllers)
                                      (pad-same-length new-controllers old-controllers))
                                 (keep identity)
                                 vec)]
    (doseq [controller (map :old changed-controllers)]
      (apply-controller controller :stop))
    (doseq [controller (map :new changed-controllers)]
      (apply-controller controller :start))
    new-controllers))

(defn hash-change [router hash]
  (let [uri (goog.Uri/parse hash)
        match (or (reitit/match-by-path router (.getPath uri))
                  {:data {:name :not-found}})
        q (query-params uri)
        ;; Coerce if coercion enabled
        c (if (:result match)
            (coercion/coerce-request (:result match) {:query-params q
                                                      :path-params (:params match)})
            {:query q
             :path (:param match)})
        ;; Replace original params with coerced params
        match (-> match
                  (assoc :query (:query c))
                  (assoc :params (:path c)))]
    match))
