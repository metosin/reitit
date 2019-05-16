(ns reitit.exception
  (:require [clojure.string :as str]))

(defn fail!
  ([type]
   (fail! type nil))
  ([type data]
   (throw (ex-info (str type) {:type type, :data data}))))

(defn get-message [e]
  #?(:clj (.getMessage ^Exception e) :cljs (ex-message e)))

(defmulti format-exception (fn [type _ _] type))

(defn exception [e]
  (let [data (ex-data e)
        message (format-exception (:type data) (get-message e) (:data data))]
    ;; there is a 3-arity version (+cause) of ex-info, but the default repl error message is taken from the cause
    (ex-info message (assoc (or data {}) ::cause e))))

;;
;; Formatters
;;

(defmethod format-exception :default [_ message data]
  (str message (if data (str "\n\n" (pr-str data)))))

(defmethod format-exception :path-conflicts [_ _ conflicts]
  (apply str "Router contains conflicting route paths:\n\n"
         (mapv
           (fn [[[path] vals]]
             (str "   " path "\n-> " (str/join "\n-> " (mapv first vals)) "\n\n"))
           conflicts)))

(defmethod format-exception :name-conflicts [_ _ conflicts]
  (apply str "Router contains conflicting route names:\n\n"
         (mapv
           (fn [[name vals]]
             (str name "\n-> " (str/join "\n-> " (mapv first vals)) "\n"))
           conflicts)))

(defmethod format-exception :reitit.impl/merge-data [_ _ data]
  (str "Error merging route-data\n\n" (pr-str data)))
