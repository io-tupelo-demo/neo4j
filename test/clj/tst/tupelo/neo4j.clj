(ns tst.tupelo.neo4j
  (:use tupelo.core tupelo.test)
  (:require
    [schema.core :as s]
    [tupelo.neo4j :as neo4j]
    [tupelo.set :as set]
    [tupelo.string :as str]
    ))

(dotest   ; -focus
  (neo4j/with-driver "bolt://localhost:7687" "neo4j" "secret" ; url/username/password
    (neo4j/with-session

      (let [vinfo (neo4j/info-map)]
        ; example:  {:name "Neo4j Kernel" :version "4.2-aura" :edition "enterprise"}
        ; example:  {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"}
        (with-map-vals vinfo [name version edition]
          (is= name "Neo4j Kernel")
          (is= edition "enterprise")
          (is (str/increasing-or-equal? "4.2" version))))
      (is (str/increasing-or-equal? "4.2" (neo4j/neo4j-version)))
      (is (neo4j/apoc-installed?))
      (is (str/increasing-or-equal? "4.2" (neo4j/apoc-version))))

    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!)

      ; "system" db is always present
      ; "neo4j" db is default name
      (is-set= (neo4j/db-names-all) #{"system" "neo4j"})

      (neo4j/run "create or replace database neo4j") ; drop/recreate default db
      (neo4j/run "create or replace database SPRINGFIELD") ; make a new DB
      ; NOTE: For some reason cannot get `.` (dot) to work in name. Underscore `_` is illegal for DB name

      (is-set= (neo4j/db-names-all) #{"system" "neo4j" "springfield"}) ; NOTE:  all lowercase

      ; use default db "neo4j"
      (neo4j/run "CREATE (u:Jedi $Hero)  return u as padawan"
        {:Hero {:first-name "Luke" :last-name "Skywalker"}})
      (is= (vec (neo4j/run "match (n) return n as Jedi "))
        [{:Jedi {:first-name "Luke", :last-name "Skywalker"}}])

      ; use "springfield" db (always coerced to lowercase by neo4j)
      (is= (only
             (neo4j/run "use Springfield
                                 create (p:Person $Resident) return p as Duffer"
               {:Resident {:first-name "Homer" :last-name "Simpson"}}))
        {:Duffer {:first-name "Homer", :last-name "Simpson"}})

      (is= (vec (neo4j/run "use SPRINGFIELD
                                    match (n) return n as Dummy"))
        [{:Dummy {:first-name "Homer", :last-name "Simpson"}}])

      (neo4j/run "drop database SpringField if exists"))
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
      (println :version (neo4j/info-map *neo4j-conn-map*))
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



