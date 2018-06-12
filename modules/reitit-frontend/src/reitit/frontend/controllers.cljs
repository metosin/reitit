(ns reitit.frontend.controllers)

(defn- pad-same-length [a b]
  (concat a (take (- (count b) (count a)) (repeat nil))))

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
