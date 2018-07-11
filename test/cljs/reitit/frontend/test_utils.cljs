(ns reitit.frontend.test-utils)

(defn capture-console [f]
  (let [messages (atom [])
        original-console-warn js/console.warn
        log (fn [t & message]
              (swap! messages conj {:type t
                                    :message message}))
        value (try
                (set! js/console.warn (partial log :warn))
                (f)
                (finally
                  (set! js/console.warn original-console-warn)))]
    {:value value
     :messages @messages}))
