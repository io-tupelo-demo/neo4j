(defproject demo "0.1.0-SNAPSHOT"
  :dependencies      [
                      [gorillalabs/neo4j-clj "4.1.2"]
                      [org.clojure/clojure "1.10.3"]
                      [joplin.core "0.3.11"]
                      [prismatic/schema "1.1.12"]
                      [tupelo "21.07.08"]
                     ]
  :plugins           [
                      [com.jakemccrary/lein-test-refresh "0.24.1"]
                      [lein-ancient "0.7.0"]
                     ]

  :global-vars       {*warn-on-reflection* false}
  :main              ^:skip-aot demo.core

  :source-paths      ["src/clj"]
  :java-source-paths ["src/java"]
  :test-paths        ["test/clj"]
  :target-path       "target/%s"
  :compile-path      "%s/class-files"
  :clean-targets     [:target-path]

  :profiles          {:dev     {:dependencies []}
                      :uberjar {:aot :all}}

  :jvm-opts          ["-Xms500m" "-Xmx2g"]
)
