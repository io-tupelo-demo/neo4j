(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

(def dbconn
  (db/connect (URI. "bolt://localhost:7687")  ; uri
              "neo4j"
              "secret")); user/pass

(def apoc-installed? false )

(db/defquery neo4j-version "call dbms.components() yield name, versions, edition 
                            unwind versions as version 
                            return name, version, edition ;")

(db/defquery apoc-version "return apoc.version() as ApocVersion;")

; works, but could overflow jvm heap for large db's
(db/defquery delete-all-nodes-simple!
             "match (n) detach delete n;")

(db/defquery
  delete-all-nodes-apoc!
  (str/quotes->double
    "call apoc.periodic.iterate( 'MATCH (n)  return n', 
                                 'DETACH DELETE n',
                                 {batchSize:1000} )
     yield  batches, total 
     return batches, total"))

(db/defquery create-user ; creates a global Var function, taking a session or tx as 1st arg
             "CREATE (u:User $User) 
              return u as newb")

(db/defquery get-all-users ; creates a global Var function, taking a session or tx as 1st arg
             "MATCH (u:User) 
              RETURN u as UZZER")

(dotest-focus
  ; Example usage of neo4j-clj

  ; Using a session
  (with-open [session (db/get-session dbconn)]
    (spyx (neo4j-version session))

    (try
      (let [apoc-version (unlazy (apoc-version session))]
        (println "found APOC library")
        (is= apoc-version [{:ApocVersion "4.2.0.0"}])
        (def apoc-installed? true))
      (catch Exception <>
        (println "*** APOC not installed ***")))

    (if apoc-installed?
      (is= [{:batches 1 :total 3}] ; deleted users in DB from previous run
        (vec ; consume all lazy input
          (delete-all-nodes-apoc! session)))
      (delete-all-nodes-simple! session))

    (is= [{:newb {:first-name "Luke" :last-name "Skywalker"}}]
         (create-user session {:User {:first-name "Luke" :last-name "Skywalker"}}))
    (is= [{:newb {:first-name "Leia" :last-name "Organa"}}]
         (create-user session {:User {:first-name "Leia" :last-name "Organa"}}))
    (is= [{:newb {:first-name "Anakin" :last-name "Skywalker"}}]
         (create-user session {:User {:first-name "Anakin" :last-name "Skywalker"}}))
  )

  ; Using a transaction
  (let [result (db/with-transaction dbconn tx
                                    (unlazy ; or `vec`
                                      (get-all-users tx)))]
    (is= result
         [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
          {:UZZER {:first-name "Leia" :last-name "Organa"}}
          {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))
)
