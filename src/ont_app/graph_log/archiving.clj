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
   ;; 3rd party libraries
   [cljstache.core :as stache]
   ;; ont-app libraries
   [ont-app.igraph.core :as igraph
    :refer [add
            assert-unique
            difference
            reduce-spo
            t-comp
            ]]
   [ont-app.graph-log.core :as glog
    :refer [archiving?
            entries
            log-graph
            timestamp
            ]]
   ))

(def the "alias for igraph/unique" igraph/unique)

;; ASYNC DATA FLOW

(def tap-listeners?
  "Set to `true` when you want to tap> each article when it comes off a listener channel."
  (atom false))

(defmacro def-listener
  "Expands to a go-loop which Binds to a `channel` a `handler`
  Where
  - `channel` is an async port
  - `handler` := [article] -> ?, typically with the side-effect of archiving `article`
  - `article` is an item read from `channel` asynchronously. Typically in a format
    specific to `channel`.
  "
  [channel handler]
  `(go-loop []
      (let [article# (<! ~channel)]
        (when @tap-listeners? (tap> (str "article:" article#)))
        (if article#
            (do
              (~handler article#)
              (recur))
            ;; else no article
              (recur)
              ))))
  
(def >>log-is-resetting>>
  "A channel which will call its handler (set in `def-listener`) on each `reset-state`
  Where
  `handler` := fn [reset-state] -> ? with side-effect of archving `old-graph`
  `reset-state` :=  {:old-graph ..., :new-graph ..., ...}
  `old-graph` is the old log-graph being reset to `new-graph`
  "
  (chan 1))

;; by default, it writes to a canonically named file
(declare archive-to-file)
(def-listener >>log-is-resetting>> archive-to-file)

(def >>log-is-archived>>
  "A channel containing a series of `archive-state`s  reflecting the fact that the old log has been archived and a new one has been declared.
  Typically handlers of this channel will call `set-continuing-from!` on each
  `archive-state` to link the new log-graph back to the archive of the previous graph.
  Where
  - `set-continuing-from!` := fn [archive-state] -> ? with side-effect of setting
    (@glog/log-graph :glog/LogGraph  :glog/continuingFrom) to `volume`
  - `archive-state` := {::volume ..., ...}
  - `volume` is the file holding an edn representation of the previous
    incarnation of @glog/log-graph.
  "
  (chan 1))

;; by default call `set-continuing-from!`
(declare set-continuing-from!)
(def-listener >>log-is-archived>> set-continuing-from!)

(defn wait-for
  "Returns: non-falsey `result` of `test`, or ::timeout after `ms` milliseconds. 
  Where
  - `result` is a truthy response from `test`
  - `the-test` := fn [] -> truthy value
  - `ms` is max time to wait for `test` to be truthy
  "
  ([the-test ms]
   (let [status (atom nil)
         listen (go-loop []
                  (let [test-result (the-test)]
                    (if test-result
                      test-result
                      ;; else
                      (recur))))
         ]
    (let [[result _] (<!! (go (alts! [listen (timeout ms)])))
          ]
      (if result
        (reset! status result)
        (reset! status ::timeout)))
    @status
    )))

(defn default-archive-path
  "Returns rendering of `{{directory}}/{{start}}-{{stop}}.edn`
  Where
  - `directory` is (g :glog/LogGraph :glog/archiveDirectory) or /tmp
  - `start` is the timestamp of the 0th entry
  - `stop` is the timestamp of the nth entry

  VOCABULARY
  - <graph-log> `:glog/archiveDirectory` `directory` (optional; default /tmp)
  - <log entry> `:glog/timestamp` <epoch ms>
  "
  [g]
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

(defn archive-path 
  "Returns a canonical name for an archive file for a log, caluclated per `archive-path-fn` derived from `g`
  Where
  - `g` is a log-graph, Typically `glog:LogGraph`
  - `archive-path-fn` := [g] -> `archive-path`. Or `default-archive-path`

  VOCABULARY:
  - <log-graph> `:glog/archivePathFn` <fn kw>
  - <log-graph> `:glog/timestamp` <timestamp>
  - <log-graph> `:glog/archiveDirectory` <directory URL>
  - <fn kw> `:igraph/compiledAs`  <fn [g] -> archive-path>
  "
  [g]
  (when-let [archive-path-fn (or (the (g :glog/LogGraph (t-comp [:glog/archivePathFn
                                                                 :igraph/compiledAs])))
                                 default-archive-path)
           ]
    (archive-path-fn g)
    ))

(defn save-to-archive!
     "Side-effect: Writes contents of `g` to `archive-path`, after removing stuff that would choke a reader.
Returns `archive-path` for `g`
Where:
- `g` is a graph derived from a log-graph
- `archive-path` is a path to which the contents of `g` are written, generated 
  by (`archive-path-fn` `g`)
- `archive-path-fn` is a compiled function asserted with `:glog/archivePathFn`, 
   or the default function `archive-path`.

  VOCABULARY
  - <log-graph> `:glog/archivePathFn` <fn kw>
  - <fn kw> `:igraph/compiledA`s <fn>
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
  "Side-effects: writes `contents` inferred from `reset-state` to `archive-file` and posts `archive-state` to `>>log-is-archived>>`.
  Where
  - `contents` is `old-graph` minus `new-graph`, and anything that would choke a 
   reader, rendered in EDN.
  - `reset-state` := {::topic  ::log-reset
                  ::`old-graph` ... 
                  ::`new-graph` ...
                  }
  - `archive-state` := {::topic ::archive-state
                       ::volume `archive-file`,
                       ...}, merged with `reset-state`.
  - `old-graph` is the previous contents of a log-graph
  - `new-graph` is the newly reset log-graph
  - `archive-file` is the path to a the `contents` written to disk.
  "
  [reset-state]
  (assert (not (= (::old-graph reset-state)
                  (::new-graph reset-state))))
  (let [old-graph (::old-graph reset-state)
        contents (difference old-graph
                             (::new-graph reset-state))
        result  (save-to-archive! (archive-path old-graph)
                                  contents)
        ]
    (go (>! >>log-is-archived>>
            ;; the catalog card...
            (merge reset-state
                   {::topic ::archive-state
                    ::volume result
                    })))))
   
(defn set-continuing-from!
  "Side-effect: establishes :glog/coninuingFrom in value per `archive-state` in `gatom`
Where
  - `archive-state` := {::volume `url`, ...}
  - `gatom` (optional) an atom containing an IGraph. Default is log-graph
  - `url` is the URL of a location where the previous contents of @log-graph
     have been archived. 
  - Note: may throw error of type ::UnexpectedArchivingResult.

  VOCABULARY:
  - `:glog/LogGraph`
  - <log-graph> `:glog/continuingFrom` `url`
"
  ([archive-state]
   (when (not (@log-graph :glog/LogGraph :glog/continuingFrom))
     (if-let [url (::volume archive-state)]
       (reset! glog/log-graph
               (assert-unique
                @log-graph
                :glog/LogGraph :glog/continuingFrom url))
       ;; else there is no volume in the archive-state
       (throw (ex-info "Unexpected archiving result"
                       {:type ::UnexpectedArchivingResult
                        ::archive-state archive-state}))))))

(def check-archiving-timeout
  "The timeout in ms for the `check-archiving!` function. Default is 1000"
  (atom 1000))

(defn check-archiving!
  "Side-effect: sets the :glog/continuingFrom relation in `gatom`
  Where
  - `gatom` is an atom containing an IGraph, (default `log-graph`),
    it must be configured so as to enable archiving.
  - `ms` is a timeout in milliseconds
  - `archive-state` := {:glog/continuingFrom `url`, ...}
  - `url` is typically the URL of the contents of the previous `gatom`,
    before the most recent call to `log-reset!`.

  VOCABULARY:
  - `:glog/LogGraph` - identifies the log graph itself in @log-graph
  - `:glog/FreshArchive` - names type for LogGraph with 0 iterations
  - `<log-graph> `:glog/iteration` <# of times graph has been reset>
  - `<log-graph> `:glog/continuingFrom` <url of previous archived log iteration>
"
  ([]
   (check-archiving! log-graph @check-archiving-timeout))
  ([ms]
   (check-archiving! log-graph ms))
  ([gatom ms]
   (let [iteration (or (the (@gatom :glog/LogGraph :glog/iteration))
                       0)
         ]
     (when (archiving? @gatom)
       (if (= iteration 0)
         (swap! gatom assert-unique :glog/LogGraph :rdf/type :glog/FreshArchive)
         ;; else continuing...
         (let [result (wait-for (fn []
                                  (@gatom :glog/LogGraph :glog/continuingFrom))
                                ms)]
           (when(= result ::timeout)
             (swap! gatom assert-unique :glog/LogGraph :rdf/type :glog/FreshArchive))))))))

(defn log-reset!
  "Side-effect: resets @log-graph to `new-graph`
  Side-effect: if (initial-graph:glog/ArchivePathFn igraph/compiledAs <path-fn>),
    the previous contents of the graph will be spit'd to <output-path>
  Where
  - <initial-graph> is an IGraph, informed by ont-app.graph-log.core/ontology
  - <path-fn> := fn [g] -> <output-path>
  - <output-path> is a valid path specification , possibly starting with file://

  VOCABULARY
  - `:glog/LogGraph` - identifies the log graph itself in @log-graph
  - <log-graph> `:glog/iteration` <# of times graph has been reset>
  - <log-graph> `:glog/archivePathFn` <fn kw>
  - <fn kw> `:igraph/compiledAs` <fn [g] -> archive path>
  "
  ([]
   (log-reset! glog/ontology))
  ([new-graph]
   (check-archiving!) ;; maybe deref continuing-from async in last cycle.
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
       (go (>! >>log-is-resetting>>
             {::topic ::log-reset
              ::old-graph old-graph
              ::new-graph new-graph
              }))
       (glog/log-reset! new-graph)))))
