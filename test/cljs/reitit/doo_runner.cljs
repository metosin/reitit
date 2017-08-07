(ns reitit.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            reitit.core-test))

(enable-console-print!)

(doo-tests 'reitit.core-test)
