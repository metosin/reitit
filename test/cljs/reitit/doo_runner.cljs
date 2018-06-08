(ns reitit.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            reitit.coercion-test
            reitit.core-test
            reitit.impl-test
            reitit.middleware-test
            reitit.ring-test
            #_reitit.spec-test
            reitit.frontend.core-test))

(enable-console-print!)

(doo-tests 'reitit.coercion-test
           'reitit.core-test
           'reitit.impl-test
           'reitit.middleware-test
           'reitit.ring-test
           #_'reitit.spec-test
           'reitit.frontend.core-test)
