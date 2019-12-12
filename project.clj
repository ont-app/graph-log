(defproject ont-app/graph-log "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [lein-doo "0.1.11"]
                 [com.taoensso/timbre "4.10.0"] ;; basic logging
                 ;;
                 [ont-app/igraph "0.1.4-SNAPSHOT"]
                 [ont-app/igraph-vocabulary "0.1.0-SNAPSHOT"] 
                 [ont-app/vocabulary "0.1.0-SNAPSHOT"]
                 ]
  :plugins [[lein-codox "0.10.6"]
            [lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-doo "0.1.11"]
            [lein-ancient "0.6.15"]
            ]

  :target-path "target/%s"
  :resource-paths ["resources" "target/cljsbuild"]
  
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :cljsbuild
  {
   :test-commands {"test" ["lein" "doo" "node" "test" "once"]}
   :builds
   {
    :dev {:source-paths ["src"]
           :compiler {
                      :main ont-app.graph-log.core 
                      :asset-path "js/compiled/out"
                      :output-to "resources/public/js/graph-log.js"
                      :source-map-timestamp true
                      :output-dir "resources/public/js/compiled/out"
                      :optimizations :none
                      }
          }
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
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  ]
                   :source-paths ["src"] 
                   :clean-targets
                   ^{:protect false}
                   ["resources/public/js/compiled"
                    "resources/test"
                    :target-path
                    ]
                   }
             }
  :repl-options {:init-ns ont-app.graph-log.core}
  )
