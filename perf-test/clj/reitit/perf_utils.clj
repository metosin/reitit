(ns reitit.perf-utils
  (:require [criterium.core :as cc]
            [criterium.stats :as stats]
            [clojure.string :as str]
            [reitit.core :as reitit]))

;;
;; Histograms
;;

(def +sparks+ "▁▂▃▄▅▆▇█")

(defn sparkline [xs]
  (let [min- (reduce min xs)
        max- (reduce max xs)
        d    (- max- min-)
        sc   (dec (count +sparks+))
        c    #(nth +sparks+ (int (* sc (/ (- % min-) d))))]
    (str/join (map c xs))))

(defn bin
  ([xs] (bin xs nil))
  ([xs {:keys [bin-count min-x max-x] :or {bin-count 10, min-x :auto max-x :auto}}]
   (let [min- (if (= :auto min-x) (reduce min xs) min-x)
         max- (if (= :auto max-x) (reduce max xs) max-x)
         d    (double (- max- min-))
         bins (vec (repeat bin-count 0))
         bc   (dec (count bins))]
     (reduce (fn [bins x]
               (let [bin (int (Math/round (* bc (/ (- x min-) d))))]
                 (if (<= 0 bin bc)
                   (update bins bin inc)
                   bins)))
             bins
             xs))))

;;
;; Benchmarking
;;

(defn raw-title [color s]
  (let [line-length (transduce (map count) max 0 (str/split-lines s))
        banner      (apply str (repeat line-length "#"))]
    (println (str color banner "\u001B[0m"))
    (println (str color s"\u001B[0m"))
    (println (str color banner "\u001B[0m"))))

(def title (partial raw-title "\u001B[35m"))
(def suite (partial raw-title "\u001B[32m"))

(defmacro bench! [name & body]
  `(do
     (title ~name)
     (println ~@body)
     (cc/quick-bench ~@body)))

(defmacro b! [name & body]
  `(do
     (title ~name)
     (println)
     (println "\u001B[33m" ~@body "\u001B[0m")
     (let [{[lower#] :lower-q :as res#} (cc/quick-benchmark (do ~@body) nil)
           µs# (* 1000000 lower#)
           ns# (* 1000 µs#)]
       (println "\u001B[32m\n" (format "%1$10.2fns" ns#) "\u001B[0m")
       (println "\u001B[32m" (format "%1$10.2fµs" µs#) "\u001B[0m")
       (println)
       (cc/report-result res#))
     (println)))

(defn valid-urls [router]
  (->>
    (for [name (reitit/route-names router)
          :let [match (reitit/match-by-name router name)
                params (if (reitit/partial-match? match)
                         (-> match :required (zipmap (range))))]]
      (:path (reitit/match-by-name router name params)))
    (into [])))

(defrecord Request [uri path-info request-method])

(defn- s->ns [x] (int (* x 1e9)))
(defn- get-mean-ns [results] (int (* (first (:sample-mean results)) 1e9)))
(defn- get-lower-q-ns [results] (int (* (first (:lower-q results)) 1e9)))

(defn bench-routes [routes req f]
  (let [router (reitit/router routes)
        urls (valid-urls router)]
    (mapv
      (fn [path]
        (let [request (map->Request (req path))
              results (cc/quick-benchmark (f request) {})
              mean (get-mean-ns results)
              lower (get-lower-q-ns results)]
          (println path "=>" lower "/" mean "ns")
          [path results]))
      urls)))

(defn bench [routes req no-paths?]
  (let [routes (mapv (fn [[path name]]
                       (if no-paths?
                         [(str/replace path #"\:" "") name]
                         [path name])) routes)
        router (reitit/router routes)]
    (doseq [[path results] (bench-routes routes req #(reitit/match-by-path router %))]
      (println path "\t" (get-mean-ns results) (get-lower-q-ns results)))))

;;
;; Perf tests
;;

(def handler (constantly {:status 200, :body "ok"}))

(defn bench!! [routes req verbose? name f]
  (println)
  (suite name)
  (println)
  (let [times (for [[path results] (bench-routes routes req f)]
                (do
                  (when verbose? (println (format "%7s\t%7s" (get-mean-ns results) (get-lower-q-ns results)) "\t" path))
                  results))
        ;; The samples are of equal size, so mean of means is okay.
        mean-ns (s->ns (stats/mean (map (comp first :mean) times)))
        samples (mapcat (fn [x] (map #(/ % (:execution-count x)) (:samples x))) times)
        min-ns  (int (reduce min samples))
        max-ns  (int (reduce max samples))]
    (title (str "mean of means: " (format "%4d" mean-ns) "\n"
                "distribution:  " (format "%4d"  min-ns) " " (sparkline (bin samples {:bin-count 20})) " " (format "%4d" max-ns)))))
