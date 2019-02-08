(ns reitit.frontend.controllers
  "Provides apply-controllers function")

(defn- pad-same-length [a b]
  (concat a (take (- (count b) (count a)) (repeat nil))))

(def ^:private params-warning
  (delay (js/console.warn "Reitit-frontend controller :params is deprecated. Replace with :identity or :parameters option.")))

(defn get-identity
  "Get controller identity given controller and match.

  To select interesting properties from Match :parameters option can be set.
  Value should be param-type => [param-key]
  Resulting value is map of param-type => param-key => value.

  For other uses, :identity option can be used to provide function from
  Match to identity.

  Default value is nil, i.e. controller identity doesn't depend on Match."
  [{:keys [identity parameters params]} match]
  (assert (not (and identity parameters))
          "Use either :identity or :parameters for controller, not both.")
  (when params
    @params-warning)
  (cond
    parameters
    (into {} (for [[param-type ks] parameters]
               [param-type (select-keys (get (:parameters match) param-type) ks)]))

    identity
    (identity match)

    ;; Support deprecated :params for transition period. Can be removed later.
    params
    (params match)

    :else nil))

(defn apply-controller
  "Run side-effects (:start or :stop) for controller.
  The side-effect function is called with controller identity value."
  [controller method]
  (when-let [f (get controller method)]
    (f (::identity controller))))

(defn apply-controllers
  "Applies changes between current controllers and
  those previously enabled. Reinitializes controllers whose
  identity has changed."
  [old-controllers new-match]
  (let [new-controllers (mapv (fn [controller]
                                (assoc controller ::identity (get-identity controller new-match)))
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
