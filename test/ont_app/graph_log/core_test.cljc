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
    (is (= (glog/log! :my-log/demoing-log-level :glog/level :glog/WARN)
           ))
    (is (= (glog/show :my-log/demoing-log-level)
           {:glog/level #{:glog/WARN}, 
            :rdfs/subClassOf #{:glog/Entry}}))
    
    (glog/set-level! :my-log/demoing-log-level :glog/DEBUG)
    (is (= (glog/show :my-log/demoing-log-level))
        {:rdfs/subClassOf #{:glog/Entry}, :glog/level #{:glog/DEBUG}})
    (glog/log-reset! (add glog/ontology 
                          [:glog/LogGraph :glog/level :glog/DEBUG]))
    (is (= (@glog/log-graph :glog/LogGraph :glog/level)
           #{:glog/DEBUG}))

    (is (= (glog/debug! ::demo-log-level)
           :ont-app.graph-log.core-test/demo-log-level_0))

    (glog/set-level! :glog/LogGraph :glog/WARN)

    (is (= (glog/debug! ::demo-log-level)
           nil))

    (is (= (glog/entries)
           [:ont-app.graph-log.core-test/demo-log-level_0]))

    (glog/log-reset! (add glog/ontology 
                          [:glog/LogGraph :glog/level :glog/OFF]))

    (glog/fatal! :my-log/we-are-f-cked!)
    
    (is (= (glog/entries)
           []))
    (glog/log-reset!)
    )
  (testing "Archiving"
    (glog/log-reset! (add glog/ontology
                          [[:glog/ArchiveFn 
                            :igraph/compiledAs glog/save-to-archive
                            ]
                           [:glog/LogGraph 
                            :glog/archiveDirectory "/tmp/myAppLog"
                            ]]))
    (glog/info! :my-log/Test-archiving)
    (glog/log-reset!)
    (is (= (-> 
            (clojure.java.io/as-file 
             (igraph/unique
              (@glog/log-graph :glog/LogGraph :glog/continuingFrom)))
            (.exists))
           true))
    (let [restored
          (let [g (make-graph)] 
            (igraph/read-from-file 
             g 
             (igraph/unique 
              (@glog/log-graph :glog/LogGraph :glog/continuingFrom))))
          restored (igraph/subtract restored
                             [:my-log/Test-archiving_0 :glog/timestamp])
          ]
      (is (= (igraph/normal-form restored)
             {:my-log/Test-archiving_0
              {:rdf/type #{:my-log/Test-archiving},
               :glog/executionOrder #{0}},
              :glog/LogGraph
              #:glog{:archiveDirectory #{"/tmp/myAppLog"},
                     :entryCount #{1},
                     :hasEntry #{:my-log/Test-archiving_0}},
              :my-log/Test-archiving
              {:glog/level
               #{:glog/INFO},
               :rdfs/subClassOf #{:glog/Entry}}}))
      (when (.exists (io/file "/tmp/myAppLog"))
        (doseq [f (rest (file-seq (io/file "/tmp/myAppLog")))]
          (io/delete-file f))
        (io/delete-file (io/file "/tmp/myAppLog"))))
    (glog/log-reset!))
  
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
             ))
      ))
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
        (def a @A)
        (def b @B)
        (let [A-and-B (igraph/difference
                       (igraph/intersection @A @B)
                       glog/ontology)
              ]
          (is (= (igraph/normal-form A-and-B)
                 {:my-log/returning-get-the-answer {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}}, :my-log/starting-get-the-answer {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}}, :glog/LogGraph #:glog{:hasEntry #{:my-log/returning-get-the-answer_1 :my-log/starting-get-the-answer_0}, :entryCount #{2}}, :igraph/Vocabulary #:igraph{:compiledAs #{:compiled}}, :my-log/returning-get-the-answer_1 {:rdf/type #{:my-log/returning-get-the-answer}, :glog/value #{}, :glog/executionOrder #{1}}, :my-log/starting-get-the-answer_0 {:rdf/type #{:my-log/starting-get-the-answer}, :my-log/whos-asking #{"Douglas"}, :glog/executionOrder #{0}}}))
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

    

      

