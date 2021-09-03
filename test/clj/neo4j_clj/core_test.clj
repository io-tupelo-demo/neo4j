(ns neo4j-clj.core-test
  (:use tupelo.core tupelo.test)
  (:require
    [tupelo.neo4j :as neo4j]
  ))

; Simple CRUD
(deftest create-get-delete-user
  (neo4j/with-driver ; this is URI/username/password (not uri/db/pass!)
    "bolt://localhost:7687" "neo4j" "secret"

    (neo4j/with-session
      (neo4j/drop-extraneous-dbs!) ; drop all but "system" and "neo4j" DB's
      (neo4j/run "create or replace database neo4j") ; drop/recreate default db

      (let [homer                      {:name "Homer Simpson" :role "Dummy" :age 42 :smokes false}
            get-test-users-by-name-cmd "MATCH (u:TestUser {name: $name})
                                           RETURN u.name as name,
                                                  u.role as role,
                                                  u.age as age,
                                                  u.smokes as smokes"]

        (testing "You can create a new user with neo4j"
          (neo4j/run "CREATE (u:TestUser $params)-[:SELF {reason: \"to test\"}]->(u)"
            {:params homer}))

        (testing "You can get a created user by name"
          (is (= (only (neo4j/run get-test-users-by-name-cmd {:name "Homer Simpson"}))
                homer)))

        (testing "You can get a relationship"
          (is (= (first (neo4j/run "MATCH (u:TestUser {name: $name})-[s:SELF]->()
                                    RETURN collect(u) as ucoll, collect(s) as scoll"
                          {:name "Homer Simpson"}))
                {:ucoll [homer]
                 :scoll (list {:reason "to test"})})))

        (testing "You can remove a user by name"
          (neo4j/run "MATCH (u:TestUser {name: $name})   DETACH DELETE u" {:name "Homer Simpson"}))

        (testing "Removed users can't be retrieved"
          (is (= [] (neo4j/run get-test-users-by-name-cmd {:name "Homer Simpson"}))))

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
;      (with-open [session (impl/get-session temp-db)]
;        (testing "An invalid cypher query does trigger an exception"
;          (is (thrown? Exception (impl/execute session "INVALID!!§$/%&/("))))))
;
;  ; Transactions
;  (deftest transactions-do-commit
;      (testing "If using a transaction, writes are persistet"
;        (impl/with-transaction temp-db tx
;          (impl/execute tx "CREATE (x:test $t)" {:t {:payload 42}}))
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
;                  (throw (TransientException. "" "I fail")))))))))
