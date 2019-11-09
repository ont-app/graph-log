(ns ont-app.graph-log.core
  (:require
   [clojure.string :as str]
   [clojure.set :as set]
   ;; 3rd party libraries
   [taoensso.timbre :as timbre]
   ;; ont-app libraries
   [ont-app.igraph.core :as igraph
    :refer [add
            difference
            query
            reduce-spo
            subjects
            subtract
            ]]
   [ont-app.graph-log.ont :as ont]
   [ont-app.vocabulary.core :as voc]
   [ont-app.igraph.graph :as g
    :refer [make-graph
            ]]))

(voc/cljc-put-ns-meta!
 'ont-app.prototypes.core
 {
  :voc/mapsTo 'ont-app.prototypes.ont
  }
 )
(def ontology ont/ontology)

;; FUN WITH READER MACROS

#?(:cljs
   (enable-console-print!)
   ;;(defonce app-state (atom {:text "Hello world!"}))
   ;;(println "This text is printed from src/graph-log/core.cljs. Go ahead and edit it and see reloading in action.")
   ;;(defn on-js-reload []
     ;; optionally touch your app-state to force rerendering depending on
     ;; your application
     ;; (swap! app-state update-in [:__figwheel_counter] inc)
   )

;; NO READER MACROS BEYOND THIS POINT



(def the igraph/unique)

(def empty-graph (make-graph))

(def log-graph (atom empty-graph))

(defn log-reset!
  ([]
   (log-reset! ontology))
  ([initial-graph]
   (reset! log-graph initial-graph)))

;; ontology
;; :glog/timestamp - currentTimeMillis at time the entry is created
;; :glog/executionOrder - the order in which the entry was entered
;; :glog/InformsKwi - event-types that should be added to generated entry names.
;; :rlog/date
;; :rlog/Entry
;; :rlog/

;; TODO: support rlog:OFF, and other log levels like INFO, etc.

(defn log [event-type & args]
  "Side-effect: adds an entry to log-graph for <id> minted per `event-type` and `args`
Returns: <id>
Where
<id> is a KWI minted for <event-type> and whatever <arg-kwi>s are of 
  :rdf/type :glog/InformsUri in @log-graph.
<event-type> is a KWI
<args> := [<arg-kwi> <value>, ...]
"
  (let [collect-if-informs-uri
        (fn [acc [k v]]
          (if (@log-graph k :rdf/type :glog/InformsUri)
            (conj acc (if (keyword? v)
                        (name v)
                        v))
            acc))
        mint-kwi (fn [head & args]
                   (keyword (namespace head)
                            (str/join "_"
                                      (reduce conj
                                              [(name head)]
                                              args))))
        id (apply mint-kwi (reduce collect-if-informs-uri
                                   [event-type
                                    (count (subjects @log-graph))
                                    ]
                                   (partition 2 args)))
        ]
    (timbre/debug "Logging " id)
    (swap! log-graph
           add
           (reduce conj
                   [id :rdf/type event-type
                    :glog/timestamp (System/currentTimeMillis)
                    :glog/executionOrder (count (subjects @log-graph))
                    ]
                   args))
    ;; TODO: support rdf/type :glog/Verbose
    id
    ))

(defn log-value 
  "Returns `value`
  Side effect: logs <id> :glog/value `value`, plus `other-args` into log-graph"
  ([event-type value]
   (log-value event-type [] value)
   )
  ([event-type other-args value]
   (apply log (reduce conj
                      [event-type]
                      (reduce conj
                              other-args
                              [:glog/value value])))
   value))


(defn entry-order
  "Returns [<entry-id>, ...] for `entry-type` in `g` ordered by :glog/executionOrder
  Where
  <entry-id> is a KWI identifying a log event in <g>
  <g> is an optional IGraph supporting the logging ontology, (default log-graph)
    This is typically either the current log-graph or a copy of the log-grpah
    from a previous session.
  <entry-type> is :all or the ID of some entry type.
  "
  ([]
   (entry-order @log-graph :all))
  
  ([entry-type]
   (entry-order @log-graph entry-type))
  
  ([g entry-type]
   (let [
         entries (query g (if (= entry-type :all)
                            [[:?target :glog/executionOrder :?order]
                             ]
                            ;; else
                            [[:?target :rdf/type entry-type]
                             [:?target :glog/executionOrder :?order]
                             ]))
        ]
     (vec (map :?target 
               (sort-by :?order entries))))))


(defn kth-entry
  "Returns [<entry-id> <description>] for kth execution order  in `g`
    (default @graph-log)
  Where
  <entry-id> is keyword naming the entry
  <description> is the normal-form description of <entry-id> in <g>
  <g> is a log-graph (the current one by default)
  "
  ([k]
   (kth-entry @log-graph k))
  ([g k]
   (let [event (:?s (the (query g [[:?s :glog/executionOrder k]])))
         ]
     [event (g event)])))

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
  (let [i-or-entry (first q)
        order-to-entry ;; {<i> <entry>}
        (or
         (:order-to-entry c)
         (reduce (fn [m bmap]
                   (assoc m (:?order bmap) (:?entry bmap)))
                 {}
                 (query g
                        [[:?entry :glog/executionOrder :?order]])))
        i (if (number? i-or-entry)
            i-or-entry
            (the (g i-or-entry :glog/executionOrder)))
        entry (if (number? i-or-entry)
                (order-to-entry i)
                i-or-entry)
        found? (test entry)
        ]
    [(if (contains? c :order-to-entry)
       c
       (assoc c :order-to-entry order-to-entry))
      ,
      (if found? entry)
      ,
      (if (or found? (<= i 0))
        []
        (conj (rest q) (dec i)))
      ]))


(defn remove-timestamps 
  "Spurious difference between two logs"
  ([]
   (remove-timestamps @log-graph))
  ([g]
   (letfn [(remove-timestamp [g' bmap]
             (let [{s :?s  ts :?timestamp} bmap
                   ]
               (subtract g' [s :glog/timestamp ts])))
           ]
     (reduce remove-timestamp
             g
             (query g [[:?s :glog/timestamp :?timestamp]])))))


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
         eo-1 (entry-order log1 :all) 
         eo-2 (entry-order log2 :all)
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
