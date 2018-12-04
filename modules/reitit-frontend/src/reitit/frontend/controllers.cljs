(ns reitit.frontend.controllers
  "Provides apply-controllers function")

(defn- pad-same-length [a b]
  (concat a (take (- (count b) (count a)) (repeat nil))))

(defn get-params
  "Get controller parameters given match. If controller provides :params
  function that will be called with the match. Default is nil."
  [controller match]
  (if-let [f (:params controller)]
    (f match)))

(def static
  "Static params means that the identity of controller
  doesn't not depend on Match, i.e. any parameters.

  This is same as just not defining :params."
  nil)

(defn parameters
  "Given map of parameter-type => list of keys,
  returns function taking Match and returning
  value containing given parameter types and their
  keys.

  The resulting function can be used for :params."
  [p]
  (fn [match]
    (into {} (for [[param-type ks] p]
               [param-type (select-keys (get (:parameters match) param-type) ks)]))))

(defn apply-controller
  "Run side-effects (:start or :stop) for controller.
  The side-effect function is called with controller params."
  [controller method]
  (when-let [f (get controller method)]
    (f (::params controller))))

(defn apply-controllers
  "Applies changes between current controllers and
  those previously enabled. Reinitializes controllers whose
  identity has changed."
  [old-controllers new-match]
  (let [new-controllers (mapv (fn [controller]
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
    (doseq [controller (reverse (map :old changed-controllers))]
      (apply-controller controller :stop))
    (doseq [controller (map :new changed-controllers)]
      (apply-controller controller :start))
    new-controllers))
