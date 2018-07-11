(ns reitit.frontend.test-utils)

(defn capture-console [f]
  (let [messages (atom [])
        original-console js/console
        log (fn [t & message]
              (swap! messages conj {:type t
                                    :message message}))
        value (try
                (set! js/console #js {:log (partial log :log)
                                      :warn (partial log :warn)
                                      :info (partial log :info)
                                      :error (partial log :error)
                                      :debug (partial log :debug)})
                (f)
                (finally
                  (set! js/console original-console)))]
    {:value value
     :messages @messages}))
