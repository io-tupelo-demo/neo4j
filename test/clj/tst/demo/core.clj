(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

(def driver
  (db/connect (URI. "bolt://localhost:7687")  ; uri
              "neo4j"
              "secret")); user/pass

(db/defquery create-user ; creates a global Var function, taking a session or tx as 1st arg
             "CREATE (u:User $User) 
              return u as newb"
  )

(db/defquery get-all-users ; creates a global Var function, taking a session or tx as 1st arg
             "MATCH (u:User) 
              RETURN u as UZZER")

(dotest-focus
  (util/with-connection
    "bolt://localhost:7687" "neo4j" "secret"
    ; "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    (is= (util/neo4j-info) {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})
    (is= (util/neo4j-version)  "4.3.3")
    (is= (util/apoc-version) "4.3.0.0")

    ; deleted users in DB from previous run
      (util/delete-all-nodes! )

      (is (util/apoc-installed?))

      ; Using a session
    (util/with-session
      ; tests consume all output within the session lifetime, so don't need `doall`, `vec`, or `unlazy`
      (let [create-user-cypher "CREATE (u:User $User)
                                return u as newb"]
        (is= (util/session-run create-user-cypher {:User {:first-name "Luke" :last-name "Skywalker"}})
          [{:newb {:first-name "Luke" :last-name "Skywalker"}}])
        (is= (util/session-run create-user-cypher {:User {:first-name "Leia" :last-name "Organa"}})
          [{:newb {:first-name "Leia" :last-name "Organa"}}])
        (is= (util/session-run create-user-cypher {:User {:first-name "Anakin" :last-name "Skywalker"}})
          [{:newb {:first-name "Anakin" :last-name "Skywalker"}}])

        (is= 3 (count (util/all-nodes)))))

    (comment
      ; Using a transaction
      (let [get-all-users-cmd
                   "MATCH (u:User)
                    RETURN u as UZZER"
            result (db/with-transaction driver
                     tx
                     ; or vec/doall to realize output within tx life
                     (unlazy
                       (util/session-run  get-all-users-cmd)
                       ))]
        (is= result
          [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
           {:UZZER {:first-name "Leia" :last-name "Organa"}}
           {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))


      (is= [{:batches 1 :total 3}] ; delete all users from DB
        (util/delete-all-nodes! driver)))

    ))

