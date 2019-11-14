# graph-log

Code and a small ontology for logging to an IGraph implementation.

## Usage

```
(ns ....
 (:require 
 [ont-app.igraph.core :refer [add]]
 [ont-app.igraph.graph :refer [make-graph]]
 [ont-app.graph-log.core :as glog]
   ...
   ))
   
(glog/reset-log! 
  (add (make-graph) 
       [:mylog/whos-asking :rdf/type :glog/InformsUri]))
       
(defn the-answer [whos-asking]
    (glog/log :my-log/starting-the-answer :my-log/whos-asking whos-asking)
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value :my-log/returning-the-answer 42))
    
(the-answer "Douglas")
Hello Douglas, here's the answer...
42

;; then
(glog/listEntries)
;;->
[:my-log/starting-the-answer_1_Douglas
 :my-log/returning-the-answer_2
 ]

(@glog/log-graph :my-log/starting-the-answer_1_Douglas)
;; ->
{:rdf/type :my-log/starting-the-answer
 :glog/executionOrder 1
 :glog/timestamp 1234567
 :my-log/whos-asking "Douglas"}
 
(@glog/log-graph :my-log/returning-the-answer_2)
;; ->
{:rdf/type :my-log/returning-the-answer
 :glog/executionOrder 2
 :glog/timestamp 1234568
 :glog/value 42}

(@glog/log-graph :my-log/returning-the-answer_2 :glog/value)
;; ->
#{42}


(query @glog/log-graph [[:?starting :rdf/type :my-log/starting-the-answer]
                        [:?starting :my-log/whos-asking :?asker]
                       ])
;;->
[{:?starting  :glog/executionOrder 1
  :?asker "Douglas"
  }]

```
Log entry classes and arguments can be totally ad-hoc, but hopefully it's clear that as your program starts to mature, certain entry classes will be given attributes that lend themselves as inputs to helpful diagnostic functions.

Another idiom:
```
(defn the-answer [whos-asking]
    (let [starting-entry (glog/log :my-log/starting-the-answer 
                                   :my-log/whos-asking whos-asking)
         ]
    (println "Hello " whos-asking ", here's the answer...")
    (glog/log-value 
      :my-log/returning-the-answer 
      [:my-log/response-to starting-entry] ;; <- optional attributes
      42)))

(query @glog/log-graph [[:?starting :my-log-whos-asking "Douglas"]
                        [:?return :my-log/response-to :?starting]
                       ])

```

Watch this space for more complete documentation.

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
