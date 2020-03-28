(ns reitit.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            reitit.coercion-test
            reitit.core-test
            reitit.impl-test
            reitit.middleware-test
            reitit.ring-test
            reitit.spec-test
            reitit.exception-test
            reitit.frontend.core-test
            reitit.frontend.history-test
            reitit.frontend.controllers-test
            reitit.frontend.easy-test))

(enable-console-print!)

(doo-tests 'reitit.coercion-test
           'reitit.core-test
           'reitit.impl-test
           'reitit.middleware-test
           'reitit.ring-test
           'reitit.spec-test
           'reitit.exception-test
           'reitit.frontend.core-test
           'reitit.frontend.history-test
           'reitit.frontend.controllers-test
           'reitit.frontend.easy-test)
