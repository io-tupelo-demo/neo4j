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
              return u as newb")

(db/defquery get-all-users ; creates a global Var function, taking a session or tx as 1st arg
             "MATCH (u:User) 
              RETURN u as UZZER")

(dotest-focus
  (util/with-connection
    "bolt://localhost:7687" "neo4j" "secret"
    ; "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    (is= (only (util/neo4j-version))
      {:name "Neo4j Kernel" :version "4.3.3" :edition "enterprise"})

      ; deleted users in DB from previous run
      (util/delete-all-nodes! )

      (try
        (let [vers-str (only (unlazy (util/apoc-version )))]
          ; (println "found APOC library")
          (is= vers-str {:ApocVersion "4.3.0.0"}))
        (catch Exception <>
          (println "*** APOC not installed ***")))

      (is (util/apoc-installed?))

      ; Using a session
      (with-open [session (db/get-session driver)]
        ; tests consume all output within the session lifetime
        (is= [{:newb {:first-name "Luke" :last-name "Skywalker"}}]
          (create-user session {:User {:first-name "Luke" :last-name "Skywalker"}}))
        (is= [{:newb {:first-name "Leia" :last-name "Organa"}}]
          (create-user session {:User {:first-name "Leia" :last-name "Organa"}}))
        (is= [{:newb {:first-name "Anakin" :last-name "Skywalker"}}]
          (create-user session {:User {:first-name "Anakin" :last-name "Skywalker"}}))
        )

    (comment

      ; Using a transaction
      (let [result (db/with-transaction driver
                     tx
                     ; or vec/doall to realize output within tx life
                     (unlazy
                       (get-all-users tx)))]
        (is= result
          [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
           {:UZZER {:first-name "Leia" :last-name "Organa"}}
           {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))


      (is= [{:batches 1 :total 3}] ; delete all users from DB
        (util/delete-all-nodes! driver)))

    ))

