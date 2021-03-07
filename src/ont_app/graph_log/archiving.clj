(ns ont-app.graph-log.archiving
  "Adds logic to archive the contents of log graphs on reset."
  (:require
   [clojure.core.async :as async
    :refer [<!
            <!!
            >!
            alts!
            chan
            go
            go-loop
            timeout
            ]]
   [clojure.java.io :as io]
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
            t-comp
            ]]
   [ont-app.graph-log.core :as glog
    :refer [archiving?
            entries
            log-graph
            timestamp
            ]]
   [ont-app.graph-log.ont :as ont]
   [ont-app.graph-log.levels :refer :all]
   ))

(def the igraph/unique)

;; ASYNC DATA FLOW

(defmacro def-listener
  [channel handler]
  `(go-loop []
      (let [article# (<! ~channel)]
        (timbre/trace "article:" article#)
        (if article#
            (do
              (~handler article#)
              (recur))
            ;; else no article
            (let [] (Thread/sleep 10) (recur))))))
  
(def log-reset>>archive-to-file
  "A channel which will call `archive-to-file` on each `article`
  Where
  `archive-to-file` := fn [article] -> ? with side-effect of archving `old-graph`
  `article` :=  {:old-graph ..., :new-graph ..., ...}
  `old-graph` is the old log-graph being reset to `new-graph`
  "
  (chan 1))
(declare archive-to-file)
(def-listener log-reset>>archive-to-file archive-to-file)


(def file-archived>>set-continuing-from
  "A channel which will call `set-continuing-from` on each `article`
  Where
  - `set-continuing-from` := fn [article] -> ? with side-effect of setting
    (@glog/log-graph :glog/LogGraph  :glog/continuingFrom) to `volume`
  - `article` := {::volume ..., ...}
  - `volume` is the file holding an edn representation of the previous
    incarnation of @glog/log-graph.
  "
  (chan 1))
(declare set-continuing-from)
(def-listener file-archived>>set-continuing-from set-continuing-from)

(defn wait-for
  "Returns: non-falsey `result` of `test`, or ::timeout after `ms` milliseconds. 
  Where
  - `result` is a truthy response from `test`
  - `test` := fn [] -> truthy value
  - `ms` is max time to wait for `test` to be truthy
  "
  ([_test ms]
   (let [status (atom nil)
         listen (go-loop []
                  (let [test-result (_test)]
                    (if test-result
                      test-result
                      (do
                        (recur)))))
         ]
    (let [[result _] (<!! (go (alts! [listen (timeout ms)])))
          ]
      (if result
        (reset! status result)
        (reset! status ::timeout)))
    @status
    )))

^{:vocabulary [:glog/LogGraph
               :glog/archivePathFn
               :igraph/compiledAs
               :glog/timestamp
               :glog/archiveDirectory]}
(defn archive-path 
  "Returns a canonical name for an archive file for a log
  Where
  - <g> is a log-graph, Typically `glog:LogGraph`
  "
  [g]
  (if-let [archive-path-fn (the (g :glog/LogGraph (t-comp [:glog/archivePathFn
                                                           :igraph/compiledAs])))
           ]
    (archive-path-fn g)
    ;; else no custom function...
    (let [es (entries g)
          date-string (fn [i]
                        (str (if (= (count es) 0)
                               (glog/timestamp)
                               (the (g (es i) :glog/timestamp)))))
          ]
      (stache/render
       "{{directory}}/{{start}}-{{stop}}.edn"
       {:directory (or (the (g :glog/LogGraph :glog/archiveDirectory))
                       "/tmp")
        :start (date-string 0)
        :stop (date-string (count es))
        }))))

^{:vocabulary [:igraph/compiledAs
               ]}
(defn save-to-archive 
     "Side-effect: Writes contents of `g` to `archive-path`, after removing stuff that would choke a reader.
Returns `archive-path` for `g`
Where:
- `g` is a graph derived from a log-graph
- `archive-path` is a path to which the contents of `g` are written, generated 
  by (`archive-path-fn` `g`)
- `archive-path-fn` is a compiled function asserted with `:glog/archivePathFn`, 
   or the default function `glog/archive-path`.
"
  ([archive-path g]
   (letfn [(remove-compiled [g s p o]
             ;; Don't wanna choke the reader
             ;; when we slurp it back in...
             (if (= p :igraph/compiledAs)
               g
               (add g [s p o])))
           ]
     (let [g (reduce-spo remove-compiled glog/empty-graph g)
           ]
       (igraph/write-to-file archive-path g)))))

(defn archive-to-file 
     "Side-effects: writes `contents` from `article` to `archive-file` and posts `catalog-card` to `file-archived>>set-continuing-from`.
  Where
  - `contents` is `old-graph` minus `new-graph`, and anything that would choke a 
   reader, rendered in EDN.
  - `article` := {::topic  ::LogReset
                  ::`old-graph` ... 
                  ::`new-graph` ...
                  }
  - `catalog-card` := {::topic ::catalog
                       ::volume `archive-file`,
                       ...}, merged with `article`.
  - `old-graph` is the previous contents of a log-graph
  - `new-graph` is the newly reset log-graph
  - `archive-file` is the path to a the `contents` written to disk.
"
  [article]
  (let []
    (assert (not (= (::old-graph article)
                    (::new-graph article))))
    (let [old-graph (::old-graph article)
          contents (difference old-graph
                               (::new-graph article))
          result (try (save-to-archive (archive-path old-graph)
                                       contents)
                      (catch Throwable e
                        e))]
      (go (>! file-archived>>set-continuing-from
              ;; the catalog card...
              (merge article
                     {::topic ::catalog
                      ::volume result
                      }))))))
   
^{:vocabulary [:glog/LogGraph,
               :glog/continuingFrom]}
(defn set-continuing-from 
  "Side-effect: establishes :glog/coninuingFrom value per `catalog-card` in `gatom`
Where
  - `catalog-card` := {::volume `url`, ...}
  - `gatom` (optional) an atom containing an IGraph. Default is log-graph
  - `url` is the URL of a location where the previous contents of @gatom
     have been archived. 
  - Note: may throw error of type ::UnexpectedArchivingResult.
"
  ([catalog-card]
   (when (not (@log-graph :glog/LogGraph :glog/continuingFrom))
     (if-let [url (::volume catalog-card)]
       (reset! glog/log-graph
               (assert-unique
                @log-graph
                :glog/LogGraph :glog/continuingFrom url))
       ;; else there is no volume in the card...
       (throw (ex-info "Unexpected archiving result"
                       {:type ::UnexpectedArchivingResult
                        ::card catalog-card}))))))

(def check-archiving-timeout (atom 1000))

^{:vocabulary [:glog/LogGraph
               :glog/iteration
               :glog/FreshArchive
               :glog/continuingFrom
               :rdf/type]}
(defn check-archiving 
  "Side-effect: sets the :glog/continuingFrom relation in `gatom`
  Where
  - `gatom` is an atom containing an IGraph, (default `log-graph`),
    it must be configured so as to enable archiving.
  - `ms` is a timeout in milliseconds
  - `article` := {:glog/continuingFrom `url`, ...}
  - `url` is typically the URL of the contents of the previous `gatom`,
    before the most recent call to `log-reset!`.
"
  ([]
   (check-archiving log-graph @check-archiving-timeout))
  ([ms]
   (check-archiving log-graph ms))
  ([gatom ms]
   (let [iteration (or (the (@gatom :glog/LogGraph :glog/iteration))
                       0)
         ]
     (when (archiving? @gatom)
       (if (= iteration 0)
         (swap! gatom assert-unique :glog/LogGraph :rdf/type :glog/FreshArchive)
         ;; else continuing...
         (let [result (wait-for (fn []
                                  (Thread/sleep 10)
                                  (@gatom :glog/LogGraph :glog/continuingFrom))
                                ms)]
           (when(= result ::timeout)
             (swap! gatom assert-unique :glog/LogGraph :rdf/type :glog/FreshArchive))))))))


^{:vocabulary [:glog/LogGraph
               :glog/iteration]}
(defn log-reset!
  "Side-effect: resets @log-graph to `initial-graph`
  Side-effect: if (initial-graph:glog/SaveToFn igraph/compiledAs <path-fn>),
    the previous contents of the graph will be spit'd to <output-path>
  Where
  - <initial-graph> is an IGraph, informed by ont-app.graph-log.core/ontology
  - <fn> := fn [g] -> <output-path>
  - <output-path> is a valid path specification , possibly starting with file://
  "
  ([]
   (log-reset! glog/ontology))
  ([new-graph]
   (check-archiving) ;; maybe deref continuing-from async in last cycle.
   ;; submit the transition state to the archiver channel...
   (when (not (= @log-graph new-graph))
     (let [old-graph @log-graph
           new-graph (assert-unique new-graph
                                    :glog/LogGraph
                                    :glog/iteration
                                    (inc (or (the (old-graph
                                                   :glog/LogGraph
                                                   :glog/iteration))
                                             0)))
           ]
       (go (>! log-reset>>archive-to-file
             {::topic ::LogReset
              ::old-graph old-graph
              ::new-graph new-graph
              }))
       (glog/log-reset! new-graph)))))

