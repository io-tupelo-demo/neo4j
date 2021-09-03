(ns neo4j-clj.core-test
  (:use tupelo.core tupelo.test)
  (:require
    [tupelo.neo4j :as neo4j]
    [tupelo.profile :as prof]
    ))

(def dummy-user
  {:name "MyTestUser" :role "Dummy" :age 42 :smokes true})

(def user-name-map
  {:name "MyTestUser"})

(def get-test-users-by-name-cmd
  "MATCH (u:TestUser {name: $name})
     RETURN u.name as name,
            u.role as role,
            u.age as age,
            u.smokes as smokes")

; Simple CRUD
(deftest create-get-delete-user
  (prof/with-timer-print :create-get-delete-user

    (neo4j/with-driver ; this is URI/username/password (not uri/db/pass!)
      "bolt://localhost:7687" "neo4j" "secret"

      (neo4j/with-session

        (testing "You can create a new user with neo4j"
          (neo4j/run   "CREATE (u:TestUser $params)-[:SELF {reason: \"to test\"}]->(u)"
            {:params dummy-user}))

        (testing "You can get a created user by name"
          (is (= (only (neo4j/run get-test-users-by-name-cmd user-name-map))
                dummy-user)))

        (testing "You can get a relationship"
          (is (= (first (neo4j/run "MATCH (u:TestUser {name: $name})-[s:SELF]->()
                                      RETURN collect(u) as ucoll, collect(s) as scoll"
                          user-name-map))
                {:ucoll [dummy-user]
                 :scoll (list {:reason "to test"})})))

        (testing "You can remove a user by name"
          (neo4j/run   "MATCH (u:TestUser {name: $name})   DETACH DELETE u" user-name-map))

        (testing "Removed users can't be retrieved"
          (is (= [] (neo4j/run   get-test-users-by-name-cmd user-name-map))))

        ))))

; Old (orig) tests.  Rewrite instead of adapting.
;(comment
;
;  #todo add to ns form
;  (:import [org.neo4j.driver.exceptions TransientException])
;  (require: [tupelo.neo4j.impl :as impl ] ...
;
;  ; Cypher exceptions
;  (deftest invalid-cypher-does-throw
;    (prof/with-timer-print :invalid-cypher-does-throw
;      (with-open [session (impl/get-session temp-db)]
;        (testing "An invalid cypher query does trigger an exception"
;          (is (thrown? Exception (impl/execute session "INVALID!!§$/%&/(")))))))
;
;  ; Transactions
;  (deftest transactions-do-commit
;    (prof/with-timer-print :transactions-do-commit
;      (testing "If using a transaction, writes are persistet"
;        (impl/with-transaction temp-db tx
;          (impl/execute tx "CREATE (x:test $t)" {:t {:payload 42}})))
;
;      (testing "If using a transaction, writes are persistet"
;        (impl/with-transaction temp-db tx
;          (is (= (impl/execute tx "MATCH (x:test) RETURN x")
;                '({:x {:payload 42}})))))
;
;      (testing "If using a transaction, writes are persistet"
;        (impl/with-transaction temp-db tx
;          (impl/execute tx "MATCH (x:test) DELETE x" {:t {:payload 42}})))
;
;      (testing "If using a transaction, writes are persistet"
;        (impl/with-transaction temp-db tx
;          (is (= (impl/execute tx "MATCH (x:test) RETURN x")
;                '()))))))
;
;  ; Retry
;  (deftest deadlocks-fail
;    (prof/with-timer-print :deadlocks-fail
;      (testing "When a deadlock occures,"
;        (testing "the transaction throws an Exception"
;          (is (thrown? TransientException
;                (impl/with-transaction temp-db tx
;                  (throw (TransientException. "" "I fail"))))))
;        (testing "the retried transaction works"
;          (let [fail-times (atom 3)]
;            (is (= :result
;                  (impl/with-retry [temp-db tx]
;                    (if (pos? @fail-times)
;                      (do (swap! fail-times dec)
;                          (throw (TransientException. "" "I fail")))
;                      :result))))))
;        (testing "the retried transaction throws after max retries"
;          (is (thrown? TransientException
;                (impl/with-retry [temp-db tx]
;                  (throw (TransientException. "" "I fail"))))))))))
