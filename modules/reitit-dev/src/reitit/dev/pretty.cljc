(ns reitit.dev.pretty
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [reitit.exception :as exception]
            [arrangement.core]
    ;; spell-spec
            [spell-spec.expound]
    ;; expound
            [expound.ansi]
            [expound.alpha]
    ;; fipp
            [fipp.visit]
            [fipp.edn]
            [fipp.ednize]
            [fipp.engine]))

;;
;; colors
;;

(def colors
  {:white 255
   :text 253
   :grey 245
   :title-dark 32
   :title 45
   :red 217

   :string 180
   :comment 243
   :doc 223
   :core-form 39
   :function-name 178
   :variable-name 85
   :constant 149
   :type 123
   :foreign 220
   :builtin 167
   :half-contrast 243
   :half-contrast-inverse 243
   :eldoc-varname 178
   :eldoc-separator 243
   :arglists 243
   :anchor 39
   :light-anchor 39
   :apropos-highlight 45
   :apropos-namespace 243
   :error 196})

(comment
  (defn- -color [color & text]
    (str "\033[38;5;" (colors color color) "m" (apply str text) "\u001B[0m"))

  (doseq [c (range 0 255)]
    (println (-color c "kikka") "->" c))

  (doseq [[n c] colors]
    (println (-color c "kikka") "->" c n))

  (doseq [[k v] expound.ansi/sgr-code]
    (println (expound.ansi/sgr "kikka" k) "->" k)))

(defn- -start [x] (str "\033[38;5;" x "m"))
(defn- -end [] "\u001B[0m")

(defn color [color & text]
  [:span
   [:pass (-start (colors color))]
   (apply str text)
   [:pass (-end)]])

;;
;; EDN
;;

(defrecord EdnPrinter [symbols print-meta print-length print-level]

  fipp.visit/IVisitor

  (visit-unknown [this x]
    (fipp.visit/visit this (fipp.ednize/edn x)))

  (visit-nil [_]
    (color :text "nil"))

  (visit-boolean [_ x]
    (color :text (str x)))

  (visit-string [_ x]
    (color :string (pr-str x)))

  (visit-character [_ x]
    (color :text (pr-str x)))

  (visit-symbol [_ x]
    (color :text (str x)))

  (visit-keyword [_ x]
    (color :constant (pr-str x)))

  (visit-number [_ x]
    (color :text (pr-str x)))

  (visit-seq [this x]
    (if-let [pretty (symbols (first x))]
      (pretty this x)
      (fipp.edn/pretty-coll this (color :text "(") x :line (color :text ")") fipp.visit/visit)))

  (visit-vector [this x]
    (fipp.edn/pretty-coll this (color :text "[") x :line (color :text "]") fipp.visit/visit))

  (visit-map [this x]
    (let [xs (sort-by identity (fn [a b] (arrangement.core/rank (first a) (first b))) x)]
      (fipp.edn/pretty-coll this (color :text "{") xs [:span (color :text ",") :line] (color :text "}")
                            (fn [printer [k v]]
                              [:span (fipp.visit/visit printer k) " " (fipp.visit/visit printer v)]))))

  (visit-set [this x]
    (let [xs (sort-by identity (fn [a b] (arrangement.core/rank a b)) x)]
      (fipp.edn/pretty-coll this "#{" xs :line "}" fipp.visit/visit)))

  (visit-tagged [this {:keys [tag form]}]
    (let [object? (= 'object tag)
          tag-f (if (map? form) (partial color :type) identity)]
      [:group "#" (tag-f (pr-str tag))
       (when (or (and print-meta (meta form)) (not (coll? form)))
         " ")
       (if object?
         [:group "["
          [:align
           (color :type (first form)) :line
           (color :text (second form)) :line
           (fipp.visit/visit this (last form))] "]"]
         (fipp.visit/visit this form))]))

  (visit-meta [this m x]
    (if print-meta
      [:align [:span "^" (fipp.visit/visit this m)] :line (fipp.visit/visit* this x)]
      (fipp.visit/visit* this x)))

  (visit-var [_ x]
    [:text (str x)])

  (visit-pattern [_ x]
    [:text (pr-str x)])

  (visit-record [this x]
    (fipp.visit/visit this (fipp.ednize/record->tagged x))))

(defn printer
  ([]
   (printer nil))
  ([options]
   (map->EdnPrinter
     (merge
       {:width 80
        :symbols {}
        :print-length *print-length*
        :print-level *print-level*
        :print-meta *print-meta*}
       options))))

(defn pprint
  ([x] (pprint x {}))
  ([x options]
   (let [printer (printer (dissoc options :margin))
         margin (apply str (take (:margin options 0) (repeat " ")))]
     (binding [*print-meta* false]
       (fipp.engine/pprint-document [:group margin [:group (fipp.visit/visit printer x)]] options)))))

(defn print-doc [doc printer]
  (fipp.engine/pprint-document doc {:width (:width printer)}))

(defn repeat-str [s n]
  (apply str (take n (repeat s))))

;; TODO: this is hack, but seems to work and is safe.
(defn source-str [[target _ file line]]
  (try
    (if (and (not= 1 line))
      (let [file-name (str/replace file #"(.*?)\.\S[^\.]+" "$1")
            target-name (name target)
            ns (str (subs target-name 0 (or (str/index-of target-name (str file-name "$")) 0)) file-name)]
        (str ns ":" line))
      "repl")
    (catch #?(:clj Exception, :cljs js/Error) _
      "unknown")))

(defn title [message source {:keys [width]}]
  (let [between (- width (count message) 8 (count source))]
    [:group
     (color :title-dark "-- ")
     (color :title message " ")
     (color :title-dark (repeat-str "-" between) " ")
     (color :title source) " "
     (color :title-dark (str "--"))]))

(defn footer [{:keys [width]}]
  (color :title-dark (repeat-str "-" width)))

(defn text [& text]
  (apply color :text text))

(defn edn
  ([x] (edn x {}))
  ([x options]
   (with-out-str (pprint x options))))

(defn exception-str [message source printer]
  (with-out-str
    (print-doc
      [:group
       (title "Router creation failed" source printer)
       [:break] [:break]
       message
       [:break]
       (footer printer)]
      printer)))

(defmulti format-exception (fn [type _ _] type))

(defn exception [e]
  (let [data (-> e ex-data :data)
        message (format-exception (-> e ex-data :type) #?(:clj (.getMessage ^Exception e) :cljs (ex-message e)) data)
        source #?(:clj  (->> e Throwable->map :trace
                             (drop-while #(not= (name (first %)) "reitit.core$router"))
                             (drop-while #(= (name (first %)) "reitit.core$router"))
                             next first source-str)
                  :cljs "unknown")]
    (ex-info (exception-str message source (printer)) (assoc (or data {}) ::exception/cause e))))

(defn de-expound-colors [^String s mappings]
  (let [s' (reduce
             (fn [s [from to]]
               (.replace ^String s
                         ^String (expound.ansi/esc [from])
                         ^String (-start (colors to))))
             s mappings)]
    (.replace ^String s'
              ^String (expound.ansi/esc [:none])
              (str (expound.ansi/esc [:none]) (-start (colors :text))))))

(defn fippify [s]
  [:align
   (-> s
       (de-expound-colors {:cyan :grey
                           :red :red
                           :magenta :grey
                           :green :constant})
       (str/split #"\n") (interleave (repeat [:break])))])

(defn indent [x n]
  [:group (repeat-str " " n) [:align x]])

(def expound-printer
  (expound.alpha/custom-printer
    {:theme :figwheel-theme
     :show-valid-values? false
     :print-specs? false}))

;;
;; Formatters
;;

(defmethod format-exception :default [_ message data]
  (into [:group (text message)] (if data [[:break] [:break] (edn data)])))

(defmethod format-exception :path-conflicts [_ _ conflicts]
  [:group
   (text "Router contains conflicting route paths:")
   [:break] [:break]
   (letfn [(path-report [path route-data]
             [:span (color :grey
                           (if (:conflicting route-data) "   " "-> ")
                           path
                           " ")
              (edn (not-empty (select-keys route-data [:conflicting])))])]
     (into
       [:group]
       (mapv
         (fn [[[path route-data] vals]]
           [:group
            (path-report path route-data)
            (into
              [:group]
              (map
                (fn [[path route-data]] (path-report path route-data))
                vals))
            [:break]])
         conflicts)))
   [:span (text "Either fix the conflicting paths or disable the conflict resolution")
    [:break] (text "by setting route data for conflicting route: ") [:break] [:break]
    (edn {:conflicting true} {:margin 3})
    [:break] (text "or by setting a router option: ") [:break] [:break]
    (edn {:conflicts nil} {:margin 3})]
   [:break]
   (color :white "https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-conflicts")
   [:break]])

(defmethod format-exception :name-conflicts [_ _ conflicts]
  [:group
   (text "Router contains conflicting route names:")
   [:break] [:break]
   (into
     [:group]
     (mapv
       (fn [[name vals]]
         [:group
          [:span (text name)]
          [:break]
          (into
            [:group]
            (map
              (fn [p] [:span (color :grey "-> " p) [:break]])
              (mapv first vals)))
          [:break]])
       conflicts))
   (color :white "https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-conflicts")
   [:break]])

(defmethod format-exception :reitit.spec/invalid-route-data [_ _ {:keys [problems]}]
  [:group
   (text "Invalid route data:")
   [:break] [:break]
   (into
     [:group]
     (map
       (fn [{:keys [data path spec scope]}]
         [:group
          [:span (color :grey "-- On route -----------------------")]
          [:break]
          [:break]
          (text path) (if scope [:span " " (text scope)])
          [:break]
          [:break]
          (-> (s/explain-data spec data)
              (expound-printer)
              (with-out-str)
              (fippify))
          [:break]])
       problems))
   (color :white "https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-data-validation")
   [:break]])

(defmethod format-exception :reitit.impl/merge-data [_ _ {:keys [path left right exception]}]
  [:group
   (text "Error merging route-data:")
   [:break] [:break]
   [:group
    [:span (color :grey "-- On route -----------------------")]
    [:break]
    [:break]
    (text path)
    [:break]
    [:break]
    [:span (color :grey "-- Exception ----------------------")]
    [:break]
    [:break]
    (color :red (exception/get-message exception))
    [:break]
    [:break]
    (edn left {:margin 3})
    [:break]
    (edn right {:margin 3})]
   [:break]
   (color :white "https://cljdoc.org/d/metosin/reitit/CURRENT/doc/basics/route-data")
   [:break]])
