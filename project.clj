(defproject lilypad-proto "1.0.1"
  :description "Hello World Clojure Web App"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 ;[org.clojure/clojurescript "1.8.40"]
                 ]
  :main ^:skip-aot lilypad-proto.app
  :profiles {:uberjar {:aot :all}})
