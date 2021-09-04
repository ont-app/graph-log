(ns ont-app.graph-log.core-test
  (:require
   #?(:cljs [cljs.test :refer-macros [async deftest is testing]]
      :clj [clojure.test :refer :all])
   #?(:clj [clojure.java.io :as io])
   [clojure.string :as str]
   [clojure.set :as set]
   ;; 3rd party
   [taoensso.timbre :as timbre]
   ;; ont-app 
   [ont-app.igraph.core :as igraph
    :refer [add
            difference
            query
            ]]
   [ont-app.igraph.graph :as native-normal
    :refer [make-graph
            ]]
    
   [ont-app.graph-log.core :as glog
    :refer [
            log!
            log-graph
            log-reset!
            log-value!
            ]]
   #?(:clj [ont-app.graph-log.levels :refer :all])
   #?(:cljs [ont-app.graph-log.levels :refer-macros [debug
                                                     fatal
                                                     info
                                                     value-info
                                                     ]])

   ) ;; :require
)

;; EXAMPLES FROM README

(defn get-the-answer [whos-asking]
    (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
  (glog/log-value! :my-log/returning-get-the-answer 42))

(deftest readme-examples
  (testing "Simple usage"
    (log-reset!)
    (let [result (get-the-answer "Douglas")]
      (is (= (glog/entries)
             [:my-log/starting-get-the-answer_0 
              :my-log/returning-get-the-answer_1]))

      (is (= (dissoc (glog/show :my-log/starting-get-the-answer_0)
                     :glog/timestamp)
             {:rdf/type #{:my-log/starting-get-the-answer},
              :glog/executionOrder #{0},
              :my-log/whos-asking #{"Douglas"}}))

      (is (= (let [[e d] (glog/ith-entry 1)]
               [e (dissoc d :glog/timestamp)])
             [:my-log/returning-get-the-answer_1
              {:rdf/type #{:my-log/returning-get-the-answer},
               :glog/executionOrder #{1},
               :glog/value #{42}}]
             ))
      (is (= (glog/query-log
              [[:?starting :rdf/type :my-log/starting-get-the-answer]
               [:?starting :my-log/whos-asking :?asker]
               ])
             #{{:?starting :my-log/starting-get-the-answer_0, :?asker "Douglas"}}))))

  (testing "Configuring the log-graph"
    (log-reset!)
    (is (= (@glog/log-graph :glog/INFO)
           {:rdf/type #{:glog/Level},
            :glog/priority #{3},
            :rdfs/comment #{"A standard logging level"}}))
    (is (= (@glog/log-graph :glog/message)
           #:rdfs{:subClassOf #{:rdf/Property}, :domain #{:glog/Entry},
                  :range #{:rdf/Literal},
                  :comment #{"\nA string or mustache template to print to the standard logging stream\nvia taesano.timbre. The flattened description of the entry will be\napplied to its value to resolve template {{parameters}}.\n"}})))

  (testing "Administration"
    (log-reset!)
    (let [result (get-the-answer "Douglas")]    
      (is (= (glog/entry-count) 2))))

  (testing "Log entries"
    (log-reset!)
    (let [result (get-the-answer "Douglas")
          sgta (glog/show :my-log/starting-get-the-answer)
          expressive-log (add glog/ontology 
                              [:my-log/whos-asking :rdf/type :glog/InformsUri])
          ]
      (is (= (dissoc sgta :glog/level)
             {:rdfs/subClassOf #{:glog/Entry}}))
      (is (= (glog/log! :my-log/starting-get-the-answer 
                        :my-log/whos-asking "Douglas" 
                        :glog/message
                        "{{my-log/whos-asking}} is asking for the answer")
             :my-log/starting-get-the-answer_2))
      )
      (let [expressive-log (add glog/ontology 
                                [:my-log/whos-asking :rdf/type :glog/InformsUri])
            ]
        (glog/log-reset! expressive-log)
        (is (= (glog/log! :my-log/starting-get-the-answer
                          :my-log/whos-asking "Douglas")
               :my-log/starting-get-the-answer_0_Douglas)))
    
      )
  (testing "Warning levels"
    (glog/log-reset!)
    (is (= (glog/log! :my-log/demoing-log-level)
           :my-log/demoing-log-level_0
           ))
    (is (= (glog/show :my-log/demoing-log-level)
           {:rdfs/subClassOf #{:glog/Entry}}))
    
    (glog/set-level! :my-log/demoing-log-level :glog/DEBUG)
    (is (= (glog/show :my-log/demoing-log-level)
           {:rdfs/subClassOf #{:glog/Entry}, :glog/level #{:glog/DEBUG}}))
    (glog/log-reset! (add glog/ontology 
                          [:glog/LogGraph :glog/level :glog/DEBUG]))
    (is (= (@glog/log-graph :glog/LogGraph :glog/level)
           #{:glog/DEBUG}))
    (is (= (debug ::demo-log-level)
           :ont-app.graph-log.core-test/demo-log-level_0))

    (glog/set-level! :glog/WARN)

    (is (= (debug ::demo-log-level)
           nil))

    (is (= (glog/entries)
           [:ont-app.graph-log.core-test/demo-log-level_0]))

    (glog/log-reset! (add glog/ontology 
                          [:glog/LogGraph :glog/level :glog/OFF]))

    (fatal :my-log/we-are-f-cked!)
    
    (is (= (glog/entries)
           []))
    (glog/log-reset!)
    )
  
 (testing "Clear entries"
    (glog/set-level! :glog/INFO)
    (info ::IWillBeCleared)
    (glog/log-reset! (glog/clear-entries @log-graph))
    (is (= (glog/entries) [])))
  
  (testing "Searching forward and backward"
    (glog/log-reset!)
    (get-the-answer "Douglas")
    (letfn [(is-starting-get-the-answer? [g entry]
              (g entry :rdf/type :my-log/starting-get-the-answer))
            ]
      (is (= (glog/search-backward 
              is-starting-get-the-answer? 
              :my-log/returning-get-the-answer_1)
             :my-log/starting-get-the-answer_0
             ))))
  (testing "Comparing logs"
    (let [
          A (atom nil)
          B (atom nil)
          ]
      (letfn [(get_the_answer  [whos-asking a]
                (glog/log! :my-log/starting-get-the-answer
                           :my-log/whos-asking whos-asking)
                (println "Hello " whos-asking ", here's the answer...")
                (glog/log-value! :my-log/returning-get-the-answer a))
              ]
        (glog/log-reset!)
        (get_the_answer "Douglas" 42)
        (reset! A (glog/remove-variant-values @glog/log-graph))

        (glog/log-reset!)
        (get_the_answer "Douglas" 43)
        (reset! B (glog/remove-variant-values @glog/log-graph))
        (let [A-and-B (igraph/difference
                       (igraph/intersection @A @B)
                       glog/ontology)
              ]
          (is (= (igraph/normal-form A-and-B)
                 {:my-log/returning-get-the-answer
                  {:rdfs/subClassOf #{:glog/Entry}},
                  :my-log/starting-get-the-answer
                  {:rdfs/subClassOf #{:glog/Entry}},
                  :glog/LogGraph
                  #:glog{:hasEntry
                         #{:my-log/returning-get-the-answer_1
                           :my-log/starting-get-the-answer_0},
                         :entryCount #{2}},
                  :igraph/Vocabulary
                  #:igraph{:compiledAs #{:compiled}},
                  :my-log/returning-get-the-answer_1
                  {:rdf/type #{:my-log/returning-get-the-answer},
                   :glog/executionOrder #{1}},
                  :my-log/starting-get-the-answer_0
                  {:rdf/type #{:my-log/starting-get-the-answer},
                   :my-log/whos-asking #{"Douglas"},
                   :glog/executionOrder #{0}}}))
          (let [A-not-B (igraph/difference @A (igraph/union
                                               glog/ontology A-and-B))
                B-not-A (igraph/difference @B (igraph/union glog/ontology
                                                            A-and-B))
                
                ]
            (is (= (igraph/normal-form A-not-B)
                   #:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}))
            (is (= (igraph/normal-form B-not-A)
                   #:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}))
            (is (= (igraph/normal-form A-not-B)
                   (igraph/normal-form (glog/compare-shared-entries @A @B))))
            (let [[shared [ga gb]] (glog/find-divergence @A @B)
                  ]
              (is (= (igraph/normal-form ga)
                     (igraph/normal-form A-not-B)))
              (is (= (igraph/normal-form gb)
                     (igraph/normal-form B-not-A)))
            )))))))

(defn test-log-value-at-level []
  (value-info ::test-value-info 42)
  (value-info ::test-value-info [::asdf "asdf"] 43))

#_(deftest log-value-at-level
  (testing "log-value-at-level"
    (glog/log-reset!)
    (test-log-value-at-level)
    (let [g (glog/remove-variant-values)
          ]
      (is (= (glog/ith-entry g 0)
             [::test-value-info_0
              {:rdf/type #{::test-value-info},
               :glog/executionOrder #{0},
               :glog/value #{42}}]))
      (is (= (glog/ith-entry g 1)
             [::test-value-info_1
              {:rdf/type #{::test-value-info},
               :glog/executionOrder #{1},
               ::asdf #{"asdf"}
               :glog/value #{43}}])))))
          
#_(deftest standard-logging
  (testing "std-logging-message"
    (is (= (glog/std-logging-message :glog/message "This is a number: {{number}}"
                                     :number 42)
           "This is a number: 42"))))

  
