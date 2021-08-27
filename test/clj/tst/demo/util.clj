(ns tst.demo.util
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [tupelo.string :as str]
    ))

(dotest   ; -focus
  (util/with-driver "bolt://localhost:7687" "neo4j" "secret"
    (util/with-session

      (let [vinfo (util/neo4j-info)]
        ; example:  {:name "Neo4j Kernel", :version "4.2-aura", :edition "enterprise"}
        (with-map-vals vinfo [name version edition]
          (is= name "Neo4j Kernel")
          (is= edition "enterprise")
          (is (str/increasing-or-equal? "4.2" version))))
      (is (util/apoc-installed?))
      (is (str/increasing-or-equal? "4.2" (util/apoc-version)))

      (is= (util/neo4j-info) {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})
      (is= (util/neo4j-version) "4.3.3")
      (is= (util/apoc-version) "4.3.0.0"))

    ))

;-----------------------------------------------------------------------------
#_(dotest
    (spy-pretty :impl
                (with-connection-impl '[
                                  (URI. "bolt://localhost:7687") "neo4j" "secret"
                                  (form1 *neo4j-conn-map*)
                                  (form2)
                                 ]))
  )

#_(dotest
    (with-connection "bolt://localhost:7687" "neo4j"
               "secret"
                 ; (println :aa NEOCONN )
                 (println :version (util/neo4j-info *neo4j-conn-map*))
               ; (println :zz NEOCONN )
    ))

;-----------------------------------------------------------------------------

#_(dotest-focus
    (spy-pretty :impl
                (with-session-impl '[
                                     (form1 *neo4j-session*)
                                     (form2)
                                    ])))

#_(dotest-focus
    (with-connection "bolt://localhost:7687" "neo4j"
               "secret"
                 ; (println :use-00 NEOCONN )
                 (with-session
                   ; (println :use-aa SESSION )
                   (newline)
                   (println :use-version (get-vers))
                   (newline)
                   (flush)
                   ; (println :use-zz SESSION )
                 )
               ; (println :use-99 NEOCONN )
    ))



