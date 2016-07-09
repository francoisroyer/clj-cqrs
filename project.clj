(defproject clj-cqrs "0.1.0-SNAPSHOT"
  :description "A library to build event sourcing systems."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.taoensso/timbre "4.1.4"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [com.stuartsierra/component "0.3.0"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.clojure/tools.cli "0.3.1"]
                 [com.rpl/specter "0.11.2"]
                 [clj-time "0.12.0"]
                 [prismatic/schema "1.0.3"]
                 [prismatic/plumbing "0.5.2"]
                 [org.clojure/data.fressian "0.2.1"]
                 [environ "1.0.0"]

                 [org.immutant/immutant "2.1.4"]
                 [org.immutant/wildfly "2.1.4"]
                 ;[io.undertow/undertow-core "1.3.15.Final"]
                 ;[org.keycloak/keycloak-undertow-adapter "1.9.7.Final"]

                 ;VIEWS
                 ;[org.apache.solr/solr-core "6.1.0" :exclusions [[com.fasterxml.jackson.core/jackson-core]
                 ;                                                [com.fasterxml.jackson.dataformat/jackson-dataformat-smile]]]
                 ;[org.apache.solr/solr-solrj "6.1.0"]

                 ;API
                 [metosin/compojure-api "1.1.3" :exclusions [ring/ring-core]]
                 [ring/ring-core "1.5.0"]
                 [buddy/buddy-auth "1.1.0"]
                 [jstrutz/hashids "1.0.1"]
                 ;[liberator "0.14.1"]
                 ;[cheshire "5.6.2"]

                 ;Database
                 ;[com.layerware/hugsql "0.4.7"]
                 ;[org.postgresql/postgresql "9.4.1207"]
                 ;[com.h2database/h2 "1.4.192"]
                 ]
  :plugins [[lein-immutant "2.1.0"]
            [lein-environ "1.0.1"]]
  :main clj-cqrs.core
  :aot [clj-cqrs.core]
  :source-paths ["src/clj"]
  ;:uberjar-name "demo-standalone.jar"
  :min-lein-version "2.4.0"
  ;:jvm-opts ["-Dhornetq.data.dir=target/hornetq-data"
  ;           "-Dcom.arjuna.ats.arjuna.objectstore.objectStoreDir=target/ObjectStore"]
  :profiles {:uberjar {:aot [clj-cqrs.core]}
             :dev {:source-paths ["dev" "src/clj"]
                   ;:java-source-paths ["src/java"]
                   :repl-options {:port 54806}
                   }}
  )
