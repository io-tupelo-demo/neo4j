(ns tst.demo.core
  (:use demo.core tupelo.core tupelo.test)
  (:require
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

(def dbconn
  (db/connect (URI. "bolt://localhost:7687") ; uri
    "neo4j"
    "secret")) ; user/pass

(def apoc-installed? false)

(def neo4j-version-cypher ; cypher command string used 2 ways
  "call dbms.components() yield name, versions, edition
   unwind versions as version
   return name, version, edition ;")
(db/defquery neo4j-version neo4j-version-cypher)
(db/defquery apoc-version "return apoc.version() as ApocVersion;")

(db/defquery delete-all-nodes-simple!  ; works, but could overflow jvm heap for large db's
  "match (n) detach delete n;")

(db/defquery delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
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

; Example usage of neo4j-clj
(dotest
  (spyx-pretty dbconn)
  (is (map? dbconn))
  (is-set= (keys dbconn)
    [:url ; java.net.URI "bolt://localhost:7687"
     :user ; "neo4j",
     :password ; "secret",
     :db  ;  org.neo4j.driver.internal.InternalDriver object
     :destroy-fn ; a clojure function
     ])

  ; Using a session
  (newline)
  (println "Creating session...")
  (with-open [session (db/get-session dbconn)]
    ; Since all unit tests are contained within the session `with-open`, the lazy results
    ; are automatically consumed via comparison operators. So we don't need to use either
    ; `(vec ...)` or `(unlazy ...)`

    (println "Getting Neo4j version info")
    (let [v1            (neo4j-version session) ; "db/defquery" access to DB
          v2            (db/execute session neo4j-version-cypher) ; "db/execute" produces same result
          vers-info     (only v1)
          neo4j-version (:version vers-info)]
      (is= v1 v2)
      (is= (:name vers-info) "Neo4j Kernel")
      (is (str/increasing-or-equal? "4.0" neo4j-version))
      (is= (:edition vers-info) "enterprise"))

    (try
      (println "Getting APOC version info")
      (let [apoc-version (apoc-version session)]
        (println "  found APOC library")
        (let [apoc-version (grab :ApocVersion (only apoc-version))]
          (is (str/increasing-or-equal? "4.0" apoc-version)))

        (def apoc-installed? true)) ; no exception, so set to true
      (catch Exception <>
        (println "  *** APOC not installed ***")))

    (if apoc-installed?
      (is= [{:batches 1 :total 3}] ; deleted users in DB from previous run
        (delete-all-nodes-apoc! session))
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
                 ; result is consumed outside of TX, so we must realize lazy output
                 ; via `unlazy`, `vec`, or `doall`
                 (unlazy (get-all-users tx)))]
    (is= result
      [{:UZZER {:first-name "Luke" :last-name "Skywalker"}}
       {:UZZER {:first-name "Leia" :last-name "Organa"}}
       {:UZZER {:first-name "Anakin" :last-name "Skywalker"}}]))
  )
