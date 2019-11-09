(ns ont-app.graph-log.doo
  (:require [doo.runner :refer-macros [doo-tests]]
            [ont-app.graph-log.core-test]
            ))

(doo-tests
 'ont-app.graph-log.core-test
 )
