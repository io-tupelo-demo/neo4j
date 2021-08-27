(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require
    [neo4j-clj.core :as db]
    [tupelo.neo4j :as neo4j]
    [tupelo.string :as str])
  (:import
    [java.net URI]))

(dotest   ; -focus
  (neo4j/with-driver  ; this is URI/username/password (not uri/db/pass!)
    "bolt://localhost:7687" "neo4j" "secret"
    ; "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    ; Using a session
    (neo4j/with-session

      (is= (neo4j/neo4j-info) {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})
      (is= (neo4j/neo4j-version) "4.3.3")
      (is= (neo4j/apoc-version) "4.3.0.0")

      ; deleted users in DB from previous run
      (neo4j/delete-all-nodes!)

      (is (neo4j/apoc-installed?))

      ; tests consume all output within the session lifetime, so don't need `doall`, `vec`, or `unlazy`
      (let [create-user-cmd "CREATE (u:User $User)  return u as newb"]
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Luke" :last-name "Skywalker"}})
          [{:newb {:first-name "Luke" :last-name "Skywalker"}}])
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Leia" :last-name "Organa"}})
          [{:newb {:first-name "Leia" :last-name "Organa"}}])
        (is= (neo4j/session-run create-user-cmd {:User {:first-name "Anakin" :last-name "Skywalker"}})
          [{:newb {:first-name "Anakin" :last-name "Skywalker"}}])
        (is= 3 (count (neo4j/nodes-all)))))

    (comment
      ; Using a transaction
      (let [get-all-users-cmd "MATCH (u:User)  RETURN u as UZZER"
            result            (neo4j/with-session
                                (unlazy ; or vec/doall to realize output within session life
                                  (neo4j/session-run get-all-users-cmd)))]
        (is= result
          [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
           {:UZZER {:first-name "Leia" :last-name "Organa"}}
           {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))

      (is= [{:batches 1 :total 3}] ; delete all users from DB via APOC
        (neo4j/delete-all-nodes! )))

    ))
