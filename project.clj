(defproject clj-cqrs "0.1.0-SNAPSHOT"
  :description "A library to build event sourcing systems."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [com.taoensso/timbre "4.7.2"]
                 [com.taoensso/encore "2.67.1"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.cli "0.3.1"]
                 [org.clojure/tools.reader "0.10.0"]
                 [com.rpl/specter "0.11.2"]
                 [clj-time "0.12.0"]
                 [prismatic/schema "1.0.3"]
                 [prismatic/plumbing "0.5.2"]
                 [org.clojure/data.fressian "0.2.1"]
                 [com.damballa/abracad "0.4.13"]
                 [environ "1.0.0"]

                 ;[amazonica "0.3.73"]
                 [org.immutant/immutant "2.1.6"]
                 [org.immutant/wildfly "2.1.6"]
                 ;[io.undertow/undertow-core "1.3.15.Final"] ;TODO fix dep on Jackson
                 ;[org.keycloak/keycloak-undertow-adapter "1.9.7.Final"]
                 [org.keycloak/keycloak-undertow-adapter "2.0.0.Final"]

                 ;API
                 [io.sarnowski/swagger1st "0.21.0"]
                 [metosin/compojure-api "1.1.5" :exclusions [ring/ring-core]]
                 [ring/ring-core "1.5.1" :exclusion [org.clojure/tools.reader]]
                 [ring/ring-defaults "0.2.1"]
                 [org.clojure/tools.reader "0.10.0"]
                 [org.webjars/webjars-locator "0.27"]
                 ;[buddy/buddy-auth "1.1.0"]
                 [jstrutz/hashids "1.0.1"]
                 ;[liberator "0.14.1"]
                 ;[cheshire "5.6.2"]

                 ;UI
                 [org.webjars.bower/adminlte "2.3.3"]

                 ;Database
                 [korma "0.4.0"]
                 [aggregate "1.1.3"]
                 ;[org.postgresql/postgresql "9.4.1207"]
                 [com.h2database/h2 "1.4.192"]

                 ;Front-end
                 [org.clojure/clojurescript "1.8.51"]
                 [enlive "1.1.6"]
                 [com.taoensso/sente "1.10.0"]
                 [datascript "0.15.2"]
                 [reagent "0.6.0-rc" :exclusions [cljsjs/react]]
                 [cljsjs/react-with-addons "15.1.0-0"]
                 [re-frame "0.7.0"]
                 [kioo "0.5.0-SNAPSHOT"]
                 [secretary "1.2.3"]
                 [cljs-ajax "0.5.8"]
                 [cljsjs/pdfjs "1.5.188-0"]
                 [cljsjs/vis "4.16.1-0"]
                 [cljsjs/leaflet "0.7.7-4"]
                 [cljsjs/react-grid-layout "0.12.7-0"]]
                 ;[cljsjs/codemirror "5.11.0-2"]

  :source-paths ["src/clj" "src/cljs"]
  ;:uberjar-name "demo-standalone.jar"
  :min-lein-version "2.5.1"
  ;:jvm-opts ["-Dhornetq.data.dir=target/hornetq-data"
  ;           "-Dcom.arjuna.ats.arjuna.objectstore.objectStoreDir=target/ObjectStore"]
  :profiles {:uberjar {:main cqrs.system
                       :aot [com.stuartsierra.dependency
                             com.stuartsierra.component
                             cqrs.system
                             cqrs.api]}

             :dev {:source-paths ["dev" "src/clj"]
                   :plugins [[lein-immutant "2.1.0"]
                             [lein-environ "1.0.1"]
                             [lein-cljsbuild "1.1.3"]
                             [lein-figwheel "0.5.4-7"]]

                   :dependencies [[ns-tracker "0.3.0"]
                                  ;[org.apache.solr/solr-core "6.1.0" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                                  ;                                                [com.fasterxml.jackson.dataformat/jackson-dataformat-smile]]]
                                  ;[org.apache.solr/solr-solrj "6.1.0"]
                                  ;[clojurewerkz/elastisch "3.0.0-beta1"]
                                  ;[org.elasticsearch/elasticsearch "2.3.3"]
                                  [com.h2database/h2 "1.4.192"]
                                  [org.elasticsearch/elasticsearch "1.7.3"]
                                  [clojurewerkz/elastisch "2.2.2"]
                                  [org.onyxplatform/onyx "0.9.15"]]

                   ;:java-source-paths ["src/java"]
                   :repl-options {:port 54806}}}

  :cljsbuild {:builds [ {:id "dev" ;lein figwheel dev
                         :source-paths ["src/cljs"]
                         :figwheel {:on-jsload cqrs.core/reload} ;true
                         :compiler {:output-to     "resources/public/app/js/dev.js"
                                    :output-dir    "resources/public/app/js/dev"
                                    :main cqrs.core
                                    ;;:externs       ["react/externs/react.js"]
                                    :asset-path   "js/dev"
                                    :optimizations :none
                                    :pretty-print  true}}
                        {:id "prod"  ;lein cljsbuild once prod
                         :source-paths ["src/cljs"]
                         :compiler {:output-to     "resources/public/app/js/prod.js"
                                    :output-dir    "resources/public/app/js/prod"
                                    :main cqrs.core
                                    ;;:externs       ["react/externs/react.js"]
                                    :optimizations :advanced
                                    :pretty-print  true}}]}

  :aliases {"node1"
            ["immutant" "run" "--clustered"
             "-Djboss.node.name=node1"
             "-Djboss.server.data.dir=/tmp/node1"]
            "node2"
            ["immutant" "run" "--clustered"
             "-Djboss.node.name=node2"
             "-Djboss.server.data.dir=/tmp/two"
             "-Djboss.socket.binding.port-offset=100"]})
