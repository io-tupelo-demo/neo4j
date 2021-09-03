(ns neo4j-clj.core-test
  (:use tupelo.core)
  (:require
    [clojure.test :refer :all]
    ; [tupelo.neo4j-impl :as neolib ]
    [tupelo.neo4j :as neo4j]
    [tupelo.profile :as prof]
    )
  (:import
    [org.neo4j.driver.exceptions TransientException]
    ))

(def get-test-users-by-name-cmd
  "MATCH (u:TestUser {name: $name}) RETURN u.name as name, u.role as role, u.age as age, u.smokes as smokes")

(def dummy-user
  {:name "MyTestUser" :role "Dummy" :age 42 :smokes true})

(def name-lookup
  {:name (:name dummy-user)})

(defn with-temp-db
  [tests]
  (prof/with-timer-print :with-temp-db
    (tests)
    ))

(use-fixtures :once with-temp-db)

; Simple CRUD
(deftest create-get-delete-user
  (prof/with-timer-print :create-get-delete-user

    (neo4j/with-driver ; this is URI/username/password (not uri/db/pass!)
      "bolt://localhost:7687" "neo4j" "secret"

      (neo4j/with-session

        (testing "You can create a new user with neo4j"
          (neo4j/run   "CREATE (u:TestUser $user)-[:SELF {reason: \"to test\"}]->(u)"
            {:user dummy-user}))

        (testing "You can get a created user by name"
          (is (= (neo4j/run   get-test-users-by-name-cmd name-lookup)
                (list dummy-user))))

        (testing "You can get a relationship"
          (is (= (first (neo4j/run   "MATCH (u:TestUser {name: $name})-[s:SELF]->() RETURN collect(u) as ucoll, collect(s) as scoll"
                          name-lookup))
                {:ucoll (list dummy-user) :scoll (list {:reason "to test"})})))

        (testing "You can remove a user by name"
          (neo4j/run   "MATCH (u:TestUser {name: $name}) DETACH DELETE u" name-lookup))

        (testing "Removed users can't be retrieved"
          (is (= [] (neo4j/run   get-test-users-by-name-cmd
                      name-lookup))))

        ))))

; Old (orig) tests.  Rewrite instead of adapting.
;(comment
;
;  ; Cypher exceptions
;  (deftest invalid-cypher-does-throw
;    (prof/with-timer-print :invalid-cypher-does-throw
;      (with-open [session (neolib/get-session temp-db)]
;        (testing "An invalid cypher query does trigger an exception"
;          (is (thrown? Exception (neolib/execute session "INVALID!!§$/%&/(")))))))
;
;  ; Transactions
;  (deftest transactions-do-commit
;    (prof/with-timer-print :transactions-do-commit
;      (testing "If using a transaction, writes are persistet"
;        (neolib/with-transaction temp-db tx
;          (neolib/execute tx "CREATE (x:test $t)" {:t {:payload 42}})))
;
;      (testing "If using a transaction, writes are persistet"
;        (neolib/with-transaction temp-db tx
;          (is (= (neolib/execute tx "MATCH (x:test) RETURN x")
;                '({:x {:payload 42}})))))
;
;      (testing "If using a transaction, writes are persistet"
;        (neolib/with-transaction temp-db tx
;          (neolib/execute tx "MATCH (x:test) DELETE x" {:t {:payload 42}})))
;
;      (testing "If using a transaction, writes are persistet"
;        (neolib/with-transaction temp-db tx
;          (is (= (neolib/execute tx "MATCH (x:test) RETURN x")
;                '()))))))
;
;  ; Retry
;  (deftest deadlocks-fail
;    (prof/with-timer-print :deadlocks-fail
;      (testing "When a deadlock occures,"
;        (testing "the transaction throws an Exception"
;          (is (thrown? TransientException
;                (neolib/with-transaction temp-db tx
;                  (throw (TransientException. "" "I fail"))))))
;        (testing "the retried transaction works"
;          (let [fail-times (atom 3)]
;            (is (= :result
;                  (neolib/with-retry [temp-db tx]
;                    (if (pos? @fail-times)
;                      (do (swap! fail-times dec)
;                          (throw (TransientException. "" "I fail")))
;                      :result))))))
;        (testing "the retried transaction throws after max retries"
;          (is (thrown? TransientException
;                (neolib/with-retry [temp-db tx]
;                  (throw (TransientException. "" "I fail"))))))))))
