(ns ont-app.graph-log.core-test
  (:require
   #?(:cljs [cljs.test :refer-macros [async deftest is testing]]
      :clj [clojure.test :refer :all])
   #?(:clj [clojure.java.io :as io])
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
    :refer [debug!
            error!
            fatal!
            info!
            log!
            log-graph
            log-reset!
            log-value!
            warn!
            ]
    ]))


;; EXAMPLES FROM README
(defn the-answer [whos-asking]
    (glog/log! :my-log/starting-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-the-answer 42))

(deftest readme-examples
  (log-reset!)
  (let [result (the-answer "Douglas")]
    (is (= (glog/entries)
           [:my-log/starting-the-answer_0 
            :my-log/returning-the-answer_1]))
    (is (= (dissoc (glog/show :my-log/starting-the-answer_0)
                   :glog/timestamp)
           {:rdf/type #{:my-log/starting-the-answer},
            :glog/executionOrder #{0},
            :my-log/whos-asking #{"Douglas"}}))
    (is (= (let [[e d] (glog/ith-entry 1)]
             [e (dissoc d :glog/timestamp)])
           [:my-log/returning-the-answer_1
            {:rdf/type #{:my-log/returning-the-answer},
             :glog/executionOrder #{1},
             :glog/value #{42}}]
           ))
        ))

;; 
(def initial-graph
  (add glog/ontology         
       [[:myLog/flag :rdf/type :glog/InformsUri]
        ]))

(defn dummy-test-1 []
  (log! :glog/StartingDummyTest :myLog/flag :myLog/StartFn :myLog/message "Hi")
  (log-value!
   :myLog/DummyTestReturn
   [:myLog/flag :myLog/ReturnValue :rlog/message "That's all folks"]
   42))


(defn dummy-test-with-log-levels []
  (warn! :glog/StartingDummyTest :myLog/flag :myLog/StartFn :myLog/message "Hi")
  (log-value!
   :myLog/DummyTestReturn
   [:glog/level :glog/DEBUG
    :myLog/flag :myLog/ReturnValue
    :rlog/message "That's all folks"]
   42))
 

(deftest test-logging
  (testing "Basic logging stuff"
    (glog/log-reset! initial-graph)
    (dummy-test-1)
    (let [rmts (glog/remove-timestamps)
          ]
      (is (= (glog/ith-entry  rmts 0)
             [:glog/StartingDummyTest_0_StartFn
              {:rdf/type #{:glog/StartingDummyTest},
               :glog/executionOrder #{0},
               :myLog/flag #{:myLog/StartFn},
               :myLog/message #{"Hi"}}]))
      (is (= (glog/ith-entry rmts 1)
             [:myLog/DummyTestReturn_1_ReturnValue
              {:rdf/type #{:myLog/DummyTestReturn},
               :glog/executionOrder #{1},
               :myLog/flag #{:myLog/ReturnValue},
               :rlog/message #{"That's all folks"},
               :glog/value #{42}}]
             ))
      (is (= (glog/entries)
             [:glog/StartingDummyTest_0_StartFn
              :myLog/DummyTestReturn_1_ReturnValue
              ])))

    (testing "Log levels with LogGraph at INFO"
      (glog/log-reset! (add initial-graph
                            [[:glog/LogGraph :glog/level :glog/INFO]]))
      (dummy-test-with-log-levels)
      (is (= (glog/entries)
             [:glog/StartingDummyTest_0_StartFn])))
    (testing "Log levels with LogGraph at DEBUG"
      (glog/log-reset! (add initial-graph
                            [[:glog/LogGraph :glog/level :glog/DEBUG]]))
      (dummy-test-with-log-levels)
      (is (= (glog/entries)
             [:glog/StartingDummyTest_0_StartFn
              :myLog/DummyTestReturn_1_ReturnValue]
             )))
    (testing "Log levels with LogGraph at DEBUG, globally setting entry levels"
      (glog/log-reset! (add initial-graph
                            [[:glog/LogGraph :glog/level :glog/INFO]
                             [:glog/StartingDummyTest :glog/level :glog/TRACE]
                             ]))
      ;; ... we can either set the prevailing log level at reset time
      ;; or with set-level! ...
      (glog/set-level! :myLog/DummyTestReturn :glog/INFO)
      (dummy-test-with-log-levels)
      (is (= (glog/entries)
             [:myLog/DummyTestReturn_0_ReturnValue]
             )))
    ))
    
#?(:clj 
   (deftest test-reset
     (testing "Reset should write to disk if there's a path function"
       (glog/log-reset!)
       (let [archive-dir  "/tmp/testGlog"
             ;; define the compiled function that generates the
             ;; target file name...
             
             ;; Declare said function as SaveToFn...
             configured-graph
             (add initial-graph
                  [[:glog/ArchiveFn
                    :igraph/compiledAs glog/save-to-archive]
                   [:glog/LogGraph :glog/archiveDirectory archive-dir]
                   ])
             ]
         (when (.exists (io/file archive-dir))
           (doseq [f (rest (file-seq (io/file archive-dir)))]
             (io/delete-file f))
           (io/delete-file (io/file archive-dir)))
         (glog/log-reset! configured-graph)
         ;; This reset establishes test-log-path as the SaveToFn
         (glog/log! ::TestEntry1)
         (glog/log-reset! configured-graph)
         ;; ... this should write the file...
         (let [log-path (igraph/unique
                         (@glog/log-graph
                          :glog/LogGraph
                          :glog/continuingFrom))
               ]
           ;;(is (= log-path nil))
           (is (= (.exists (io/as-file log-path))
                  true))
           (let [g (add (make-graph)
                      (read-string (slurp (io/as-file log-path))))
               ]
           (is (= (count (igraph/query g [[:?entry :rdf/type ::TestEntry1]]))
                  1))))))))


      

