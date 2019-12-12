(ns ont-app.graph-log.ont
  (:require
   [ont-app.igraph.core :as igraph]
   [ont-app.igraph.graph :as graph]
   [ont-app.igraph-vocabulary.core :as igv]
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

(def ontology
  (let [g (graph/make-graph)]
    (voc/clear-caches!)
    (igraph/add
     g
     [[:glog/Vocabulary
       :igraph/imports :igraph/Vocabulary
       ]
      [:igraph/Vocabulary
       :igraph/compiledAs igv/ontology
       ]
      [:glog/SaveToFn
       :rdf/type :igraph/Function
       :rdfs/comment "
Refers to an optionally provided function [log]-> pathname.
When provided with :igraph/compiledAs, the log-graph will be written
to <pathname> automatically in the log-reset! function before resetting
the contents of the graph.
"
       ]
      [:glog/LogGraph
       :rdf/type :igraph/Graph
       :rdfs/comment "The URI of igraph.graph-log.core/log-graph"
       ]
      [:glog/entryCount
       :rdfs/domain :graph/Graph
       :rdfs/range :xsd/NonNegativeInteger
       :rdfs/comment "Asserts the number of log entries in this graph."
       ]
      [:glog/Entry
       :owl/sameAs :rlog/Entry
       :rdfs/comment "Refers to an entry in a log"
       ]
      [:glog/level
       :rdfs/domain :glog/Entry
       :rdfs/range :glog/Level
       :rdfs/comment "Asserts the Level of an Entry type"
       :owl/sameAs :rlog/level
       ]
      [:glog/Level
       :owl/sameAs :rlog/Level
       ]
      [:glog/ALL
       :rdf/type :glog/Level
       :glog/priority 0
       :rdfs/comment "Signals that the log should record all log statements"
       :owl/sameAs :rlog/ALL
       ]
      [:glog/TRACE
       :rdf/type :glog/Level
       :glog/priority 1
       :rdfs/comment "Finer grained informational events than DEBUG"
       :owl/sameAs :rlog/TRACE
       ]
      [:glog/DEBUG
       :rdf/type :glog/Level
       :glog/priority 2
       :owl/sameAs :rlog/DEBUG
       ]
      [:glog/INFO
       :rdf/type :glog/Level
       :glog/priority 3
       :owl/sameAs :rlog/INFO
       ]
      [:glog/WARN
       :rdf/type :glog/Level
       :glog/priority 4
       :owl/sameAs :rlog/WARN
       ]
      [:glog/ERROR
       :rdf/type :glog/Level
       :glog/priority 5
       :owl/sameAs :rlog/ERROR
       ]
      [:glog/FATAL
       :rdf/type :glog/Level
       :glog/priority 6
       :owl/sameAs :rlog/FATAL
       ]
      [:glog/OFF
       :rdf/type :glog/Level
       :glog/priority 7
       :rdfs/comment "Signals that the log should not record events."
       :owl/sameAs :rlog/OFF
       ]
      
      [:glog/executionOrder
       :rdf/type :rdf/Property
       :rdfs/domain :glog/Entry
       :rdfs/range :xsd/nonNegativeInteger
       :rdfs/comment "
Asserts the order of execution for some Entry within a log."
       ]
      [:glog/timestamp
       :rdf/type :rdf/Property
       :rdfs/domain :glog/Entry
       :rdfs/range :xsd/nonNegativeInteger
       :rdfs/comment "
Current time in milliseconds at time Entry was created."
       :owl/seeAlso :rlog/date ;; which uses Date string.
       ]
      [:glog/InformsUri
       :rdfs/subClassOf :rdf/Property
       :rdfs/comment "
Refers to a kwi whose name should inform the minting of each new 
Entry's URI, in addition to its class and execution order, the better to 
understand at a glance what the log entry is about."
       ]
      ])))
    
