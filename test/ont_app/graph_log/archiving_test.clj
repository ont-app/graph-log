(ns ont-app.graph-log.archiving-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   ;; 3rd party
   [taoensso.timbre :as timbre]
   ;; ont-app 
   [ont-app.igraph.core :as igraph
    :refer [add
            difference
            query
            ]]
   [ont-app.igraph.graph :as native-normal
    :refer [make-graph]]
   [ont-app.graph-log.core :as glog
    :refer [
            log!
            log-graph
            ]]
   [ont-app.graph-log.levels :refer :all]
   [ont-app.graph-log.archiving :as archive]
   ))

(def fresh-graph
  (add glog/ontology
       [[:glog/LogGraph :glog/archiveDirectory "/tmp/myAppLog"]]))

(def expected-restored-graph
  {:ont-app.graph-log.archiving-test/testing
   #:rdfs{:subClassOf #{:glog/Entry}},
   :glog/LogGraph
   {:glog/entryCount #{1},
    :glog/hasEntry #{:ont-app.graph-log.archiving-test/testing_0},
    :rdf/type #{:glog/FreshArchive}},
   :ont-app.graph-log.archiving-test/testing_0
   {:rdf/type #{:ont-app.graph-log.archiving-test/testing},
    :glog/executionOrder #{0},
    :test #{1}}}
  )

#_(def expected-restored-graph nil)
(deftest archiving
  (glog/log-reset! fresh-graph)
  ;; establishes the archive directory
  (glog/log! ::testing :test 1)
  (time (archive/log-reset! fresh-graph))
  ;; this will archive to spec'd directory
  ;; first reset will time out in check-archiving
  (time (archive/wait-for (fn []
                            (@glog/log-graph :glog/LogGraph :glog/continuingFrom))
                          100))
  (is (some? (igraph/unique (@glog/log-graph
                             :glog/LogGraph :glog/continuingFrom))))
  (when (some? (igraph/unique (@glog/log-graph
                               :glog/LogGraph :glog/continuingFrom)))
    (is (-> (clojure.java.io/as-file
             (igraph/unique (@glog/log-graph
                             :glog/LogGraph :glog/continuingFrom)))
            (.exists)))
    (when (-> 
           (clojure.java.io/as-file 
            (igraph/unique
             (@glog/log-graph :glog/LogGraph :glog/continuingFrom)))
           (.exists))
      (let [restored
            (-> (make-graph)
                (igraph/read-from-file (igraph/unique 
                                        (@glog/log-graph
                                         :glog/LogGraph :glog/continuingFrom))))
            ]
        (is (= expected-restored-graph
               (igraph/normal-form (glog/remove-variant-values restored))
               )))))

  ;; clean up myAppLog directory.....
  (when (.exists (io/file "/tmp/myAppLog"))
    (doseq [f (rest (file-seq (io/file "/tmp/myAppLog")))]
      (io/delete-file f))
    (io/delete-file (io/file "/tmp/myAppLog"))))

(deftest increment-iterations
  (glog/log-reset! fresh-graph)
  ;; establishes the archive directory
  (glog/log! ::testing :test 1)
  (time (archive/log-reset! fresh-graph))
  (glog/log! ::testing :test 2)
  (time (archive/log-reset! fresh-graph))
  ;; this will archive to spec'd directory
  ;; first reset will time out in check-archiving
  (time (archive/wait-for (fn []
                            (@glog/log-graph :glog/LogGraph :glog/continuingFrom))
                          100))
  (is (= 2
         (igraph/unique (@glog/log-graph :glog/LogGraph :glog/iteration))))
  ;; clean up myAppLog directory.....
  (when (.exists (io/file "/tmp/myAppLog"))
    (doseq [f (rest (file-seq (io/file "/tmp/myAppLog")))]
      (io/delete-file f))
    (io/delete-file (io/file "/tmp/myAppLog"))))

