(ns tst.demo.util
  (:use demo.util tupelo.core tupelo.test)
  (:require
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

;-----------------------------------------------------------------------------
#_(dotest
    (spy-pretty :impl
                (with-conn-impl '[
                                  (URI. "bolt://localhost:7687") "neo4j" "secret"
                                  (form1 NEOCONN)
                                  (form2)
                                 ]))
  )

#_(dotest
    (with-connection "bolt://localhost:7687" "neo4j"
               "secret"
                 ; (println :aa NEOCONN )
                 (println :version (util/neo4j-version NEOCONN))
               ; (println :zz NEOCONN )
    ))

;-----------------------------------------------------------------------------

#_(dotest-focus
    (spy-pretty :impl
                (with-session-impl '[
                                     (form1 SESSION)
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



