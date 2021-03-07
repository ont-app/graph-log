# <img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="NaturalLexicon logo" :width=100 height=100/> ont-app/graph-log

Code and a small ontology for logging to an
[IGraph](https://github.com/ont-app/igraph) in clojure(script). It is
part of the ont-app project.

This this intended as a tool to be able to construct a graph of
queryable, inter-related logging events which can serve as a basis for
useful diagnostics.

It integrates with standard, string-based logging implemented with
[timbre](https://github.com/ptaoussanis/timbre).

## Contents
- [Dependencies](#Dependencies)
- [Simple usage](#Simple_usage)
  - [Logging-levels](#h4-simple-logging-levels)]
  - [Standard logging](#h4-standard-logging)
- [More advanced usage](#More_advanced_usage)
  - [Configuring the `log-graph`](#Configuring_the_log-graph)
  - [Adminstration](#Adminstration)
  - [Log entries](#Log_entries)
  - [Logging levels](#h4-logging-levels)
    - [Setting logging levels of entry types](#Setting_warning_levels_of_entry_types)
    - [Setting the global log level](#Setting_the_global_log_level)
- [Utilities](#Utilities)
  - [Archiving](#h3-archiving)
  - [Searching forward and backward](#Searching_forward_and_backward)
  - [Comparing logs](#Comparing_logs)
- [License](#License)

<a name="Dependencies"></a>
## Dependencies

Available at [Clojars](https://clojars.org/ont-app/graph-log).

[![Clojars Project](https://img.shields.io/clojars/v/ont-app/graph-log.svg)](https://clojars.org/ont-app/graph-log)

Cljdoc.org hosts [documentation](https://cljdoc.org/d/ont-app/graph-log/0.1.1).

```
(ns ....
 (:require 
   [ont-app.igraph.core :as igraph]          ;; The IGraph protocol
   [ont-app.igraph.graph :as graph]          ;; Default implementation of IGraph
   [ont-app.graph-log.core :as glog]         ;; the graph-log library
   [ont-app.graph-log.levels :refer :all]    ;; log level macros
   [taoensso.timbre :as timbre]              ;; standard logging clj/cljs
   ...
   ;; ...optionally ...
   [ont-app.graph-log.archiving :as archive]  ;; asynchronous archiving

   ))
```

<a name="Simple_usage"></a>
### Simple usage

This feature maintains a graph in memory, and is disabled by default.

To enable:
```
> (glog/log-reset!)
#object[ont_app.igraph.graph.Graph yadda yadda]
>
```

This will instantiate a graph globally declared as `@glog/log-graph`,
populated with the basic vocabulary that informs the graph-logging
process.

The minimal logging operation uses `glog/log!` and glog/log-value!:

```
> (defn get-the-answer [whos-asking]
    (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-get-the-answer 42))

> (get-the-answer "Douglas")
Hello Douglas, here's the answer...
42
>
```

;; then in the repl, we can ask for a listing of entry names...

```
> (glog/entries)
[:my-log/starting-get-the-answer_0 
 :my-log/returning-get-the-answer_1]
>
```

'0' and '1' here mark off the execution orders of each respective entry.

We can ask for a description of any element of the `log-graph` in 
[IGraph Normal Form](https://github.com/ont-app/igraph) with `show` ...

```
> (glog/show :my-log/starting-get-the-answer_0)
{:rdf/type #{:my-log/starting-get-the-answer},
 :glog/timestamp #{1576114979964},
 :glog/executionOrder #{0},
 :my-log/whos-asking #{"Douglas"}}
>
> (glog/show :my-log/starting-get-the-answer_0 :glog/executionOrder)
#{0}
>
> (glog/show :my-log/starting-get-the-answer_0 :glog/executionOrder 0)
0 ;; truthy
```

... Or we can ask for a log entry by its _execution order_ ...


```
> (glog/ith-entry 1)
[:my-log/returning-get-the-answer_1
 {:rdf/type #{:my-log/returning-get-the-answer},
  :glog/timestamp #{1576114979965},
  :glog/executionOrder #{1},
  :glog/value #{42}}]
>
```

... note that it returns a vector [_entry-id_ _entry-description_],
and that the description is again in Normal Form.

The elements in this graph are made up largely by `keyword
identifiers` ([KWIs](https://github.com/ont-app/vocabulary#defining-keyword-identifiers-kwis-mapped-to-uri-namespaces)), which are namespaced Clojure keywords serving as
[URI](https://www.wikidata.org/wiki/Q61694)s. For purposes of this
discussion "KWI" and "URI" will be used interchangeably.


KWIs in turn are mappable to an
[ontology](https://www.wikidata.org/wiki/Q324254) dedicated to
describing the various entities and relationships in play. This
ontology is defined as an
[ont-app.igraph.graph/Graph](https://github.com/ont-app/igraph/blob/master/src/ont_app/igraph/graph.cljc)
in the file
[ont.cljc](https://github.com/ont-app/graph-log/blob/master/src/ont_app/graph_log/ont.cljc).

Let's break out the KWIs in the example

|KWI |Description |
|--- |:---------- |
|:my-log/starting-get-the-answer |coined _ad hoc_ to name a class of log entries|
|:my-log/returning-get-the-answer |coined _ad hoc_ to name another class of log entries|
|:rdf/type |This correponds to a [URI in RDF's public vocabulary](https://www.w3.org/TR/rdf-schema/#ch_type) to assert an instance of a class. Part of the ont-app's design philosophy involves leveraging and integrating with public vocabularies, without a direct dependency on the full RDF stack.  |
|:my-log/returning-get-the-answer_1 |Minted automatically to name the (zero-based) 1th entry in the `log-graph`, an instance of _returning-get-the-answer_|
|:glog/timestamp |The timestamp in [milliseconds](https://en.wikipedia.org/wiki/Unix_time) associated with the entry |
|:glog/executionOrder| Asserts that this is the ith entry in the `log-graph`|
|:my-log/whos-asking |a property coined _ad hoc_ for the _starting-get-the-answer_ entry type.|
|:glog/value |the value returned by the expression being traced by any call to _glog/log-value!_|


We can query `@log-graph` with `query-log`:

```
> (glog/query-log 
    [[:?starting :rdf/type :my-log/starting-get-the-answer]
     [:?starting :my-log/whos-asking :?asker]
    ])
#{{:?starting :my-log/starting-get-the-answer_0, :?asker "Douglas"}}
>
```

This is the query format used by
[ont-app.igraph.graph/Graph](https://github.com/ont-app/igraph#Graph).
It consists of a graph pattern expressed as a vector of triples, each
elment of which is either a KWI, a literal value, or a :?variable. It
returns a set of {:?variable `value`, ...} maps.

An IGraph can also be applied as a function with 0, 1, 2, or 3 arities:
```
> (@glog/log-graph)
;; ...(returns the entire graph contents in Normal Form)
>
> (@glog/log-graph :my-log/returning-get-the-answer_1)
{:rdf/type #{:my-log/returning-get-the-answer},
  :glog/timestamp #{1576114979965},
  :glog/executionOrder #{1},
  :glog/value #{42}}
;; ... (same as glog/show)
>
> (igraph/flatten-description
    (@glog/log-graph :my-log/returning-get-the-answer_1))
{:rdf/type :my-log/returning-get-the-answer,
  :glog/timestamp 1576114979965,
  :glog/executionOrder 1,
  :glog/value 42}
>
> (@glog/log-graph :my-log/returning-get-the-answer_1 :glog/executionOrder)
#{1}
>
> (igraph/unique 
    (@glog/log-graph :my-log/returning-get-the-answer_1 :glog/executionOrder))
1
>
> (@glog/log-graph :my-log/returning-get-the-answer_1 :glog/executionOrder 0)
nil 
;; ... truthy
> 
```
As you can see, log entry classes and properties are largely declared
ad-hoc by the user, but hopefully it's clear that as your program
starts to mature, certain entry classes can be given attributes that
lend themselves as inputs to helpful diagnostic functions.

<a name="h4-simple-logging-levels"></a>
#### Logging levels

More commonly you'll probably want to attach logging statements to the usual logging levels. This can be done by swapping in say `info` or `value-info` expressions in place of `log!` and `log-value!` as follows:

```
(ns ...
  (:require
   ...
   [ont-app.graph-log.levels :refer :all]    ;; log level macros
   ...))
   
(defn get-the-answer [whos-asking]
    (info :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (value-info :my-log/returning-get-the-answer 42))

```

See [the discussion below](#h4-logging-levels) for details.

<a name="h4-standard-logging"></a>
#### Standard logging

|KWI |Description |
:--- |:---------- |
|:glog/message | A string or Mustache-type template to print to the standard logging stream via `taesano.timbre`. The flattened description of the entry will be applied to its value to resolve template {{parameters}}. |

Standard, string-based logging is done with
[timbre](https://github.com/ptaoussanis/timbre), which should be
configured directly using its API.

Typically these are integrated into the levels macros

```
> (info :my-log/starting-get-the-answer
    :my-log/whos-asking whos-asking
    :glog/message "{{{my-log/whos-asking}} is asking for the answer."
    )
```

In this example the `info` macro will generate a standard string-based
logging message keyed to the INFO logging level. It will do this by
calling `std-logging-message`, described below.


##### `std-logging-message`

The `std-logging-message` function expects a set of property/value
pairs, one of whose properties is _:glog/message_, paired with a
mustache-type template string. It will generate a string based on said
template, whose parameters should match other properies in the same
call (minus :colons).

This can be passed to standard logging functions. The library
_taoensso.timbre_ is a dependency of this library, so the following
example will be logged if timbre/*config* is configured for :debug or
lower:


Example:
```
> (timbre/debug
  (std-logging-message 
    :glog/message "This is a number: {{my-ns/number}}"
    :my-ns/number 42))
    
```

Standard logging and graph-logging can function independently, but are brought together under the common umbrella of the [levels macros](#h4-logging-levels).


<a name="More_advanced_usage"></a>
### More advanced usage

<a name="Configuring_the_log-graph"></a>
#### Configuring the `log-graph`

The graph can be reset
```
> (log-reset! <initial-graph>) -> <initial-graph>
```

This will replace any previous contents of @log-graph with
_initial-graph_. There is also an archiving utility ([discussed
below](#h3-archiving)) which can save the previous contents of
@log-graph ansynchronously.

The default initial graph is `ont-app.graph-log.core/ontology`.

```
> (log-reset!) 
#object[ont_app.igraph.graph.Graph yadda yadda]
>
```

The `ont-app.igraph.graph/Graph` data structure is immutable. The
graph `ont-app.graph-log.core/log-graph` is an atom containing an
instance of
[ont-app.igraph.graph/Graph](https://github.com/ont-app/igraph#Graph),
a lightweight, immutable implementation of the IGraph protocol
provided with _ont-app/igraph_.

All the graph-log KWI constructs are kept in the `glog` namespace.
However, much of it is aligned to namesakes in [an existing public
vocabulary called
rlog](https://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog/rlog.html
"rlog") (See
[here](https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl)
for the Turtle definition).

Since the log-graph implements IGraph, we can get the entire contents
of the log in Normal Form by invoking it as a function without
arguments:

```
> (@glog/log-graph)

{:glog/INFO
 {:rdf/type #{:glog/Level},
  :glog/priority #{3},
  :rdfs/comment #{"A standard logging level"}},

... yadda yadda

 :glog/message
 #:rdfs{:subClassOf #{:rdf/Property}, 
        :domain #{:glog/Entry},
        :range #{:rdf/Literal},
        :comment #{"\nA string or mustache template to print to the standard logging stream\nvia taesano.timbre. The flattened description of the entry will be\napplied to its value to resolve template {{parameters}}.\n"}}}
>
```

Let's break out the supporting ontology by category.

<a name="Adminstration"></a>
#### Adminstration

|KWI |Description |
:--- |:---------- |
|:glog/LogGraph |The KWI of igraph.graph-log.core/log-graph |
|:glog/entryCount |Asserts the number of log entries in this graph. |

The log-graph itself is identified by the KWI `:glog/LogGraph`. 

The entry-count is fairly self-explanatory. There's a function to access it:

```
> (glog/entry-count)
2
>
```

The log is cleared of entries and configured with the supporting
ontology when we reset. We configure the log further by adding other assertions, as we will see in sections below.

```
> (def initial-graph (add glog/ontology [_configuration-triples_]))
...
> (glog/log-reset! initial-graph) 
#object[ont_app.igraph.graph.Graph yadda yadda]
>
```

<a name="Log_entries"></a>
#### Log entries
|KWI |Description |
:--- |:---------- |
|:glog/Entry |The grandparent class of all log entries |
|:glog/executionOrder | Asserts the order of execution for some Entry within a log. |
|:glog/timestamp | Current time in milliseconds at time the Entry was created. |
|:glog/message | A string or Mustache-type template to print to the standard logging stream via `taesano.timbre`. The flattened description of the entry will be applied to its value to resolve template {{parameters}}.
|:glog/InformsUri |Refers to a KWI whose name should inform the minting of each new Entry's URI, in addition to its class and execution order, the better to understand at a glance what the log entry is about. |

As we saw above, each log expression creates a class of log entries if it doesn't already exist.

```
> (glog/show :my-log/starting-get-the-answer)
{:rdfs/subClassOf #{:glog/Entry}, ...}
>
```

Execution order and timestamp were already discussed above.

<a name="h5-glog-message"></a>
##### `:glog/message`

You may also optionally add a message which will be printed to the
traditional logging stream. It supports {{mustache}} templating:

```
> (glog/log! :my-log/starting-get-the-answer 
    :my-log/whos-asking "Douglas" 
    :glog/message "{{my-log/whos-asking}} is asking for the answer")

19-12-14 23:50:33 INFO - Douglas is asking for the answer
:my-log/starting-get-the-answer_2
>
```

<a name="h5-glog-informs-uri"></a>
##### `:glog/InformsUri`

Declaring your property to be of type `InformsUri` creates more
expressive entry names:

```
> (def expressive-log 
    (add glog/ontology 
      [:my-log/whos-asking :rdf/type :glog/InformsUri]))
> (glog/reset-log! expressive-log)
> (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking "Douglas")
:my-log/starting-get-the-answer_0_Douglas
>
```

This should only be used for properties whose values are expected to
render well as strings.

<a name="h5-glog-annotate"></a>
##### `glog/annotate!`

You can use the `annotate!` function to add arbitrary triples to
`log-graph`:

```
(glog/annotate! 
  :my-log/starting-get-the-answer_0 
  :my-log/attn-Mary 
  "Mary does this look OK to you?")
...
```

<a name="h4-logging-levels"></a>
#### Logging levels

Here's the vocabulary that relates to logging levels:

|KWI |Description |
:--- |:---------- |
|:glog/level |Asserts the Level of an Entry type |
|:glog/Level |Refers to a logging level like :glog/WARN |
|:glog/OFF |Signals that the log should not record events. |
|:glog/TRACE |Finer grained informational events than DEBUG |
|:glog/DEBUG |A standard logging level |
|:glog/INFO |A standard logging level |
|:glog/ERROR |A standard logging level |
|:glog/WARN |A standard logging level |
|:glog/FATAL |A standard logging level |
|:glog/ALL |Signals that the log should record all log statements |


<a name="Setting_the_global_log_level"></a>
##### Setting the global log level

The default log-level for the `log-graph` itself is
:glog/INFO. Managing this value can be done in two ways.

We can reset glog/log-graph with an assertion of log-level to its
namesake in the initial graph:

```
> (def debugging-log
    (add glog/ontology 
       [:glog/LogGraph :glog/level :glog/DEBUG]))
> (glog/log-reset! debugging-log)
> (@glog/log-graph :glog/LogGraph :glog/level)
#{:glog/DEBUG}
>
```

or we can also reset the log level at any time thus:

```
> (when (= whos-asking "Douglas")
    (glog/set-level! :glog/LogGraph :glog/level :glog/DEBUG))
...
```

For efficiency's sake, we want to avoid evaluating log statements when
the log level in not appropriate, and so this stuff is handled by a
set of macros defined in `ont-app.graph-log.levels`. 

Here are two examples, the first of which calls `log!` and the second of which calls `log-value!`

```
> (glog/log-reset! debugging-log)
> (debug
    :my-log/starting-get-the-answer 
    :my-log/whos-asking "Douglas")
:my-log-starting-get-the-answer_1
>
> (value-debug :my-log/returning-get-the-answer 42)
42
> (glog/entries)
[:my-log/starting-get-the-answer_0 
 :my-log/returning-get-the-answer_1]
>
``` 

... These entries will only create log entries, and will only evaluate
their arguments, if the current logging level is >= the global logging
level, with the exception described in the next section.

There are of course corresponding macros for all the other log levels.


##### Logging levels and standard logging

Each of these macros also makes calls to standard logging functions in
cases where:
- timbre is configured to be senstive to the operative debug level 
- the entry has a _:glog/message_ clause.

In cases where the logging statment includes a `:glog/message` clause,
the logging levels also inform standard messages, keyed to the value of
(:level timbre/*config*).

When logging levels are appropriate, standard logging messages will be
issued regardless of the state of the log-graph:

```
> (reset! glog/log-graph nil) ;; turning off all graph-logging
> (:level timbre/*config*)
:debug
> (debug :my-log/demoning-messages 
    :glog/message "This is a number: {{my-log/number}}"
    :my-log/number 42)
yadda yadda WARN [yadda] - This is a number: 42
nil
> 

```

<a name="Setting_warning_levels_of_entry_types"></a>
##### Setting logging levels of entry types

The level-based logging macros described above (e.g. (debug ...) are
conditioned on their associated levels, but we can override the
effective logging level of a given entry-type with `glog/set-level!`.


```
> (glog/log-reset!)
> (glog/set-level! :glog/LogGraph :glog/INFO)
>
> (glog/set-level! :my-log/demoing-log-level :glog/WARN)
> (glog/show :my-log/demoing-log-level)
{:glog/level #{:glog/WARN}, 
 :rdfs/subClassOf #{:glog/Entry}}
> ;; this will be logged in spite of 'debug' < 'info':
> (debug :my-log/demoing-log-level) 
> (glog/entries)
[:my-log/demoing-log-level_0] 
>
```

Having set the level, only entry types whose logging level matches or
exceeds that of the log will be entered.

You can turn logging off by setting its level to `glog/OFF`

```
> (def no-logging
    (add glog/ontology 
       [:glog/LogGraph :glog/level :glog/OFF]))
...
> (glog/reset-log! no-logging)
...
> (fatal :my-log/we-are-so-screwed!)
...
> (glog/entries)
[]
>
```

<a name="Utilities"></a>
## Utilities

<a name="#h3-archiving"></a>
### Archiving (JVM version only)

|KWI |Description |
| :--- | :---------- |
| :glog/archivePathFn |Asserts a function [g] -&gt; archive-path to which the current state of the log may be written before resetting. |
| :glog/archiveDirectory | Asserts the directory portion of the archive-path used by archivePathFn. (only applicable if the local file system is used) |
| :glog/continuingFrom | Asserts the archive-path of the log previously archived on the last reset. |
| :glog/iteration | Asserts the number of times the log in this lineage has been reset. |
| :glog/FreshArchive | the class of archived logs which are the first in its lineage. |

The contents of `glog/log-graph` are by default held in memory until
the log is reset. 

Long-running processes will naturally need to reclaim that memory by
being reset periodically, and in many cases we may want to preserve
the history of such logs. The vocabulary listed above provides support
for doing so.

To enable archiving require the archiving module...

```
(ns ....
   (:require 
     ...
     [ont-app.graph-log.archiving :as archive]
     )
  )
```

Every call to `archive/log-reset!` will behave exactly as
glog/log-reset! does, but as a side-effect, it will asynchronously
publish a representation of the transition between the old and new
logs to a file. When the archiving operation is complete, this triple
will be asserted:

```
> (@log-graph :glog/LogGraph `glog/continuingFrom`)
/path/to/previous-log.edn
```


By default, each file will be named after the beginning and ending
timestamps of its entries, and be written to the _/tmp_
directory. Best practice would define a target directory:

```
> (def archivable-log 
    (add glog/ontology
      [[:glog/LogGraph 
        :glog/archiveDirectory "/tmp/myAppLog"
        ]]))
```

Asserting an `archiveDirectory` for the `LogGraph` will direct all log
archive files to that directory. This is optional. The default is `/tmp`.

With this configuration, the following call:

```
> (glog/log-reset! archivable-log)
```

... will establish a log with this configuration. Then another call
after adding an entry:

```
> (info :my-log/Test-archiving)
...
> (archive/log-reset! archivable-log) 
```

...will give you a fresh `log-graph`, with the following side-effects:

- A new file like `/tmp/myAppLog/1576yadda-1576yadda.edn` will
  be created (using integer timestamps from the first and last
  entries).
- The previous contents of `log-graph` will be written to said file
  with the following modifications:
  - The contents of the next `initial-graph` (in this case
    `glog/ontology`) will be subtracted from the original contents
  - Compiled values (asserted with `:igraph/compiledAs`) will be
    filtered out so that they don't choke the reader if you want to
    slurp the contents later.
- There will be an additional assertion in the new log-graph:
  `:glog/LogGraph :glog/continuingFrom
  "/tmp/myAppLog/1576yadda-1576yadda.edn"`.
- There will be an assertion counting the number of resets:
  `:glog/LogGraph :glog/iteration <one greater than the last iteration>`

```

> (igraph/unique (@glog/log-graph :glog/LogGraph :glog/continuingFrom))
"/tmp/myAppLog/1576yadda-1576yadda.edn"
> (-> 
    (clojure.java.io/as-file 
      (igraph/unique (@glog/log-graph :glog/LogGraph :glog/continuingFrom))) 
      (.exists))
true
>
```

Having written the archive file, you can read the contents into any
IGraph-compliant graph implementation thus:

```
> (def restored-log-graph 
    (let [g (make-graph)] ;; implementation-specific
       (igraph/read-from-file 
          g 
          (igraph/unique 
            (@glog/log-graph :glog/LogGraph :glog/continuingFrom)))))
...
> (restored-log-graph)
{:my-log/Test-archiving_0
 {:rdf/type #{:my-log/Test-archiving},
  :glog/timestamp #{157yadda-yadda},
  :glog/executionOrder #{0}},
 :glog/LogGraph
 #:glog{:archiveDirectory #{"/tmp/myAppLog"},
        :entryCount #{1},
        :hasEntry #{:my-log/Test-archiving_0}},
 :my-log/Test-archiving
 {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}}}
>
```

In the example above, the file `/tmp/myAppLog/1576yadda-1576yadda.edn`
was generated by the default function `archive/archive-path`, a function
`[log-graph] -> path`, which references the `archiveDirectory`
property mentined above. It generates a canonical pathname for the
current graph based on timestamps. You may override this with your own
function with the same signature, asserting something like:

```
> (def archivable-log 
    (add glog/ontology
      [[:glog/LogGraph 
        :glob/archivePathFn :my-log/MyArchivePathFn
       ]
       [:my-log/MyArchivePathFn 
         :igraph/compiledAs my-ns/my-archive-path
        ]]))
...
> (archive/reset-graph! archivable-log)
```

And of course then it would be up to you whether
`my-ns/my-archive-path` availed itself of the
`archiveDirectory` construct.


<a name="Searching_forward_and_backward"></a>
### Searching forward and backward

In addition to `entries`, `show`, `ith-entry` discussed above, there are functions to enable searching for entries which match some test, starting from either the KWI of some entry or it's entry-order.


```
> (defn is-starting-get-the-answer? [g entry]
    (g entry :rdf/type :my-log/starting-get-the-answer))
... 
> (search-backward 
    is-starting-get-the-answer? 
    :my-log/returning-get-the-answer_1) ;; or just `1`
:my-log/starting-get-the-answer_0
>
```

<a name="Comparing_logs"></a>
### Comparing logs

One use-case for `graph-log` is comparing two log-graphs after making a change.

```
> (def the-answer 42)
...
> (defn get-the-answer [whos-asking]
    (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-get-the-answer the-answer))
...
> (glog/log-reset!)
...
> (get-the-answer "Douglas")
...
> (def A (glog/remove-variant-values @glog/log-graph))
...
> (def the-answer 43)
...
> (glog/log-reset!)
...
> (get-the-answer "Douglas")
...
> (def B (glog/remove-variant-values @glog/log-graph))

```

So `A` and `B` reflect the logs run on the same code with different
values for `the-answer`.

Because we called `remove-variant-values` on each of them, values
which are guaranteed to be different between the two runs such as
timestamps have been removed.

We can subtract configuration stuff with IGraphSet operations:

```
> (def A-and-B 
    (igraph/difference
      (igraph/intersection A B)
        glog/ontology))
...
> (A-and-B)
{
  :my-log/returning-get-the-answer 
    {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}},
  :my-log/starting-get-the-answer 
    {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}},
  :glog/LogGraph 
    #:glog{:hasEntry #{:my-log/returning-get-the-answer_1 :my-log/starting-get-the-answer_0}, :entryCount #{2}},
  :igraph/Vocabulary 
    #:igraph{:compiledAs #{:compiled}}, 
  :my-log/returning-get-the-answer_1 
    {:rdf/type #{:my-log/returning-get-the-answer}, :glog/value #{}, :glog/executionOrder #{1}},
  :my-log/starting-get-the-answer_0 
    {:rdf/type #{:my-log/starting-get-the-answer}, 
     :my-log/whos-asking #{"Douglas"}, 
     :glog/executionOrder #{0}}
}
> (def A-not-B (igraph/difference A (igraph/union glog/ontology A-and-B)))
...
> (A-not-B)
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}
> (def B-not-A (igraph/difference B (igraph/union glog/ontology A-and-B)))
...
> (B-not-A)
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}
>
```

There's a function `compare-shared-entries` which will derive the
difference of A and B for us.

```
> (def g (glog/compare-shared-entries A B))
...
> (g)
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}
> (def g (glog/compare-shared-entries B A))
...
> (g)
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}
>
```

The `find-divergence` function will return the set of entries that are
shared between A and B, then give us two separate graphs representing
the first point of divergence:

```
> (let [[shared [ga gb]] (glog/find-divergence A B)] shared)
[:my-log/starting-get-the-answer_0]
> (let [[shared [ga gb]] (glog/find-divergence A B)] (ga))
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}
> (let [[shared [ga gb]] (glog/find-divergence A B)] (gb))
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}
>
```

Or `report-divergence` will pprint a summary and return the contrasting graphs:

```
> (glog/report-divergence A B)
Shared:
[:my-log/starting-get-the-answer_0]
In G1:
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}
In G2:
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}

[#object[ont_app.igraph.graph.Graph 0x7069e763 "ont_app.igraph.graph.Graph@7069e763"]
 #object[ont_app.igraph.graph.Graph 0x813382c "ont_app.igraph.graph.Graph@813382c"]]
 > 
```

<a name="License"></a>
## License

Copyright © 2020-21 Eric D. Scott

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

<table>
<tr>
<td width=75>
<img src="http://ericdscott.com/NaturalLexiconLogo.png" alt="Natural Lexicon logo" :width=50 height=50/> </td>
<td>
<p>Natural Lexicon logo - Copyright © 2020 Eric D. Scott. Artwork by Athena M. Scott.</p>
<p>Released under <a href="https://creativecommons.org/licenses/by-sa/4.0/">Creative Commons Attribution-ShareAlike 4.0 International license</a>. Under the terms of this license, if you display this logo or derivates thereof, you must include an attribution to the original source, with a link to https://github.com/ont-app, or  http://ericdscott.com. </p> 
</td>
</tr>
<table>
