(ns tst.demo.core
  (:use tupelo.core tupelo.test)
  (:require
    [tupelo.neo4j :as neo4j]
    [tupelo.set :as set]
    [tupelo.string :as str]
    ))

(dotest   ; -focus
  (neo4j/with-driver  ; this is URI/username/password (not uri/db/pass!)
    "bolt://localhost:7687" "neo4j" "secret"
    ; "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    ; Using a session
    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!)
      (neo4j/session-run "create or replace database neo4j") ; drop/recreate default db

      (is= (neo4j/neo4j-info) {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})
      (is= (neo4j/neo4j-version) "4.3.3")
      (is= (neo4j/apoc-version) "4.3.0.0")


      (is (neo4j/apoc-installed?))

      ; tests consume all output within the session lifetime, so don't need `doall`, `vec`, or `unlazy`
      (let [create-user-cmd "CREATE (u:User $User)  return u as newb"]
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Luke" :last-name "Skywalker"}})
          [{:newb {:first-name "Luke" :last-name "Skywalker"}}])
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Leia" :last-name "Organa"}})
          [{:newb {:first-name "Leia" :last-name "Organa"}}])
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Anakin" :last-name "Skywalker"}})
          [{:newb {:first-name "Anakin" :last-name "Skywalker"}}])
        (is= 3 (count (neo4j/nodes-all))))

      (is= (vec (neo4j/session-run "MATCH (u:User)  RETURN u as Jedi"))
        [{:Jedi {:first-name "Luke" :last-name "Skywalker"}}
         {:Jedi {:first-name "Leia" :last-name "Organa"}}
         {:Jedi {:first-name "Anakin" :last-name "Skywalker"}}])

      (is= (neo4j/delete-all-nodes!) ; delete all users from DB via APOC
        [{:batches 1 :total 3}]))

    ))
