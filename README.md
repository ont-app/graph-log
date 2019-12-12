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
(defn the-answer [whos-asking]
    (glog/log! :my-log/starting-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value! :my-log/returning-the-answer 42))
```

```
(glog/log-reset!)
``` 
   
```
(the-answer "Douglas")
;; ->
Hello Douglas, here's the answer...
42
```

;; then in the repl, we can ask for a listing of entry names...
```
(glog/entries)
;;->
[:my-log/starting-the-answer_0 
 :my-log/returning-the-answer_1]

```

We can ask for a description of any element of the `log-graph` in 
`IGraph Normal Form`...

```
(glog/show :my-log/starting-the-answer_0)
;; ->
{:rdf/type #{:my-log/starting-the-answer},
 :glog/timestamp #{1576114979964},
 :glog/executionOrder #{0},
 :my-log/whos-asking #{"Douglas"}}

```
... Or we can ask for log entries by `executionOrder`...

```
(glog/ith-entry 1)
;; ->
[:my-log/returning-the-answer_1
 {:rdf/type #{:my-log/returning-the-answer},
  :glog/timestamp #{1576114979965},
  :glog/executionOrder #{1},
  :glog/value #{42}}]
```
... note that it returns a vector [<entry-id> <entry-description>], and
that the description is again in Normal Form.

The values in this description are keyed to a vocabulary in an
`ontology` dedicated to graph-log. This ontology is defined as an
ont-app.igraph.graph/Graph in the file ont.cljc.

This ontology is made up largely by `keyword-identifiers` (KWIs), which
are namespaced Clojure keywords used exactly like URIs are in RDF.

Let's break out the KWIs in the example
- `:my-log/starting-the-answer` -- coined _ad hoc_ to name a class of
      log entries
- `:my-log/returning-the-answer` -- coined _ad hoc_ to name another class of
      log entries
- `:rdf/type` -- This correponds to a [URI in RDF's public
      vocabulary](https://www.w3.org/TR/rdf-schema/#ch_type) to assert
      an instance of a class. Part of the ont-app's design philosophy
      involves leveraging and integrating with public vocabularies.
- `:my-log/returning-the-answer_1` -- Minted automatically to name the
      1th entry in the `log-graph`, an instance of
      _returning-the-answer_
- `:glog/timestamp -- The timestamp in milliseconds associated with
      the entry
- `:glog/executionOrder -- Asserts that this is the ith entry in the
      `graph-log`
- `:glog/value` -- the value returned by the expression being traced
      by any call to glog/log-value!
- `:my-log/whos-asking` -- a property coined _ad hoc_ for the
  _starting-the-answer_ entry type.


We can query `@log-graph` with `query-log`:

```
(query-log [[:?starting :rdf/type :my-log/starting-the-answer]
            [:?starting :my-log/whos-asking :?asker]
           ])
;;->
#{{:?starting :my-log/starting-the-answer_0, :?asker "Douglas"}}

```

This query format used by ont-app.igraph.graph/Graph. It consists of a
graph pattern expressed as a vector of triples, each elment of which
is either a KWI, a value, or a :?variable. It returns a set of
{:?variable <value>} maps. 


As you can see, log entry classes and properties are totally ad-hoc,
but hopefully it's clear that as your program starts to mature,
certain entry classes will be given attributes that lend themselves as
inputs to helpful diagnostic functions.



## License

Copyright © 2019 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
