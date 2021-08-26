(ns tst.demo.indexes
  (:use tupelo.core
        tupelo.test)
  (:require
    [demo.util :as util]
    [tupelo.string :as str])
  )

(defn delete-all-movies! ; works, but could overflow jvm heap for large db's
  []
  (unlazy (util/exec-session "match (m:Movie) detach delete m;")))

(defn create-movie
  [arg]
  (unlazy (util/exec-session
            "CREATE (m:Movie $Data) 
            return m as film"
            arg)))

(defn get-all-movies
  []
  (unlazy (util/exec-session
            "MATCH (m:Movie) 
            RETURN m as flick")))

(dotest   ; -focus
  (util/with-connection
    ; "bolt://localhost:7687" "neo4j" "secret"
    "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

    (newline)
    (spyx (util/neo4j-version))

    (util/with-session
      (newline)
      (spyx-pretty util/NEOCONN)

      (newline)
      (delete-all-movies!)

      (spyx (create-movie {:Data {:title "The Matrix"}}))
      (spyx (create-movie {:Data {:title "Star Wars"}}))
      (spyx (create-movie {:Data {:title "Raiders"}}))
      (newline)
      (spyx-pretty (get-all-movies))
      (newline)
      (flush)

      (is= [] (util/exec-session "drop constraint cnstrUniqueMovieTitle if exists ;"))
      (is= []
        (util/exec-session
          "create constraint  cnstrUniqueMovieTitle  if not exists
                         on (m:Movie) assert m.title is unique;"))
      (let [result (only (util/exec-session "show constraints  ;"))]
        (spyx result)
        (is (wild-match?
              '{:id            :*
                :name          "cnstrUniqueMovieTitle"
                :type          "UNIQUENESS"
                :entityType    "NODE"
                :labelsOrTypes ("Movie")
                :properties    ("title")
                :ownedIndexId  :*}
              result)))
      (throws? (create-movie {:Data {:title "Raiders"}})) ; duplicate title

      (is= []
        (util/exec-session
          "create index  idxMovieTitle  if not exists
                         for (m:Movie) on (m.title);"))
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
      (let [idxs-found (it-> (util/exec-session "show indexes;")
                         (keep-if #(= (grab :name %) "cnstrUniqueMovieTitle") it))
            idx-ours   (only idxs-found)] ; there should be only 1
        (is (wild-match?
              {:entityType        "NODE"
               :id                :*
               :indexProvider     "native-btree-1.0"
               :labelsOrTypes     ["Movie"]
               :name              "cnstrUniqueMovieTitle"
               :populationPercent 100.0
               :properties        ["title"]
               :state             "ONLINE"
               :type              "BTREE"
               :uniqueness        "UNIQUE"}
              idx-ours)))

      )))

