(defproject ont-app/graph-log "0.1.2"
  :description "An graph-based logging system for debugging"
  :url "https://github.com/ont-app/graph-log"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [;; deps adjustments
                 [com.taoensso/timbre "5.1.2"] ;;
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/spec.alpha "0.2.194"]
                 [com.google.errorprone/error_prone_annotations "2.4.0"]
                 [org.clojure/tools.reader "1.3.3"]
                 ;; 3rd party libraries
                 [cljstache "2.0.6"]
                 [lein-doo "0.1.11"] ;; cljs testing
                 ;;
                 [ont-app/igraph "0.1.5"]
                 [ont-app/igraph-vocabulary "0.1.2"] 
                 [ont-app/vocabulary "0.1.3"]
                 ]
  :plugins [[lein-codox "0.10.6"]
            [lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-doo "0.1.11"]
            ]

  :repl-options {:init-ns ont-app.graph-log.core}
  :target-path "target/%s"
  :resource-paths ["resources" "target/cljsbuild"]
  
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :cljsbuild
  {
   :test-commands {"test" ["lein" "doo" "node" "test" "once"]}
   :builds
   {
    :test {:source-paths ["src" "test"]
           :compiler {
                      :main ont-app.graph-log.doo
                      :target :nodejs
                      :asset-path "resources/test/js/compiled/out"
                      :output-to "resources/test/compiled.js"
                      :output-dir "resources/test/js/compiled/out"
                      :optimizations :advanced ;;:none
                      }
           }
    }} ;; end cljsbuild
  :codox {:output-path "doc"}
  :profiles {:uberjar {}}
  :clean-targets
    ^{:protect false}
     ["resources/public/js/compiled"
     "resources/test"
     :target-path
      ]
  :eastwood {:exclude-linters [:unused-meta-on-macro]}
  )
       
