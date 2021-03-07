(ns ont-app.graph-log.levels
  {:doc "Macros to condition logging on the operative log level"
   }
  (:require
   [taoensso.timbre :as timbre]
   [ont-app.igraph.core :as igraph]
   [ont-app.graph-log.core :as glog]
   ))


^{:vocabulary [:glog/message
               ]}
(defmacro apply-std-logging-fn-at-level
  [val level & args]
  `(let []
     (if (and (glog/level>= ~level (:level timbre/*config*))
              (some (fn [x#] (= x# :glog/message)) (list ~@args)))
       ~(list (symbol "taoensso.timbre" (name level))
              `(apply glog/std-logging-message (list ~@args))))
     ~val))

^{:vocabulary [:glog/LogGraph
               :glog/level
               :glog/OFF
               ]}
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
       (apply ~log-fn (reduce conj [~entry-type] (list ~@args)))
       ~default)))

;; log! per debug level

^{:vocabulary [:glog/TRACE
               ]}
(defmacro trace [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :trace ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/TRACE ~entry-type ~@args)))

^{:vocabulary [:glog/DEBUG
               ]}
(defmacro debug [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :debug ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/DEBUG ~entry-type ~@args)))

^{:vocabulary [:glog/INFO
               ]}
(defmacro info [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :info ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/INFO ~entry-type ~@args)))

^{:vocabulary [:glog/TRACE
               ]}
(defmacro warn [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :warn ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/WARN ~entry-type ~@args)))

^{:vocabulary [:glog/ERROR
               ]}
(defmacro error [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :error ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/ERROR ~entry-type ~@args)))

^{:vocabulary [:glog/FATAL
               ]}
(defmacro fatal [entry-type & args]
  `(let []
     (apply-std-logging-fn-at-level nil :fatal ~@args)
     (apply-log-fn-at-level nil glog/log! :glog/FATAL ~entry-type ~@args)))


;; log-value! per log level

^{:vocabulary [:glog/value
               :glog/TRACE
               ]}
(defmacro value-trace
  ([entry-type val]
   `(value-trace ~entry-type [] ~val))
  
  ([entry-type extras val]
  (let [args (reduce conj extras  [:glog/value val])]
    `(let []
       (apply-std-logging-fn-at-level nil :trace ~@args)
       (apply-log-fn-at-level
        ~val  glog/log-value! :glog/TRACE ~entry-type ~extras ~val)))))

^{:vocabulary [:glog/value
               :glog/INFO
               ]}
(defmacro value-info
  ([entry-type val]
   `(value-info ~entry-type [] ~val))
  
  ([entry-type extras val]
   (let [args (reduce conj extras  [:glog/value val])
         ]
     `(let []
        (apply-std-logging-fn-at-level nil :info ~@args)
        (apply-log-fn-at-level
         ~val  glog/log-value! :glog/INFO ~entry-type ~extras ~val)))))

^{:vocabulary [:glog/value
               :glog/DEBUG
               ]}
(defmacro value-debug
  ([entry-type val]
   `(value-debug  ~entry-type [] ~val))
  
  ([entry-type extras val]
   (let [args (reduce conj extras  [:glog/value val])]
     `(let []
        (apply-std-logging-fn-at-level nil :debug ~@args)
        (apply-log-fn-at-level
         ~val  glog/log-value! :glog/DEBUG ~entry-type ~extras ~val)))))

^{:vocabulary [:glog/value
               :glog/TRACE
               ]}
(defmacro value-warn
  ([entry-type val]
   `(value-warn ~entry-type [] ~val))
  
  ([entry-type extras val]
   (let [args (reduce conj extras  [:glog/value val])]
    `(let []
       (apply-std-logging-fn-at-level nil :warn ~@args)
       (apply-log-fn-at-level
        ~val  glog/log-value! :glog/WARN ~entry-type ~extras ~val)))))

^{:vocabulary [:glog/value
               :glog/ERROR
               ]}
(defmacro value-error
  ([entry-type val]
   `(value-error ~entry-type [] ~val))
  
  ([entry-type extras val]
   (let [args (reduce conj extras  [:glog/value val])]
     `(let []
        (apply-std-logging-fn-at-level nil :error ~@args)
        (apply-log-fn-at-level
         ~val  glog/log-value! :glog/ERROR ~entry-type ~extras ~val)))))

^{:vocabulary [:glog/value
               :glog/FATAL
               ]}
(defmacro value-fatal
  ([entry-type val]
   `(value-fatal ~entry-type [] ~val))
  
  ([entry-type extras val]
   (let [args (reduce conj extras  [:glog/value val])]
     `(let []
        (apply-std-logging-fn-at-level nil :fatal ~@args)
        (apply-log-fn-at-level
         ~val  glog/log-value! :glog/FATAL ~entry-type ~extras ~val)))))

