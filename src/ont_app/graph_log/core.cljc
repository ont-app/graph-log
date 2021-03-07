(ns ont-app.graph-log.core
  (:require
   [clojure.string :as str]
   [clojure.set :as set]
   #?(:clj [clojure.java.io :as io])
   #?(:clj [clojure.pprint :as pp]
      :cljs [cljs.pprint :as pp])

   ;; 3rd party libraries
   [taoensso.timbre :as timbre]
   [cljstache.core :as stache]
   ;; ont-app libraries
   [ont-app.igraph.core :as igraph
    :refer [add
            assert-unique
            difference
            query
            reduce-spo
            subjects
            subtract
            t-comp
            traverse
            traverse-link
            ]]
   [ont-app.graph-log.ont :as ont]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph-vocabulary.core :as igv
    :refer [mint-kwi
            ]]
   [ont-app.igraph.graph :as g
    :refer [make-graph
            ]]))

(voc/put-ns-meta!
 'ont-app.graph-log.core
 {
  :voc/mapsTo 'ont-app.graph-log.ont
  }
 )

(def ontology ont/ontology)

(def the igraph/unique)

(def empty-graph (make-graph))

(def log-graph (atom empty-graph))


(def default-log-level :glog/INFO)

(def level-priorities
  "Caches level priorities, to inform `level>=`"
  (atom nil))

^{:vocabulary [:glog/priority
               ]}
(defn level>=
  "Returns true iff `this-level` has priority >= `that-level`
  Where
  <this-level> e.g. :glog/INFO
  <that-level> e.g. :glog/DEBUG
  "
  [this-level that-level]
  {:pre [(keyword? this-level)
         (keyword? that-level)]
   :post [(boolean? %)]
   }
  (when (not @level-priorities)
    ;; populate the level-priorities cache
    (letfn [(collect-priority [macc bmap]
              (assoc macc
                     (:?level bmap)
                     (:?priority bmap)
                     ;; strip out the namespaces and downcase to
                     ;; match timbre levels:
                     (keyword (str/lower-case (name (:?level bmap))))
                     (:?priority bmap)
                     ))
            ]
      (reset! level-priorities
              (reduce collect-priority
                      {}
                      (query ontology
                             [[:?level :glog/priority :?priority]])))))
  (>= (this-level @level-priorities)
      (that-level @level-priorities)))


;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   )

(defn timestamp []
  #?(:clj
     (System/currentTimeMillis)
     )
  #?(:cljs
     (system-time)))

^{:vocabulary [:glog/LogGraph
               :glog/archiveDirectory
               :glog/archivePathFn
               :igraph/compiledAs
               ]}
(defn archiving? 
  "True iff archiving is enabled for `g`
  Where:
  - `g` is an IGraph being used as a log-graph (default [[log-graph]])
  "
  ([]
   (archiving? @log-graph))
  ([g]
   ;; TODO: generalize this as other options come into play.
   (or (g :glog/LogGraph :glog/archiveDirectory)
       (g :glog/LogGraph (t-comp [:glog/archivePathFn :igraph/compiledAs])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR LOG MAINTENANCE
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log-reset!
  "Side-effect: resets @log-graph to `initial-graph`
  Where
  - <initial-graph> is an IGraph, informed by ont-app.graph-log.core/ontology
    - optional. Default is core/ontology.
  "
  ([]
   (log-reset! ontology))
  ([initial-graph]
   (reset! log-graph initial-graph)))

^{:vocabulary [:glog/level
               ]}
(defn set-level! 
  "Side-effect, adds `args` to entry for `element` in log-graph
Where
<args> := [<predicate> <object>, ...]
<element> is an element of the log-graph
"
  [element level]
  (swap! log-graph assert-unique element :glog/level level))

(defn annotate! 
  "Side-effect, adds `args` to entry for `element` in log-graph
Where
<args> := [<predicate> <object>, ...]
<element> is an element of the log-graph
"
  [element & args]
  (when-not (subjects @log-graph)
    (throw (ex-info "Annotating an empty log-graph"
                    {:element element
                     :args args
                     })))
  (swap! log-graph add (reduce conj [element] args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR ENTERING TO LOG
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

^{:vocabulary [:glog/LogGraph
               :glog/entryCount
               ]}
(defn entry-count
  "Returns the number of entries in `g` (default @log-graph)"
  ([] (entry-count @log-graph))
  ([g] (or (the (g :glog/LogGraph :glog/entryCount))
           0)))

^{:vocabulary [:glog/message
               ]}
(defn std-logging-message 
  "Returns: a string suitable for standard logging based on `args`, or nil
  if there is no :glog/message specification.
Where
<args> := {<p> <o>, ....}
<p> is a keyword naming a property, possibly :glog/message
<o> is a value, known as <template> if <p> = :glog/message
<template> is a mustache-style template to which <desc-map> will be applied
  Of the form `yadda {{<p>}} yadda...'
<desc-map> := {<p> <o>, ...}, minus any :glog/message. Specifying <o>'s to be 
  inserted into <template>.
"
  [& args]
  {:pre [(even? (count args))]
   }
  (let [collect-msgs (fn [[msgs desc] [k v]]
                       [(if (= k :glog/message)
                          (conj msgs v)
                          msgs)
                        ,
                        (if-not (= k :glog/message)
                          (assoc desc k v)
                          desc)
                        ])
        [messages desc] (reduce collect-msgs [[]{}] (partition 2 args))
         ]
     (if-not (empty? messages)
       (str/join "\n" (map (fn [msg] (stache/render msg desc))
                           messages)))))


^{:vocabulary [:rdf/type
               :rdfs/subClassOf
               :glog/InformsUri
               :glog/Entry
               :glog/LogGraph
               :glog/entryCount
               :glog/timestamp
               :glog/executionOrder
               :glog/hasEntry
               ]}
(defn log! 
  "Side-effect: adds an entry to log-graph for <id> minted per `entry-type` and `args`
  Returns: <id> or nil (if no entry was made)
  Where
  <id> is a KWI minted for <entry-type> and whatever <arg-kwi>s are of 
  :rdf/type :glog/InformsUri in @log-graph.
  <entry-type> is a KWI
  <args> := [<arg-kwi> <value>, ...]
  Note: Any issues relating to log levels should be handled before calling this
  function.
  "
  [entry-type & args]
  (when (not (even? (count args)))
    (throw (ex-info "Invalid arguments to log entry. Should be an even number"
                    {:type ::InvalidArgumentsToLogEntry
                     ::args args
                     ::arg-count (count args)
                     })))
  (when (@log-graph :glog/LogGraph) ;; the graph is initialized
    (letfn [
            (collect-if-informs-uri
              [acc [k v]]
              ;; maybe mint a more expressive kwi
              (if (@log-graph k :rdf/type :glog/InformsUri)
                (conj acc (if (keyword? v)
                            (name v)
                            v))
                acc))
            
            (add-entry [id-atom g args]
              ;; side-effect: sets `id-atom` to a newly minted kwi
              ;; returns `g`' with new entry for @id-atom, per args,
              ;; incrementing the entry-count of the graph
              ;; This is called in a swap!
              (let  [execution-order (entry-count)
                     maybe-add-type (fn [g]
                                      (if (g entry-type :rdfs/subClassOf)
                                        g
                                        (add g
                                             [entry-type
                                              :rdfs/subClassOf :glog/Entry])))
                     ]
                
                (reset! id-atom
                        (apply mint-kwi
                               (reduce collect-if-informs-uri
                                       (vec (filter some?
                                                    [entry-type
                                                     ;; still making up my mind
                                                     #_(the (g :glog/LogGraph
                                                             :glog/iteration))
                                                     execution-order]))
                                       (partition 2 args))))
                (-> g
                    (maybe-add-type)
                    (assert-unique :glog/LogGraph
                                   :glog/entryCount
                                   (inc (entry-count)))
                    (add (reduce conj
                                 [@id-atom :rdf/type entry-type
                                  :glog/timestamp (timestamp) 
                                  :glog/executionOrder execution-order
                                  ]
                                 args))
                    (add [:glog/LogGraph :glog/hasEntry @id-atom]))))
            
            ] ;; letfn
      (let [id-atom (atom nil)]
        (swap! log-graph (partial add-entry id-atom) args)
        @id-atom
        ))))

^{:vocabulary [:glog/value
               ]}
(defn log-value!
  "Returns `value`
  Side effect: logs <id> :glog/value `value`, plus `other-args` into log-graph
  Where
  <entry-type> is a keyword naming the type of <entry>
  <value> is the value being logged before it's returned.
  <other-args> := [<p> <o>, ...]
  <entry> is an entry in @glog/log-graph
  <p> is a keyword naming a property of <entry>
  <o> is a value asserted for <p> s.t. [<entry> <p> <o>] in the log.
  "
  ([entry-type value]
   (log-value! entry-type [] value)
   )
  ([entry-type other-args value]
   {:pre [(vector? other-args)]
    }
   (apply log! (reduce conj
                      [entry-type]
                      (reduce conj
                              other-args
                              [:glog/value value])))
   value))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR VIEWING LOG CONTENTS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

^{:vocabulary [:glog/executionOrder
               :rdf/type
               :glog/LogGraph
               :glog/hasEntry
               ]}
(defn entries
  "Returns [<entry-id>, ...] for `entry-type` in `g` ordered by :glog/executionOrder
  Where
  <entry-id> is a KWI identifying a log event in <g>
  <g> is an optional IGraph supporting the logging ontology, (default log-graph)
    This is typically either the current log-graph or a copy of the log-grpah
    from a previous session.
  <entry-type> is :all or the ID of some entry type.
  "
  ([]
   (entries @log-graph :all))
  
  ([entry-type]
   (entries @log-graph entry-type))
  
  ([g entry-type]
   (let [execution-order (fn [entry] (the (g entry :glog/executionOrder)))
         matches-type? (fn [entry] (g entry :rdf/type entry-type))
         _entries (sort-by execution-order (g :glog/LogGraph :glog/hasEntry))
         ]
     (into []
           (if (= entry-type :all)
             _entries
             ;; else
             (filter matches-type? _entries))))))


(defn ith-entry
  "Returns [<entry-id> <description>] for ith execution order  in `g`
    (default @graph-log)
  Where
  <entry-id> is keyword naming the entry
  <description> is the normal-form description of <entry-id> in <g>
  <g> is a log-graph (@log-graph by default)
  "
  ([i]
   (ith-entry @log-graph i))
  ([g i]
   (let [e ((entries g :all) i)]
     [e (g e)])))
     

(defn show
  "Returns contents of `entry-id` for optional `g` 
  Where
  <entry-id> is the KWI of an entry
  <g> is a log-graph (default @log-graph)
  "
  ([entry-id]
   (igraph/get-p-o @log-graph entry-id))
  ([entry-id p]
   (igraph/get-o @log-graph entry-id p))
  ([entry-id p o]
   (igraph/ask @log-graph entry-id p o)))


(defn query-log
  "Returns [<bmap>, ....] for `q` posed to optional `g`
  Where
  <bmap> := {<var> <value>, ...} bindings to <q> posed to <g>
  <q> is a query in a format amenable to <g>
  <g> is an IGraph supporting the graph-log vocabulary (default @log-graph)
  "
  ([q]
   (query-log @log-graph q))
  ([g q]
   (query g q)))

;; traversal function
^{vocabulary [:glog/executionOrder
              ]}
(defn search 
  "Returns [c found [previous-index]] for `entry-test` of <i>th  entry per `q` and inc-or-dec
  See also the IGraph docs for traversal functions.

  Where
  <c> is the (ignored) traversal context
  <found> is nil or the first previous entry to pass <test>
  <previous-index> decrements the head of <q>, or empty if found or <i> < 0
  <entry-test> := fn [g entry] -> boolean
  <q> := [<entry> or <i> [i] if still searching or [] if found. inc/dec-ing
    per iteration
  <i> is the execution order to test
  <inc-or-dec> :~ #{inc dec}, inc to search forward dec to search backward.
  <entry> is the <i>th entry in <g>
  <g> is a log-graph.
NOTE: typically this is used as a partial application over <test>
(igraph/traverse <log> (partial search-backward <test>)
                                 nil
                                 [<entry-id>])
"
  [inc-or-dec entry-test g c found q]
  {:pre [(fn? entry-test)
         ]
   }
  (let [_entries (or (:entries c)
                     (entries g :all))
        i-or-entry (first q)
        i (if (number? i-or-entry)
            i-or-entry
            (or (the (g i-or-entry :glog/executionOrder))
                (throw (ex-info (str i-or-entry
                                     " does not have an execution order")
                                {:type ::NoExecutionOrderInSearch
                                 ::i-or-entry i-or-entry
                                 ::g g}))))
        entry (if (number? i-or-entry)
                (if (< -1 i-or-entry (count _entries))
                  (_entries i-or-entry)
                  :out-of-bounds)
                i-or-entry)
        found? (and (not (= :out-of-bounds entry))
                    (entry-test g entry))
        ]
    [(assoc c :entries _entries)
     ,
     (if found? entry)
     ,
     (if (or found?
             (= entry :out-of-bounds)
             (if (= inc-or-dec dec)
               (<= i 0)
               (>= i (dec (count _entries)))))
       []
       (conj (rest q) (inc-or-dec i)))
     ]))

(defn search-backward 
  "Searches the log backward for a match to `test`, starting at `start`
  Where
  <test> := fn [g entry] -> boolean
  <start> is an integer indexing the (entries g) (default end of entries)
  <g> is the log-graph
  "
  ([test]
   (search-backward @log-graph test (dec (count (entries)))))
  
  ([test start]
   (search-backward @log-graph test start))
  
  ([g test start]
   (traverse g (partial search dec test) nil [start])))

(defn search-forward 
  "Searches the log forward for a match to `test` starting at `start`
  Where
  <test> := fn [g entry] -> boolean
  <start> is an integer indexing (entries g) (default 0)
  <g> is the log-graph
  "
  ([test]
   (search-forward @log-graph test 0))

  ([test start]
   (search-forward @log-graph test start))

  ([g test start]
   (traverse g (partial search inc test) nil [start])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UTILITIES SUPPORTING COMPARISON OF TWO LOGS
;; This should allow you to save a log, make a
;; change in your code and compare the results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

^{:vocabulary [:glog/timestamp
               :glog/LogGraph
               :glog/hasEntry
               ]}
(defn remove-timestamps 
  "Spurious difference between two logs"
  ([]
   (remove-timestamps @log-graph))
  ([g]
   (letfn [(remove-timestamp [g' entry-id]
             (let [ts (the (g entry-id :glog/timestamp))
                   ]
               (subtract g' [entry-id :glog/timestamp ts])))
           ]
     (reduce remove-timestamp
             g
             (g :glog/LogGraph :glog/hasEntry)))))


(defn remove-variant-values
  "Returns `g`, removing/replacing values which would naturally vary between sessions.
  These would give rise to spurious differences between two otherwise identical
    logs. Timestamps are removed, and compiled objects are replaced with
    :compiled
  Where
  <g> is an optional log-graph from the current or past sessions. Default
    is current @log-graph
  "
  ([]
   (remove-variant-values @log-graph))
  ([g]
   (letfn [(compiled? [x]
             (or (= (type x) (type empty-graph))
                 (fn? x)
                 ))
           
           (do-map [m k v]
             (assoc m k (cond
                          (compiled? v) :compiled
                          (coll? v) (remove-variants v)
                          :default v)))

           (empty-for [coll] (cond (vector? coll) []
                                   (set? coll) #{}
                                   (seq? coll) '()))
       
           (remove-variants [x]
             (cond
               (map? x) (reduce-kv do-map {} x)
               (coll? x) (into (empty-for x)
                               (map remove-variants x))
               (compiled? x) :compiled
               :default x))
                     
           (remove-spo-variants [g s p o]
             (cond
               (compiled? o) (add g [s p :compiled])
               (coll? o) (add g [s p (remove-variants o)])
               (= p :glog/timestamp) g
               :default (add g [s p o])
               ))
           ]
     (reduce-spo remove-spo-variants empty-graph g))))

(defn compare-shared-entries 
  "Returns an IGraph containing content shared between `g1` and `g2`
Where
<g1>, <g2> are @log-graph's from two different sessions.
"
  [g1 g2]
  (let [shared-keys (set/intersection (set (igraph/subjects g1))
                                      (set (igraph/subjects g2)))
        g1' (add empty-graph (select-keys (g1) shared-keys))
        g2' (add empty-graph (select-keys (g2) shared-keys))
        ]
    (igraph/difference
     (remove-variant-values g1')
     (remove-variant-values g2'))))

(defn find-divergence 
  "Returns [<same> [<e1> <e2>] for `log1` and `log2`
Where
<same> := [<shared-event>, ...]
<e1>, <e2> name events whose details differ between <log1> and <log2>
<log1> <log2> are @log-graph's from two different sessions, 
  typically reflecting some minor change in the same code base.
"
  [log1 log2]
  (letfn [(sub-graph [g e]
            (remove-variant-values
             (add empty-graph (select-keys (g) #{e}))))
          
          (diff-record [e1 e2]
            (cond
              (and e1 e2 (= e1 e2))
              [(igraph/difference (sub-graph log1 e1)
                                              (sub-graph log2 e1))
               (igraph/difference (sub-graph log2 e1)
                                  (sub-graph log1 e1))]
              (and e1 e2)
              [(sub-graph log1 e1) (sub-graph log2 e2)]
              e1
              [(sub-graph log1 e1) nil]
              e2
              [nil (sub-graph log2 e2)]
              :default nil))]
              
  (loop [same []
         ;; [<entry-id>, ...] ...
         eo-1 (entries log1 :all) 
         eo-2 (entries log2 :all)
         ]
      
    (if-let [d (diff-record (first eo-1) (first eo-2))]
      (if (and (empty? (igraph/subjects (d 0)))
               (empty? (igraph/subjects (d 1))))
        (recur (conj same (first eo-1)) (rest eo-1) (rest eo-2))
        ;; else there's a difference
        [same d]
        )
      ;; else all the entries were identical
      [same nil]))))

(defn report-divergence [g1 g2]
  (let [[same [d1 d2]] (find-divergence g1 g2)
        ]
    (println "Shared:")
    (pp/pprint same)
    (println "In G1:")
    (pp/pprint (igraph/normal-form d1))
    (println "In G2:")
    (pp/pprint (igraph/normal-form d2))
    [d1 d2]))

;;(defn -main [& _] )


