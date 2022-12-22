(ns ont-app.graph-log.levels
  {:doc "Macros to condition logging on the operative log level"
   }
  (:require
   [taoensso.timbre :as timbre]
   [ont-app.igraph.core :as igraph]
   [ont-app.graph-log.core :as glog]
   ))


(defmacro apply-std-logging-fn-at-level
  "Expands to an expression that checks `level` logging configuration, and prints a message to standard logging as appropriate. `:glog/message` statements will be posted to standard logging regardless of whether the is a @`log-graph` or not.
  Where
  - `val` is the value to be logged (or nil if we're not tracing the value of a form
  - `level` is a `:glog/Level`
  - `args` are glog logging args appropriate to `glog/std-logging-message`
  "
  [val level & args]
  (let [args (if val
               (reduce conj args [:glog/value val])
               args)
        ]
    `(let []
       (if (and (:min-level timbre/*config*)
                (glog/level>= ~level (:min-level timbre/*config*))
                (some (fn [x#] (= x# :glog/message)) (list ~@args)))
         ~(list (symbol "taoensso.timbre" (name level))
                `(apply glog/std-logging-message (list ~@args))))
       ;; else logging level does not match
       ~val)))

(defmacro apply-log-fn-at-level
  "Returns value of  `log-fn` on `entry-type` and `args` as appropriate for `level`, else return `default`
  - Where
    - `log-fn` is log! or value-log!, to be called only if appropriate for level.
    - `entry-type` is a keyword naming a type of entry
    - `args` := [<p> <o>, ...] for log!, [[<p> <o>, ...] <value>] for log-value!
      These will only be evaluated if <level> is appropriate
    - `level` is the level of logging for the log statement, this may be overridden
      by the level asserted for <entry-type>
    - `default` should be the value of  <log-fn> if it's log-value!, typically nil
      for log!

  VOCABULARY:
  - `:glog/LogGraph` - names @log-graph in @log-graph
  - `:glog/OFF` - names the logging level that switches logging off
  - <log graph or entry> `:glog/level` <logging level>
  "
  [default log-fn level entry-type & args]
  `(let [entry-level# (or (igraph/unique
                           (@glog/log-graph ~entry-type :glog/level))
                          ~level)
         global-level# (or (igraph/unique
                            (@glog/log-graph :glog/LogGraph :glog/level))
                           @glog/default-log-level)
         ]
     (if (and (not= entry-level# :glog/OFF)
              (not= global-level# :glog/OFF)
              (glog/level>= entry-level# global-level#))
       (apply ~log-fn (reduce conj [~entry-type] (list ~@args)))
       ~default)))

;; log! per debug level

(defmacro trace
  "Marks `entry-type` as logging-level :glog/TRACE
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :trace ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/TRACE ~entry-type ~@args)))

(defmacro debug
  "Marks `entry-type` as logging-level :glog/DEBUG
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :debug ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/DEBUG ~entry-type ~@args)))

(defmacro info
  "Marks `entry-type` as logging-level :glog/INFO
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :info ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/INFO ~entry-type ~@args)))

(defmacro warn
  "Marks `entry-type` as logging-level :glog/WARN
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :warn ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/WARN ~entry-type ~@args)))

(defmacro error
  "Marks `entry-type` as logging-level :glog/ERROR
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :error ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/ERROR ~entry-type ~@args)))

(defmacro fatal
  "Marks `entry-type` as logging-level :glog/FATAL
  "
  [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :fatal ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/FATAL ~entry-type ~@args)))

;; log-value! per log level

(defmacro value-trace
  "Logs value of a form with logging level :glog/TRACE
  "
  ([entry-type val]
   `(value-trace ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :trace ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/TRACE ~entry-type ~extras val#))))

(defmacro value-info
  "Logs value of a form with logging level :glog/INFO
  "
  ([entry-type val]
   `(value-info ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :info ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/INFO ~entry-type ~extras val#))))


(defmacro value-debug
  "Logs value of a form with logging level :glog/DEBUG
  "
  ([entry-type val]
   `(value-debug  ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :debug ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/DEBUG ~entry-type ~extras val#))))


(defmacro value-warn
  "Logs value of a form with logging level :glog/WARN
  "
  ([entry-type val]
   `(value-warn ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :warn ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/WARN ~entry-type ~extras val#))))

(defmacro value-error
  "Logs value of a form with logging level :glog/ERROR
  "
  ([entry-type val]
   `(value-error ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :error ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/ERROR ~entry-type ~extras val#))))

(defmacro value-fatal
  "Logs value of a form with logging level :glog/FATAL
  "
  ([entry-type val]
   `(value-fatal ~entry-type [] ~val))
  
  ([entry-type extras val]
   `(let [val# ~val]
      (apply-std-logging-fn-at-level val# :fatal ~@extras)
      (apply-log-fn-at-level
       val#  glog/log-value! :glog/FATAL ~entry-type ~extras val#))))

