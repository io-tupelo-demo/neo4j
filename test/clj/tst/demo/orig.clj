(ns tst.demo.orig
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

; a neo4j connection map with the driver under `:db`
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

(dotest
  ; deleted users in DB from previous run
  (util/delete-all-nodes! driver)

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
  
  
  (is= [{:batches 1 :total 3}]  ; delete all users from DB 
       (util/delete-all-nodes! driver))

  )

