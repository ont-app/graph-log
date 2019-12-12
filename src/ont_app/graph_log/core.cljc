(ns ont-app.graph-log.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.set :as set]
   ;; 3rd party libraries
   [taoensso.timbre :as timbre]
   ;; ont-app libraries
   [ont-app.igraph.core :as igraph
    :refer [add
            assert-unique
            difference
            query
            reduce-spo
            subjects
            subtract
            traversal-comp
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

(voc/cljc-put-ns-meta!
 'ont-app.graph-log.core
 {
  :voc/mapsTo 'ont-app.graph-log.ont
  }
 )

(def ontology ont/ontology)


;; ontology
;; :glog/timestamp - currentTimeMillis at time the entry is created
;; :glog/executionOrder - the order in which the entry was entered
;; :glog/InformsUri - properties that should be added to generated entry names.
;; :rlog/date
;; :rlog/Entry
;; :rlog/

;; TODO: support rlog:OFF, and other log levels like INFO, etc.

;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   )

;; NO READER MACROS BEYOND THIS POINT

(def the igraph/unique)

(def empty-graph (make-graph))

(def log-graph (atom empty-graph))

(def default-log-level :glog/INFO)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR LOG MAINTENANCE
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn log-reset!
  "Side-effect: resets @log-graph to `initial-graph`
  Side-effect: if (initial-graph:glog/SaveToFn igraph/compiledAs <path-fn>),
    the previous contents of the graph will be spit'd to <output-path>
  Where
  <initial-graph> is an IGraph, informed by ont-app.graph-log.core/ontology
  <fn> := fn [g] -> <output-path>
  <output-path> is a valid path specification , possibly starting with file://
  "
  ([]
   (log-reset! ontology))
  ([initial-graph]
   (when-let [save-to-fn
              (the (@log-graph :glog/SaveToFn :igraph/compiledAs))]
     ;; Save the previous log to the path provided by SaveToFn
     (let [remove-compiled (fn [g s p o]
                             ;; Don't wanna choke the reader
                             ;; if we slurp 
                             (if (= p :igraph/compiledAs)
                               g
                               (add g [s p o])))
           output-path (str/replace (save-to-fn @log-graph)
                                    #"^file://" "")
           ]
       (io/make-parents output-path)
       (spit output-path
             (with-out-str
               (pprint
                (igraph/normal-form (reduce-spo
                                     remove-compiled
                                     empty-graph
                                     (difference @log-graph ontology))))))))

   (reset! log-graph initial-graph)))

(defn set-level! [element level]
  "Side-effect, adds `args` to entry for `element` in log-graph
Where
<args> := [<predicate> <object>, ...]
<element> is an element of the log-graph
"
  (swap! log-graph assert-unique element :glog/level level))

(defn annotate! [element & args]
  "Side-effect, adds `args` to entry for `element` in log-graph
Where
<args> := [<predicate> <object>, ...]
<element> is an element of the log-graph
"
  (when-not (subjects @log-graph)
    (throw (ex-info "Annotating an empty log-graph"
                    {:element element
                     :args args
                     })))
  (swap! log-graph add (reduce conj [element] args)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR ENTERING TO LOG
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn entry-count
  "Returns the number of entries in `g` (default @log-graph)"
  ([] (entry-count @log-graph))
  ([g] (or (the (g :glog/LogGraph :glog/entryCount))
           0)))

(defn log! [entry-type & args]
  "Side-effect: adds an entry to log-graph for <id> minted per `entry-type` and `args`
Returns: <id>
Where
<id> is a KWI minted for <entry-type> and whatever <arg-kwi>s are of 
  :rdf/type :glog/InformsUri in @log-graph.
<entry-type> is a KWI
<args> := [<arg-kwi> <value>, ...]
"
  
  (when-not (subjects @log-graph) ;; the graph is not initialized
    (timbre/warn "graph-log/log-graph is not initialized. Using default")
    (log-reset!))

  (when (not (@log-graph :glog/Log :glog/level :glog/OFF))
    (letfn [(handle-type-specific-args [acc [k v]]
              ;; returns args without type-specific args
              ;; side-effect: assigns log level to entry type
              (if (#{:glog/level} k)
                (let []
                  (when-not (the (@log-graph entry-type :glog/level))
                    ;; ... if not already specified...
                    (swap! log-graph add [entry-type :glog/level v]))
                  acc)
                ;;else it's an entry-specific attribute
                (reduce conj acc [k v])))
                  
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
                                      (if (g entry-type)
                                        g
                                        (add g [entry-type
                                                :glog/level
                                                (or (the (g
                                                          :glog/LogGraph
                                                          :glog/level))
                                                    default-log-level)
                                                ])))
                     ]
                                        
                                        
                (reset! id-atom
                        (apply mint-kwi (reduce collect-if-informs-uri
                                                [entry-type
                                                 execution-order
                                                 ]
                                                (partition 2 args))))
                (-> g
                    (maybe-add-type)
                    (assert-unique :glog/LogGraph
                                   :glog/entryCount
                                   (inc (entry-count)))
                    (add (reduce conj
                                 [@id-atom :rdf/type entry-type
                                   :glog/timestamp (System/currentTimeMillis)
                                   :glog/executionOrder execution-order
                                   ]
                                 args))
                    (add [:GraphLog :glog/hasEntry @id-atom]))))
            
            ]
      (let [level-priority (traversal-comp [(traverse-link :glog/level)
                                            (traverse-link :glog/priority)])

            log-priority (or (the (@log-graph :glog/LogGraph level-priority))
                             (the (@log-graph default-log-level :glog/priority)))
            ;; ... the level this log is set to
            args (reduce handle-type-specific-args
                         []
                         (partition 2 args))

            entry-priority (or (the (@log-graph entry-type level-priority))
                               (the (@log-graph
                                     default-log-level
                                     :glog/priority)))
            ]
        (when (>= entry-priority log-priority)
          (let [id-atom (atom nil)]
            (swap! log-graph (partial add-entry id-atom) args)
            (timbre/debug "Graph-logging " @id-atom)
            ;; TODO: support rdf/type :glog/Verbose
            @id-atom
            ))))))

(defn log-at-level! [level]
  "Returns a logging function with logging level `level`"
  (fn [entry-type & args]
    (apply log! (reduce conj [entry-type :glog/level level] args))))

(def debug! (log-at-level! :glog/DEBUG))
(def info!  (log-at-level! :glog/INFO))
(def warn!  (log-at-level! :glog/WARN))
(def error! (log-at-level! :glog/ERROR))
(def fatal! (log-at-level! :glog/FATAL))


(defn log-value!
  "Returns `value`
  Side effect: logs <id> :glog/value `value`, plus `other-args` into log-graph
  Where
  <"
  ([entry-type value]
   (log-value! entry-type [] value)
   )
  ([entry-type other-args value]
   (apply log! (reduce conj
                      [entry-type]
                      (reduce conj
                              other-args
                              [:glog/value value])))
   value))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SUPPORT FOR VIEWING LOG CONTENTS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
         matches-type (fn [entry] (g entry :rdf/type entry-type))
         _entries (sort-by execution-order (g :glog/LogGraph :glog/hasEntry))
         ]
     (into []
           (if (= entry-type :all)
             _entries
             ;; else
             (filter matches-type _entries))))))


(defn ith-entry
  "Returns [<entry-id> <description>] for ith execution order  in `g`
    (default @graph-log)
  Where
  <entry-id> is keyword naming the entry
  <description> is the normal-form description of <entry-id> in <g>
  <g> is a log-graph (the current one by default)
  "
  ([i]
   (ith-entry @log-graph i))
  ([g i]
   ((entries g :all) i)))

(defn show
  "Returns contents of `entry-id` for optional `g` 
  Where
  <entry-id> is the KWI of an entry
  <g> is a log-graph (default @log-graph)
  "
  ([entry-id]
   (show @log-graph entry-id))
  ([g entry-id]
     (g entry-id)))

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

^:traversal-fn
(defn search-backward [test g c found q]
  
  "Returns [c found [previous-index]] for `test` of <i>th descending entry per `q`
  See also the IGraph docs for traversal functions.

  Where
  <c> is the (ignored) traversal context
  <found> is nil or the first previous entry to pass <test>
  <previous-index> decrements the head of <q>, or empty if found or <i> < 0
  <test> := fn [entry] -> boolean
  <q> := [<entry> or <i> [i] if still searching or [] if found. decrementing per iteration
  <i> is the execution order to test
  <entry> is the <i>th entry in <g>
  <g> is a log-graph.
NOTE: typically this is used as a partial application over <test>
(igraph/traverse <log> (partial search-backward <test>)
                                 nil
                                 [<entry-id>])
"
  (let [entries (or (:entries c)
                    (entries g))
        i-or-entry (first q)
        i (if (number? i-or-entry)
            i-or-entry
            (the (g i-or-entry :glog/executionOrder)))
        entry (if (number? i-or-entry)
                (entries i-or-entry)
                i-or-entry)
        found? (test entry)
        ]
    [(assoc c :entries entries)
     ,
     (if found? entry)
     ,
     (if (or found? (<= i 0))
       []
       (conj (rest q) (dec i)))
     ]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UTILITIES SUPPORTING COMPARISON OF TWO LOGS
;; This should allow you to save a log, make a
;; change in your code and compare the results
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-timestamps 
  "Spurious difference between two logs"
  ([]
   (remove-timestamps @log-graph))
  ([g]
   (letfn [(remove-timestamp [g' entry-id]
             (let [ts (the (g entry-id :glog/timestamp))
                   ]
               (subtract g' [s :glog/timestamp ts])))
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

(defn compare-shared-entries [g1 g2]
  "Returns an IGraph containing content shared between `g1` and `g2`
Where
<g1>, <g2> are @log-graph's from two different sessions.
"
  (let [shared-keys (set/intersection (set (igraph/subjects g1))
                                      (set (igraph/subjects g2)))
        g1' (add empty-graph (select-keys (g1) shared-keys))
        g2' (add empty-graph  (select-keys (g2) shared-keys))
        ]
    (igraph/difference
     (remove-variant-values g1')
     (remove-variant-values g2'))))

(defn find-divergence [log1 log2]
  "Returns [<same> [<e1> <e2>] for `log1` and `log2`
Where
<same> := [<shared-event>, ...]
<e1>, <e2> name events whose details differ between <log1> and <log2>
<log1> <log2> are @log-graph's from two different sessions, 
  typically reflecting some minor change in the same code base.
"
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
    (clojure.pprint/pprint same)
    (println "In G1:")
    (clojure.pprint/pprint (igraph/normal-form d1))
    (println "In G2:")
    (clojure.pprint/pprint (igraph/normal-form d2))
    [d1 d2]))
