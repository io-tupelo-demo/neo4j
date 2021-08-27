(ns tst.demo.indexes
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [tupelo.string :as str]))

(defn delete-all-movies! ; works, but could overflow jvm heap for large db's
  []
  (unlazy (util/session-run "match (m:Movie) detach delete m;")))

(defn create-movie
  [arg]
  (unlazy (util/session-run "CREATE (m:Movie $Data)
                              return m as film" arg)))

(defn get-all-movies
  []
  (unlazy (util/session-run "MATCH (m:Movie) RETURN m as flick")))

(dotest-focus

  (util/with-connection
    ; "bolt://localhost:7687" "neo4j" "secret"
    "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    (comment ; example
      (newline) (spyx-pretty util/*neo4j-conn-map*)
      ; {:url        #object[java.net.URI 0x1d4d84fb "neo4j+s://4ca9bb9b.databases.neo4j.io"],
      ;  :user       "neo4j",
      ;  :password   "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY",
      ;  :db         #object[org.neo4j.driver.internal.InternalDriver 0x59e97f75 "org.neo4j.driver.internal.InternalDriver@59e97f75"],
      ;  :destroy-fn #object[neo4j_clj.core$connect$fn__19085 0x1d696b35 "neo4j_clj.core$connect$fn__19085@1d696b35"]}

      )

    (util/with-session
      (comment ; example
        (newline) (spyx-pretty util/*neo4j-session*)
        ; #object[org.neo4j.driver.internal.InternalSession 0x2eba393 "org.neo4j.driver.internal.InternalSession@2eba393"]
        )

      (util/delete-all-nodes!)
      (util/constraints-drop-all!)
      (util/indexes-drop-all!)

      (is= 0 (count (util/nodes-all)))

      ; note return type :film set by "return ... as ..."
      (is= (create-movie {:Data {:title "The Matrix"}}) [{:film {:title "The Matrix"}}])
      (is= (create-movie {:Data {:title "Star Wars"}}) [{:film {:title "Star Wars"}}])
      (is= (create-movie {:Data {:title "Raiders"}}) [{:film {:title "Raiders"}}])
      (is= 3 (count (spyx-pretty (util/nodes-all))))

      ; note return type :flick set by "return ... as ..."
      (is-set= (get-all-movies)
        [{:flick {:title "The Matrix"}}
         {:flick {:title "Star Wars"}}
         {:flick {:title "Raiders"}}])

      (is= [] (util/session-run "drop constraint cnstrUniqueMovieTitle if exists ;"))
      (is= [] (util/session-run "drop constraint cnstrxUniqueMovieTitle if exists ;"))

      (is= [] (util/session-run "create constraint  cnstrxUniqueMovieTitle  if not exists
                                   on (m:Movie) assert m.title is unique;"))
      (let [indexes  (util/session-run "show indexes;")
            >> (spyx-pretty  indexes)
            constraints (vec (keep-if #(str/contains-str? (grab :name %) "Unique")
                           indexes))
            ]
        (spyx constraints)
        (is (wild-match?
              '{:id            :*
                :name          "cnstrxUniqueMovieTitle"
                :type          "UNIQUENESS"
                :entityType    "NODE"
                :labelsOrTypes ("Movie")
                :properties    ("title")
                :ownedIndexId  :*}
              constraints)))

      ; (throws? (create-movie {:Data {:title "Raiders"}})) ; duplicate title

      ;(is= []
      ;  (util/session-run
      ;    "create index  idxMovieTitle  if not exists
      ;                   for (m:Movie) on (m.title);"))
      (comment
        ; Sometimes (neo4j linux!) extraneous indexes are also returned
        ; We need to filter them out before performing the test
        {:properties        nil,
         :populationPercent 100.0,
         :name
                            "__org_neo4j_schema_index_label_scan_store_converted_to_token_index",
         :type              "LOOKUP",
         :state             "ONLINE",
         :uniqueness        "NONUNIQUE",
         :id                1,
         :indexProvider     "token-lookup-1.0",
         :entityType        "NODE",
         :labelsOrTypes     nil})
      (let [idxs-found (vec (keep-if #(= (grab :name %) "cnstrxUniqueMovieTitle")
                              (util/session-run "show indexes;")))
            >>         (spyx idxs-found)
            idx-ours   (only idxs-found)] ; there should be only 1
        (is (wild-match?
              {:entityType        "NODE"
               :id                :*
               :indexProvider     "native-btree-1.0"
               :labelsOrTypes     ["Movie"]
               :name              "cnstrxUniqueMovieTitle"
               :populationPercent 100.0
               :properties        ["title"]
               :state             "ONLINE"
               :type              "BTREE"
               :uniqueness        "UNIQUE"}
              idx-ours)))

      )))

