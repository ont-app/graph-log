(ns ont-app.graph-log.core-test
  (:require
   #?(:cljs [cljs.test :refer-macros [async deftest is testing]]
      :clj [clojure.test :refer :all])
   [clojure.string :as str]
   [clojure.set :as set]
   [ont-app.igraph.core :as igraph
    :refer [add
            difference
            query
            ]]
   [ont-app.igraph.graph :as g
    :refer [make-graph
            ]]
    
   [ont-app.graph-log.core :as glog
    :refer [
            log
            log-graph
            log-reset!
            log-value
            ]
    ]))


(def initial-graph
  (add glog/ontology         
       [[:myLog/flag :rdf/type :glog/InformsUri]
        ]))

(defn dummy-test-1 []
  (log :glog/StartingDummyTest :myLog/flag :myLog/StartFn :rlog/message "Hi")
  (Thread/sleep 10)
  (log-value
   :myLog/DummyTestReturn
   [:myLog/flag :myLog/ReturnValue :rlog/message "That's all folks"]
   42))

   
(deftest test-logging
  (testing "Basic logging stuff"
    (glog/log-reset! initial-graph)
    (dummy-test-1)
    (let [types-query (set (query (glog/remove-timestamps @log-graph)
                                  [[:?entry :rdf/type :?type]]))
          ]
      (is (= types-query
             #{
               {:?entry :glog/executionOrder,
                :?type :rdf/Property}
               {:?entry :glog/timestamp,
                :?type :rdf/Property}
               {:?entry :myLog/flag,
                :?type :glog/InformsUri}
               {:?entry :glog/StartingDummyTest_4_StartFn,
                :?type :glog/StartingDummyTest}
               {:?entry :myLog/DummyTestReturn_5_ReturnValue,
                :?type :myLog/DummyTestReturn}
               }
          )
          (= (glog/entry-order)
             [:glog/StartingDummyTest_4_StartFn
              :myLog/DummyTestReturn_5_ReturnValue]
             )))))

