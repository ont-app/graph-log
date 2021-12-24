(ns ont-app.graph-log.ont
  (:require
   [ont-app.igraph.core :as igraph]
   [ont-app.igraph.graph :as graph]
   [ont-app.igraph-vocabulary.core :as igv]
   [ont-app.vocabulary.core :as voc]
   )
  )

(voc/put-ns-meta!
 'ont-app.graph-log.rlog
 {
  :vann/preferredNamespacePrefix "rlog"
  :vann/preferredNamespaceUri
  "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog#"
  :dc/description "RLOG - an RDF Logging Ontology"
  :foaf/homepage
  "https://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog/rlog.html"
  :dcat/downloadURL
  "https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl"
  :voc/appendix
  [["https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl"
    :dcat/mediaType "text/turtle"]]
  }
 )

;; TODO import the RLOG source

(voc/put-ns-meta!
 'ont-app.graph-log.ont
 {
  :vann/preferredNamespacePrefix "glog"
  :vann/preferredNamespaceUri "http://rdf.naturallexicon.org/graph-log/ont#"
  :rdfs/comment "
Contains logging costructs addressed in the graph-log code not found in the 
rlog vocabulary."
  })

(def alignment-to-rlog
  "Many constructs in glog were appropriated from rlog. Using the sameAs
  relation to keep the namepaces simple.
  Breaking this out into its own graph to keep the log models small."
  (igraph/add (graph/make-graph)
       [
        [:glog/ALL :owl/sameAs :rlog/ALL]
        [:glog/DEBUG :owl/sameAs :rlog/DEBUG]
        [:glog/ERROR :owl/sameAs :rlog/ERROR]
        [:glog/Entry :owl/sameAs :rlog/Entry]
        [:glog/FATAL :owl/sameAs :rlog/FATAL]
        [:glog/INFO :owl/sameAs :rlog/INFO]
        [:glog/Level :owl/sameAs :rlog/Level]
        [:glog/OFF :owl/sameAs :rlog/OFF]
        [:glog/TRACE :owl/sameAs :rlog/TRACE]
        [:glog/WARN :owl/sameAs :rlog/WARN]
        [:glog/level :owl/sameAs :rlog/level]
        ]))

(def ontology
  "A native-normal graph containing descriptions of constructs informing the graph-log model."
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
       :rdfs/comment "The grandparent class of all log entries"
       ]
      [:glog/level
       :rdfs/domain :glog/Entry
       :rdfs/range :glog/Level
       :rdfs/comment "Asserts the Level of an Entry type"
       ]
      [:glog/Level
       :rdf/type :rdfs/Class
       :rdfs/comment "Refers to a logging level like :glog/WARN"
       ]
      [:glog/ALL
       :rdf/type :glog/Level
       :glog/priority 0
       :rdfs/comment "Signals that the log should record all log statements"
       ]
      [:glog/TRACE
       :rdf/type :glog/Level
       :glog/priority 1
       :rdfs/comment "Finer grained informational events than DEBUG"
       ]
      [:glog/DEBUG
       :rdf/type :glog/Level
       :glog/priority 2
       :rdfs/comment "A standard logging level"
       ]
      [:glog/INFO
       :rdf/type :glog/Level
       :glog/priority 3
       :rdfs/comment "A standard logging level"
       ]
      [:glog/WARN
       :rdf/type :glog/Level
       :glog/priority 4
       :rdfs/comment "A standard logging level"
       ]
      [:glog/ERROR
       :rdf/type :glog/Level
       :glog/priority 5
       :rdfs/comment "A standard logging level"
       ]
      [:glog/FATAL
       :rdf/type :glog/Level
       :glog/priority 6
       :rdfs/comment "A standard logging level"
       ]
      [:glog/OFF
       :rdf/type :glog/Level
       :glog/priority 7
       :rdfs/comment "Signals that the log should not record events."
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
Current time in milliseconds at time the Entry was created."
       :owl/seeAlso :rlog/date ;; which uses Date string.
       ]
      [:glog/InformsUri
       :rdfs/subClassOf :rdf/Property
       :rdfs/comment "
Refers to a property KWI whose name should inform the minting of each new 
Entry's URI, in addition to its class and execution order, the better to 
understand at a glance what the log entry is about."
       ]
      [:glog/message
       :rdfs/subClassOf :rdf/Property
       :rdfs/domain :glog/Entry
       :rdfs/range :rdf/Literal
       :rdfs/comment "
A string or mustache template to print to the standard logging stream
via taesano.timbre. The flattened description of the entry will be
applied to its value to resolve template {{parameters}}.
"
       ]
      ;; ARCHIVING THE LOG GRAPH ON RESET
      [:glog/FreshArchive
       :rdf/type :rdfs/Class
       :rdfs/comment "
An archived log which is the first of its lineage. Implied glog/iteration is 0. Not continuingFrom anything.
"
       ]
      [:glog/iteration
       :rdf/type :rdf/Property
       :rdfs/domain :glog/Entry
       :rdfs/range :xsd/nonNegativeInteger
       :rdfs/comment "
Optional. Asserts the number of times this log has been reset. When non-nil this will inform the KWI of each entry."
       ]
      [:glog/archivePathFn
       :rdfs/domain :igraph/Graph
       :rdfs/comment "Asserts a function [g] -> archive-path to which
the current state of the log may be written before resetting."
       ]
      [:glog/archiveDirectory
       :rdfs/domain :igraph/Graph
       :rdfs/comment "Asserts the directory portion of the archive-path used 
by archivePathFn. (only applicable if the local file system is used)"
       ]
      [:glog/continuingFrom
       :rdfs/domain :igraph/Graph
       :rdfs/comment "Asserts the archive-path of the log previously archived 
on the last reset."
       ]
      ])))
