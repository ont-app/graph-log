(ns ont-app.graph-log.core
  (:require
   [clojure.string :as str]
   [clojure.set :as set]
   #?(:clj [clojure.java.io :as io])
   ;; 3rd party libraries
   [taoensso.timbre :as timbre]
   ;;[selmer.parser :as selmer]
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

(voc/cljc-put-ns-meta!
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
  "Caches level priorities"
  (atom nil))

(defn level>=
  "Returns true iff `this-level` has priority >= `that-level`
  Where
  <this-level> e.g. :glog/INFO
  <that-level> e.g. :glog/DEBUG
  "
  [this-level that-level]
  {:pre [(keyword? this-level)
         (keyword? that-level)]
   :post [#(boolean? %)]
   }
  (when (not @level-priorities)
    (letfn [(collect-priority [macc bmap]
              (assoc macc
                     (:?level bmap)
                     (:?priority bmap)))
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

;; ARCHIVING TO THE LOCAL FILE SYSTEM IN CLJ

(declare entries)
#?(:clj
   (defn archive-path [g]
  "Returns a canonical name for an archive file for a log
Where
<g> is a log-graph

Vocabulary:
:glog/archiveDirectory -- asserts the name of the directory to which logs
  should be achived.
  Defaults to '/tmp'
"
  (let [es (entries g)
        date-string (fn [i]
                      (str (if (= (count es) 0)
                             (timestamp)
                             (the (g (es i) :glog/timestamp)))))

                      
        ]
    
    (stache/render
     "{{directory}}/{{start}}-{{stop}}.edn"
     {:directory (or (the (g :glog/LogGraph :glog/archiveDirectory))
                     "/tmp")
      :start (date-string 0)
      :stop (date-string (count es))
      })))
   )

#?(:clj
   (defn save-to-archive [g]
     (letfn [(remove-compiled [g s p o]
               ;; Don't wanna choke the reader
               ;; when we slurp 
               (if (= p :igraph/compiledAs)
                 g
                 (add g [s p o])))
             (get-archive-path-fn [g]
               (or (the (g :glog/archivePathFn :igraph/compiledAs))
                   archive-path))
           
             ]
       (let [archive-path-fn (get-archive-path-fn g)
             archive-path (archive-path-fn g)
             g (reduce-spo remove-compiled empty-graph g)
             ]

       (igraph/write-to-file archive-path g)
        ))))


;; NO READER MACROS BEYOND THIS POINT


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
   (let [archive-file (atom nil)]
     (when-let [archive-fn
                (the (@log-graph :glog/ArchiveFn :igraph/compiledAs))]
       (reset! archive-file (archive-fn (difference @log-graph
                                                      initial-graph))))
     ;; Save the previous log to the path provided by SaveToFn
     (timbre/debug "archive file:" @archive-file)
     (reset! log-graph (if @archive-file
                         (add initial-graph
                              [:glog/LogGraph
                               :glog/continuingFrom
                               @archive-file])
                         initial-graph)))))


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

(defn log-message [message entry-id]
  "Side-effect: writes a message to the timbre log stream.
Where
<message> may be a selmer template, in which case the flattened
  description of <entry-id> will be applied.
<entry-id> is a KWI s.t. (@log-graph entry-id :glog/message <message>)
"
  (let [desc (igraph/flatten-description (@log-graph entry-id))
        level-p (igraph/t-comp [(traverse-link :rdf/type)
                                (traverse-link :glog/level)])
        level (or (the (@log-graph entry-id level-p))
                  :debug)
        ]
    (timbre/log (keyword (str/lower-case (name level)))
                (stache/render message desc))))

(defn log! [entry-type & args]
  "Side-effect: adds an entry to log-graph for <id> minted per `entry-type` and `args`
Returns: <id> or nil (if no entry was made)
Where
<id> is a KWI minted for <entry-type> and whatever <arg-kwi>s are of 
  :rdf/type :glog/InformsUri in @log-graph.
<entry-type> is a KWI
<args> := [<arg-kwi> <value>, ...]
"
  
  (when-not (@log-graph :glog/LogGraph) ;; the graph is not initialized
    (timbre/warn "graph-log/log-graph is not initialized. Using default")
    (log-reset!))

  (when (not (@log-graph :glog/Log :glog/level :glog/OFF))
    (letfn [(handle-type-specific-args [acc [k v]]
              ;; returns args w/ type-specific args removed
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
                                      (if (g entry-type :rdfs/subClassOf)
                                        g
                                        (add g
                                             [entry-type
                                              :rdfs/subClassOf :glog/Entry])))
                     maybe-add-level (fn [g]
                                      (if (g entry-type :glog/level)
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
                    (maybe-add-level)
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
            
            ]
      (let [level-priority (t-comp [:glog/level :glog/priority])


            log-priority (or (the (@log-graph :glog/LogGraph level-priority))
                             (the (@log-graph default-log-level :glog/priority))
                             (the (ontology default-log-level :glog/priority)))
            ;; ... the level this log is set to
            args (reduce handle-type-specific-args
                         []
                         (partition 2 args))

            entry-priority (or (the (@log-graph entry-type level-priority))
                               (the (@log-graph
                                     default-log-level
                                     :glog/priority))
                               (the (ontology default-log-level :glog/priority)))
            ]
        (when (and (>= entry-priority log-priority)
                   (not (@log-graph entry-type :glog/level :glog/OFF)))
          (let [id-atom (atom nil)]
            (swap! log-graph (partial add-entry id-atom) args)
            (if-let [messages (@log-graph @id-atom :glog/message)]
              (doseq [message messages]
                (log-message message @id-atom))
              ;; else
              (timbre/debug "Graph-logging " @id-atom))
            
            ;; TODO: support rdf/type :glog/Verbose
            @id-atom
            ))))))



(defmacro apply-log-fn-at-level [default log-fn level entry-type & args]
  `(let [level# (or (the (@log-graph ~entry-type :glog/level))
                    ~level)
         ]
     (print "level:" level#)
     (if (level>= level# (or (the (@log-graph :glog/LogGraph :glog/level))
                             default-log-level))
       (apply ~log-fn (reduce conj [~entry-type] '~args))
       ~default)))


#_(defn old-log-at-level! [level]
  "Returns a logging function with logging level `level`"
  (fn [entry-type & args]
    (apply log! (reduce conj [entry-type :glog/level level] args))))


(defmacro trace! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/TRACE ~entry-type ~@args))

(defmacro debug! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/DEBUG ~entry-type ~@args))

(defmacro info! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/INFO ~entry-type ~@args))

(defmacro warn! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/WARN ~entry-type ~@args))

(defmacro error! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/ERROR ~entry-type ~@args))

(defmacro fatal! [entry-type & args]
  `(apply-log-fn-at-level nil log! :glog/FATAL ~entry-type ~@args))

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


(defmacro value-trace! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/TRACE ~entry-type ~@args))

(defmacro value-debug! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/DEBUG ~entry-type ~@args))

(defmacro value-info! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/INFO ~entry-type ~@args))

(defmacro value-warn! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/WARN ~entry-type ~@args))

(defmacro value-error! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/ERROR ~entry-type ~@args))

(defmacro value-fatal! [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) log-value! :glog/FATAL ~entry-type ~@args))



#_(defn log-value-at-level! [level]
  "Returns a logging function with logging level `level`"
  (fn _log-value-at-level!
    ([entry-type value]
     (log-value! entry-type [:glog/level level] value))
    ([entry-type other-args value]
     (log-value! entry-type
                 (reduce conj other-args [:glog/level level])
                 value))))


;; (def value-debug! (log-value-at-level! :glog/DEBUG))
;; (def value-info!  (log-value-at-level! :glog/INFO))
;; (def value-warn!  (log-value-at-level! :glog/WARN))
;; (def value-error! (log-value-at-level! :glog/ERROR))
;; (def value-fatal! (log-value-at-level! :glog/FATAL))

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
   (let [e ((entries g :all) i)]
     [e (g e)])))
     

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
(defn search [inc-or-dec test g c found q]
  
  "Returns [c found [previous-index]] for `test` of <i>th  entry per `q` and inc-or-dec
  See also the IGraph docs for traversal functions.

  Where
  <c> is the (ignored) traversal context
  <found> is nil or the first previous entry to pass <test>
  <previous-index> decrements the head of <q>, or empty if found or <i> < 0
  <test> := fn [g entry] -> boolean
  <q> := [<entry> or <i> [i] if still searching or [] if found. decrementing per iteration
  <i> is the execution order to test
  <inc-or-dec> :~ #{inc dec}, inc to search forward dec to search backward.
  <entry> is the <i>th entry in <g>
  <g> is a log-graph.
NOTE: typically this is used as a partial application over <test>
(igraph/traverse <log> (partial search-backward <test>)
                                 nil
                                 [<entry-id>])
"
  (let [_entries (or (:entries c)
                     (entries g :all))
        i-or-entry (first q)
        i (if (number? i-or-entry)
            i-or-entry
            (the (g i-or-entry :glog/executionOrder)))
        entry (if (number? i-or-entry)
                (if (< -1 i-or-entry (count _entries))
                  (_entries i-or-entry)
                  :out-of-bounds)
                i-or-entry)
        found? (and (not (= :out-of-bounds entry))
                    (test g entry))
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

;; TODO: add support to search archived logs following continuingFrom links.

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
