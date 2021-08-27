(ns tst.tupelo.neo4j
  (:use tupelo.core tupelo.test)
  (:require
    [schema.core :as s]
    [tupelo.neo4j :as neo4j]
    [tupelo.set :as set]
    [tupelo.string :as str]
    ))

(s/defn db-names-all :- [s/Str]
  []
  (mapv #(grab :name %) (neo4j/session-run "show databases")))

(s/defn drop-extraneous-dbs! :- [s/Str]
  []
  (let [keep-db-names #{"system" "neo4j"} ; never delete these DBs!
        drop-db-names (set/difference (set (db-names-all)) keep-db-names)]
    (doseq [db-name drop-db-names]
      (neo4j/session-run (format "drop database %s if exists" db-name)))))

(dotest-focus
  (neo4j/with-driver "bolt://localhost:7687" "neo4j" "secret" ; url/username/password
    (neo4j/with-session

      (let [vinfo (neo4j/neo4j-info)]
        ; example:  {:name "Neo4j Kernel", :version "4.2-aura", :edition "enterprise"}
        (with-map-vals vinfo [name version edition]
          (is= name "Neo4j Kernel")
          (is= edition "enterprise")
          (is (str/increasing-or-equal? "4.2" version))))
      (is (neo4j/apoc-installed?))
      (is (str/increasing-or-equal? "4.2" (neo4j/apoc-version)))

      (is= (neo4j/neo4j-info) {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})
      (is= (neo4j/neo4j-version) "4.3.3")
      (is= (neo4j/apoc-version) "4.3.0.0"))

    (neo4j/with-session
      (drop-extraneous-dbs!)

      ; "system" db is always present
      ; "neo4j" db is default name
      (is-set= (db-names-all) #{"system" "neo4j"})

      (neo4j/session-run "create or replace database neo4j")
      (neo4j/session-run "create or replace database SPRINGFIELD") ; make a new DB
      (is-set= (db-names-all) #{"system" "neo4j" "springfield"}) ; NOTE:  all lowercase

      ; use default db "neo4j"
      (neo4j/session-run "CREATE (u:Jedi $Hero)  return u as padawan"
        {:Hero {:first-name "Luke" :last-name "Skywalker"}})
      (is= (vec (neo4j/session-run "match (n) return n as Jedi "))
        [{:Jedi {:first-name "Luke", :last-name "Skywalker"}}])

      ; use "springfield" db (always coerced to lowercase)
      (is= (only
             (neo4j/session-run
               "use Springfield
                create (u:user $Resident)  return u as Duffer"
               {:Resident {:first-name "Homer" :last-name "Simpson"}}))
        {:Duffer {:first-name "Homer", :last-name "Simpson"}})

      (is= (vec (neo4j/session-run "use Springfield
                                    match (n) return n as Dummy"))
        [{:Dummy {:first-name "Homer", :last-name "Simpson"}}])
      (neo4j/session-run "drop database SPRINGFIELD if exists"))

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
      (println :version (neo4j/neo4j-info *neo4j-conn-map*))
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



