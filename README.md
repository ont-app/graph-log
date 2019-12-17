# graph-log

Code and a small ontology for logging to an IGraph 

## Usage

```
(ns ....
 (:require 
 [ont-app.igraph.core :refer [add]]
 [ont-app.igraph.graph :refer [make-graph]]
 [ont-app.graph-log.core :as glog]
   ...
   ))
```

### Simple usage

```
(defn get-the-answer [whos-asking]
    (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-get-the-answer 42))
```

```
(glog/log-reset!)
``` 
   
```
(get-the-answer "Douglas")
;; ->
Hello Douglas, here's the answer...
42
```

;; then in the repl, we can ask for a listing of entry names...
```
(glog/entries)
;;->
[:my-log/starting-get-the-answer_0 
 :my-log/returning-get-the-answer_1]

```

We can ask for a description of any element of the `log-graph` in 
[IGraph Normal Form](https://github.com/ont-app/igraph)...

```
(glog/show :my-log/starting-get-the-answer_0)
;; ->
{:rdf/type #{:my-log/starting-get-the-answer},
 :glog/timestamp #{1576114979964},
 :glog/executionOrder #{0},
 :my-log/whos-asking #{"Douglas"}}

```
... Or we can ask for log entries by `executionOrder`...

```
(glog/ith-entry 1)
;; ->
[:my-log/returning-get-the-answer_1
 {:rdf/type #{:my-log/returning-get-the-answer},
  :glog/timestamp #{1576114979965},
  :glog/executionOrder #{1},
  :glog/value #{42}}]
```
... note that it returns a vector [`entry-id` `entry-description`], and
that the description is again in Normal Form.

The values in this description are keyed to a vocabulary in an
[ontology](https://www.wikidata.org/wiki/Q324254) dedicated to graph-log. This ontology is defined as an
ont-app.igraph.graph/Graph in the file [ont.cljc](https://github.com/ont-app/graph-log/blob/master/src/ont_app/graph_log/ont.cljc).

This ontology is made up largely by `keyword-identifiers` (KWIs), which
are namespaced Clojure keywords serving as [URI](https://www.wikidata.org/wiki/Q61694)s. 

Let's break out the KWIs in the example

|KWI |Description |
|--- |:---------- |
|:my-log/starting-get-the-answer |coined _ad hoc_ to name a class of log entries|
|:my-log/returning-get-the-answer |coined _ad hoc_ to name another class of log entries|
|:rdf/type |This correponds to a [URI in RDF's public vocabulary](https://www.w3.org/TR/rdf-schema/#ch_type) to assert an instance of a class. Part of the ont-app's design philosophy involves leveraging and integrating with public vocabularies.  |
|:my-log/returning-get-the-answer_1 |Minted automatically to name the 1th entry in the `log-graph`, an instance of _returning-get-the-answer_|
|:glog/timestamp |The timestamp in milliseconds associated with the entry |
|:glog/executionOrder| Asserts that this is the ith entry in the `log-graph`|
|:my-log/whos-asking |a property coined _ad hoc_ for the _starting-get-the-answer_ entry type.|
|:glog/value |the value returned by the expression being traced by any call to glog/log-value!|


We can query `@log-graph` with `query-log`:

```
(query-log [[:?starting :rdf/type :my-log/starting-get-the-answer]
            [:?starting :my-log/whos-asking :?asker]
           ])
;;->
#{{:?starting :my-log/starting-get-the-answer_0, :?asker "Douglas"}}

```

This query format used by ont-app.igraph.graph/Graph. It consists of a
graph pattern expressed as a vector of triples, each elment of which
is either a KWI, a value, or a :?variable. It returns a set of
{:?variable `value`, ...} maps. Other implementations of IGraph will have
their own _native representations_, and their own query formats. The
`query-log` function is the only part of `graph-log` where differing
query formats will come into play.


As you can see, log entry classes and properties are largely declared
ad-hoc by the user, but hopefully it's clear that as your program
starts to mature, certain entry classes can be given attributes that
lend themselves as inputs to helpful diagnostic functions.


### More advanced usage

#### Configuring the `log-graph`

The graph can be reset
```
(log-reset! `initial-graph`) -> `initial-graph`
```
The default initial graph is `ont-app.graph-log.core/ontology`
```
(log-reset!) 

-> 
#object[ont_app.igraph.graph.Graph 0x59f517f7 "ont_app.igraph.graph.Graph@59f517f7"]

```

All the graph-log constructs are kept in the `glog` namespace, but much of it is aligned to namesakes in [an existing public vocabulary called rlog](https://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog/rlog.html "rlog") (See [here](https://github.com/NLP2RDF/ontologies/blob/master/rlog/rlog.ttl) for the Turtle definition).

Since the log-graph implements IGraph, we can get the entire contents of the log in Normal Form by invoking it as a function without arguments:
```
(@glog/log-graph)
;; ->
{:glog/INFO
 {:rdf/type #{:glog/Level},
  :glog/priority #{3},
  :rdfs/comment #{"A standard logging level"}},

... yadda yadda

 :glog/message
 #:rdfs{:subClassOf #{:rdf/Property},
        :domain #{:glog/Entry},
        :range #{:rdf/Literal},
        :comment
        #{"\nA string or mustache-type template to print to the standard logging stream\nvia taesano.timber. The flattened description of the entry will be\napplied to its value to resolve template {{parameters}}.\n"}},
 :glog/Entry #:rdfs{:comment #{"Refers to an entry in a log"}}}
```

Let's break out the supporting ontology by category.

#### Adminstration


|KWI |Description |
:--- |:---------- |
|:glog/LogGraph |The URI of igraph.graph-log.core/log-graph |
|:glog/entryCount |Asserts the number of log entries in this graph. |

The log-graph itself is identified by `:glog/LogGraph`. 

The entry-count is fairly self-explanatory.

There's a function:
```
(entry-count)
;; ->
2
```

The log is cleared of entries and configured with the supporting
ontology when we reset:

```
(glog/log-reset!) ;;;loads the ontology
```
We configure the log further by adding other assertions, as we will see in sections below.

```
(def initial-graph (add glog/ontology [...]))

(glog/log-reset! initial-graph) 

```

#### Log entries
|KWI |Description |
:--- |:---------- |
|:glog/Entry |Refers to an entry in a log |
|:glog/executionOrder | Asserts the order of execution for some Entry within a log. |
|:glog/timestamp | Current time in milliseconds at time Entry was created. |
|:glog/message | A string or Mustache-type template to print to the standard logging stream via `taesano.timbre`. The flattened description of the entry will be applied to its value to resolve template {{parameters}}.
|:glog/InformsUri |Refers to a kwi whose name should inform the minting of each new Entry's URI, in addition to its class and execution order, the better to understand at a glance what the log entry is about. |

As we saw above, each log expression creates a class of log entries if it doesn't already exist.

```
(glog/show :my-log/starting-get-the-answer)
;; ->
{:rdfs/subClassOf #{:glog/Entry}, ...}
```

Execution order and timestamp were already discussed above.

You may also optionally add a message which will be printed to the
traditional logging stream. It supports {{mustache}} templating:

```
(glog/log! :my-log/starting-get-the-answer 
    :my-log/whos-asking "Douglas" 
    :glog/message "{{my-log/whos-asking}} is asking for the answer")

19-12-14 23:50:33 INFO - Douglas is asking for the answer

;; =>

:my-log/starting-get-the-answer_2

```

Declaring your property to be of type `InformsUri` can create more
expressive entry names:

```
(def expressive-log 
  (add glog/ontology 
    [:my-log/whos-asking :rdf/type :glog/InformsUri]))

(glog/log! :my-log/starting-get-the-answer :my-log/whos-asking "Douglas")

;;->

:my-log/starting-get-the-answer_2_Douglas

```

This should only be used for properties whose values are expected to
render well as strings.

#### Warning levels

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

These all have namesakes in [rlog](https://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog/rlog.html).

##### Setting warning levels of entry types
We can declare the level of each entry type in the log declaration. 

```
(glog/log! :my-log/demoing-log-level :glog/level :glog/WARN)
;; ->
:my-log/demoing-log-level_3 ;; note that the '3' marks this as an instance
```
Log level is assigned to the entry class itself, and not the individual entry:
```
(glog/show :my-log/demoing-log-level)
;; ->
{:glog/level #{:glog/WARN}, 
 :rdfs/subClassOf #{:glog/Entry}}
```

We could also have used one of the dedicated logging expressions and
achieved the same effect:

```
(glog/warn! :my-log/demoing-log-level)
```

There are analogous statements for the other log levels:
- `glog/debug!`
- `glog/info!`
- `glog/warn!`
- `glog/error!`
- `glog/fatal!`

And for value entries as well:
- `glog/value-debug!`
- `glog/value-info!`
- `glog/value-warn!`
- `glog/value-error!`
- `glog/value-fatal!`

Log declarations only establish a log level for their associated entry
types if there is not already a log-level set for that type.

We can set the log level for the entry type directly thus:

```
(glog/set-level! :my-log/demoing-log-level :glog/DEBUG)

(glog/show :my-log/demoing-log-level)

;; ->

{:rdfs/subClassOf #{:glog/Entry}, :glog/level #{:glog/DEBUG}}
```

##### Setting the global log level

The default log-level for the `log-graph` itself is
:glog/INFO. Managing this value can be done in two ways.

We can reset glog/log-graph with an assertion of log-level to its
namesake in the initial graph:

```
(def debugging-log
  (add glog/ontology 
       [:glog/LogGraph :glog/level :glog/DEBUG]))

(glog/log-reset! debugging-log)

(@glog/log-graph :glog/LogGraph :glog/level)

->
#{:glog/DEBUG}
       
```

or we can also reset the log level at any time thus:

```
(when (= whos-asking "Douglas")
    (glog/set-level! :glog/LogGraph :glog/level :glog/DEBUG))
```

Having set the level, only entry types whose logging level matches or
exceeds that of the log will be entered.

```
(def debugging-log
  (add glog/ontology 
       [:glog/LogGraph :glog/level :glog/DEBUG]))
       
(glog/log-reset! debugging-log)

(glog/debug! ::demo-log-level)

;; Returns the KWI for the new entry:
;;->  :ont-app.graph-log.core-test/demo-log-level_0

(glog/entries)
;; -> [:ont-app.graph-log.core-test/demo-log-level_0]

(glog/set-level! :glog/LogGraph :glog/WARN)

(glog/debug! ::demo-log-level)

;; No new entry created, returns nil:
;; -> nil

;; So there's no change in the set of entries...
(glog/entries)
;; -> [:ont-app.graph-log.core-test/demo-log-level_0]

```

You can turn logging off by setting its level to `glog/OFF`

```
(def no-logging
  (add glog/ontology 
       [:glog/LogGraph :glog/level :glog/OFF]))

(glog/reset-log! no-logging)

```


#### Archiving
|KWI |Description |
:--- |:---------- |
|:glog/ArchiveFn |A function [g] -&gt; archive-path, with side-effect of saving the current log before resetting. Only invoked if :igraph/compiledAs is asserted with an executable function. |
|:glog/archivePathFn |Asserts a function [g] -&gt; archive-path to which the current state of the log may be writtenbefore resetting. |
|:glog/archiveDirectory |Asserts the directory portion of the archive-path used by archivePathFn. (only applicable if the local file system is used) |
|:glog/continuingFrom |Asserts the archive-path of the log previously archived on the last reset. |
|

The contents of `glog/log-graph` are by default held in memory until
the log is reset. 

Long-running processes will naturally need to reclaim that memory by
being reset periodically, and in many cases we will want to preserve
the history of such logs. The vocabulary listed above provides support
for doing so.

When properly configured, a call to `log-reset!` will write the
contents of the graph in Normal Form (minus a few things described
below) to a file, or any other medium you care to support in code.

Let's start with an example configuration:
```
(def archived-log 
  (add glog/ontology
    [[:glog/ArchiveFn 
      :igraph/compiledAs glog/save-to-archive
      ]
     [:glog/LogGraph 
      :glog/archiveDirectory "/tmp/myAppLog"
      ]]))
```

The first clause in the configuration asserts that the `ArchiveFn` is
associated with a compiled executable function, thus enabling
it. Graph-log provides the `save-to-archive` function (on the :clj
platform only) to provide what is hoped to be good default behavior
for this purpose.

Any function provided for `ArchiveFn` must have the signature `[g] ->
URL`, taking the the log graph as an argument, writing  Normal Form to whatever
medium makes sense, and returning an identifier of the resource in a form
that will make it retrievable later, such as a URL.

Asserting an `archiveDirectory` for the `LogGraph` will direct all log
archive files to that directory. This is optional. The default is `/tmp`.

With this configuration, the following call:

```
(glog/log-reset! archived-log)

```

... will establish a log with this configuration. Then another call
after running your program for a while:

```
(glog/log-reset! glog/ontology) 
;; this will turn archiving off
```

...will give you a fresh `log-graph`, with the following side-effects.

- A new file like `/tmp/myAppLog/1576114979964-1576115000000.edn` will
  be created (using integer timestamps from the first and last
  entries).
- The previous contents of `log-graph` will be written to said file with the following modifications:
  - The contents of the next `initial-graph` (in this case
    `glog/ontology`) will be subtracted from the original contents
  - Compiled values (asserted with `:igraph/compiledAs`) will be filtered out so that they don't choke the reader if you want to slurp the contents later.
- There will be an additional assertion in the new log-graph:
  `:glog/LogGraph :glog/continuingFrom
  "/tmp/myAppLog/1576114979964-1576115000000.edn.edn"`.


In the example above, the file `/tmp/myAppLog/1576114979964-1576115000000.edn` was generated by the default function `glog/archive-path`, a function
`[log-graph] -> path`, which references the `archiveDirectory`
property mentined above, and generates a canonical pathname for the
current graph based on timestamps. You may override this with your own function with the same signature, asserting:

```

(def archived-log 
  (add glog/ontology
    [[:glog/ArchiveFn 
      :igraph/compiledAs glog/save-to-archive
      ]
    [:glog/LogGraph 
     :glob/archivePathFn :my-log/MyArchivePathFn
     ]
    [:my-log/MyArchivePathFn 
    :igraph/compiledAs my-ns/my-archive-path
     ]]))
```

And of course then it would be up to you whether
`my-ns/my-archive-path` availed itself of the
`archiveDirectory` construct.


Having written the archive file, you can read the contents into any IGraph-compliant graph implementation thus:

```
(require '[ont-app.igraph.core :as igraph])
(require '[ont-app.igraph.graph :as graph])

(def my-log-graph 
  (igraph/read-from-file 
    (graph/make-graph) ;; or any IGraph implementation
    "/tmp/myAppLog/1576114979964-1576115000000.edn"))
```

The contents of that file should then be accessible using all the
IGraph facilities for doing so.


## Utilities

### Searching forward and backward

In addition to `entries`, `show`, `ith-entry` discussed above, there are functions to enable searching for entries which match some test, starting from either the KWI of some entry or it's entry-order.


```
(defn is-starting-get-the-answer? [g entry]
  (g entry :rdf/type :my-log/starting-get-the-answer))
  
(search-backward 
  is-starting-get-the-answer? 
  :my-log/returning-get-the-answer_1)

;; ->

:my-log/starting-get-the-answer_0

```

### Comparing logs

One use-case for `graph-log` is comparing two log-graphs after making a change.

```
(def the-answer 42)

(defn get-the-answer [whos-asking]
    (glog/log! :my-log/starting-get-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-get-the-answer the-answer))
    
(glog/log-reset!)

(get-the-answer "Douglas")

(def A (glog/remove-variant-values @glog/log-graph))

(def the-answer 43)

(glog/log-reset!)

(get-the-answer "Douglas")

(def B (glog/remove-variant-values @glog/log-graph))

```

So `A` and `B` reflect the logs run on the same code with different
values for `the-answer`.

Because we called `remove-variant-values` on each of them, values
which are guaranteed to be different between the two runs such as
timestamps have been removed.

We can subtract configuration stuff with IGraphSet operations:

```
(def A-and-B 
  (igraph/difference
    (igraph/intersection A B)
        glog/ontology))

(A-and-B)
;; ->

{:my-log/returning-get-the-answer
 {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}},
 :my-log/starting-get-the-answer
 {:glog/level #{:glog/INFO}, :rdfs/subClassOf #{:glog/Entry}},
 :glog/LogGraph
 #:glog{:hasEntry
        #{:my-log/returning-get-the-answer_1
          :my-log/starting-get-the-answer_0},
        :entryCount #{2}},
 :igraph/Vocabulary #:igraph{:compiledAs #{:compiled}},
 :my-log/returning-get-the-answer_1
 {:rdf/type #{:my-log/returning-get-the-answer},
  :glog/value #{},
  :glog/executionOrder #{1}},
 :my-log/starting-get-the-answer_0
 {:rdf/type #{:my-log/starting-get-the-answer},
  :my-log/whos-asking #{"Douglas"},
  :glog/executionOrder #{0}}}

(def A-not-B (igraph/difference A (igraph/union glog/ontology A-and-B)))

(A-not-B)
;;->
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}

(def B-not-A (igraph/difference B (igraph/union glog/ontology A-and-B)))

(B-not-A)
;; -> 
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}
```

There's a function `compare-shared-entries` which will derive the
difference of A and B for us.

```
(def g (glog/compare-shared-entries A B))
(g)
;->

#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}

(def g (glog/compare-shared-entries B A))
(g)
;->

#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}

```

The `find-divergence` function will return the set of entries that are
shared between A and B, then give us two separate graphs representing
the first point of divergence:

```
(let [[shared [ga gb]] (glog/find-divergence A B)] shared)
;->
[:my-log/starting-get-the-answer_0]


(let [[shared [ga gb]] (glog/find-divergence A B)] (ga))
;->
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}


(let [[shared [ga gb]] (glog/find-divergence A B)] (gb))
;->
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}
```

Or `report-divergence` will pprint a summary and return the contrasting graphs:
```
(glog/report-divergence A B)
Shared:
[:my-log/starting-get-the-answer_0]
In G1:
#:my-log{:returning-get-the-answer_1 #:glog{:value #{42}}}
In G2:
#:my-log{:returning-get-the-answer_1 #:glog{:value #{43}}}

;; ->

[#object[ont_app.igraph.graph.Graph 0x7069e763 "ont_app.igraph.graph.Graph@7069e763"]
 #object[ont_app.igraph.graph.Graph 0x813382c "ont_app.igraph.graph.Graph@813382c"]]
```

## License

Copyright © 2019 Eric D. Scott

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.

