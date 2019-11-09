(ns ont-app.graph-log.ont
  (:require
   [ont-app.igraph.core :as igraph]
   [ont-app.igraph.graph :as graph]
   [ont-app.vocabulary.core :as voc]
   )
  )

(voc/cljc-put-ns-meta!
 'ont-app.graph-log.rlog
 {
  :vann/preferredNamespacePrefix "rlog"
  :vann/preferredNamespaceUri
  "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog#"
  :dc/description "RLOG - an RDF Logging Ontology"
  :foaf/homepage
  "https://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog/rlog.html#d4e91"
  :dcat/downloadURL
  "https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl"
  :voc/appendix
  [["https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl"
    :dcat/mediaType "text/turtle"]]
  }
 )

;; TODO import the RLOG source

(voc/cljc-put-ns-meta!
 'ont-app.graph-log.ont
 {
  :vann/preferredNamespacePrefix "glog"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/graph-log/ont#"
  :rdfs/comment "
Contains logging costructs addressed in the graph-log code not found in the 
rlog vocabulary."
  })

(defonce ontology
  (let [g (graph/make-graph)]
    (voc/clear-caches!)
    (igraph/add
     g
     [[:glog/executionOrder
       :rdf/type :rdf/Property
       :rdfs/domain :rlog/Entry
       :rdfs/range :xsd/nonNegativeInteger
       :rdfs/comment "
Asserts the order of execution for some Entry within a log."
       ]
      [:glog/timestamp
       :rdf/type :rdf/Property
       :rdfs/domain :rlog/Entry
       :rdfs/range :xsd/nonNegativeInteger
       :rdfs/comment "
Current time in milliseconds at time Entry was created."
       :owl/seeAlso :rlog/date ;; which uses Date string.
       ]
      [:glog/InformsUri
       :rdfs/subClassOf :rdf/Property
       :rdfs/comment "
Refers to a property whose object should inform the minting of each new 
Entry's URI, in addition to its class and execution order, the better to 
understand at a glance what the log entry is about."
       ]
      ])))
    
