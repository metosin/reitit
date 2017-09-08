(ns reitit.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            reitit.coercion-test
            reitit.core-test
            reitit.middleware-test
            reitit.ring-test
            reitit.spec-test))

(enable-console-print!)

(doo-tests 'reitit.coercion-test
           'reitit.core-test
           'reitit.middleware-test
           'reitit.ring-test
           'reitit.spec-test)
