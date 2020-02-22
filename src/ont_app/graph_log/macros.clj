(ns ont-app.graph-log.macros
  (:require
   [ont-app.igraph.core :as igraph]
   [ont-app.graph-log.core :as glog]
   ))


(defmacro apply-log-fn-at-level
  "Returns value of  `log-fn` on `entry-type` and `args` as appropriate for `level`, else return `default`
  Where
  <log-fn> is log! or value-log!, to be called only if appropriate for level.
  <entry-type> is a keyword naming a type of entry
  <args> := [<p> <o>, ...] for log!, [[<p> <o>, ...] <value>] for log-value!
    These will only be evaluated if <level> is appropriate
  <level> is the level of logging for the log statement, this may be overridden
    by the level asserted for <entry-type>
  <default> should be the value of  <log-fn> if it's log-value!, typically nil
    for log!
  "
  [default log-fn level entry-type & args]
  `(let [entry-level# (or (igraph/unique
                           (@glog/log-graph ~entry-type :glog/level))
                          ~level)
         global-level# (or (igraph/unique
                            (@glog/log-graph :glog/LogGraph :glog/level))
                           glog/default-log-level)
         ]
     (if (and (not= entry-level# :glog/OFF)
              (not= global-level# :glog/OFF)
              (glog/level>= entry-level# global-level#))
       (apply ~log-fn (reduce conj [~entry-type] '~args))
       ~default)))

;; log! per debug level

(defmacro trace [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/TRACE ~entry-type ~@args))

(defmacro debug [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/DEBUG ~entry-type ~@args))

(defmacro info [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/INFO ~entry-type ~@args))

(defmacro warn [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/WARN ~entry-type ~@args))

(defmacro error [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/ERROR ~entry-type ~@args))

(defmacro fatal [entry-type & args]
  `(apply-log-fn-at-level nil glog/log! :glog/FATAL ~entry-type ~@args))


;; log-value! per log level
(defmacro value-trace [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/TRACE ~entry-type ~@args))

(defmacro value-debug [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/DEBUG ~entry-type ~@args))

(defmacro value-info [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/INFO ~entry-type ~@args))

(defmacro value-warn [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/WARN ~entry-type ~@args))

(defmacro value-error [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/ERROR ~entry-type ~@args))

(defmacro value-fatal [entry-type & args]
  `(apply-log-fn-at-level
    ~(last args) glog/log-value! :glog/FATAL ~entry-type ~@args))

