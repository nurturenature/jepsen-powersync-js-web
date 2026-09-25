(defproject ps-web "0.1.0-SNAPSHOT"
  :description "Testing PowerSync's JS Web SDK with Jepsen for Causal Consistency, Atomic transactions, and Strong Convergence."
  :url "https://github.com/powersync-ja/powersync-js"
  :license {:name "Apache License Version 2.0, January 2004"
            :url "http://www.apache.org/licenses/"}
  :dependencies [[org.clojure/clojure "1.12.6"]
                 [jepsen "0.3.14"]
                 [cheshire "6.2.0"]
                 [http-kit "2.8.1"]]
  :jvm-opts ["-Xmx8g"
             "-Djava.awt.headless=true"
             "-server"]
  :main ps-web.cli
  :repl-options {:init-ns ps-web.cli}
  :plugins [[lein-codox "0.10.8"]
            [lein-localrepo "0.5.4"]]
  :codox {:output-path "target/doc/"
          :source-uri "../{filepath}#L{line}"
          :metadata {:doc/format :markdown}})
