(ns tst.demo.indexes
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI]))

(defn delete-all-movies!  ; works, but could overflow jvm heap for large db's
  []
  (unlazy (util/exec-sess "match (m:Movie) detach delete m;")))

(defn create-movie
  [arg]
  (unlazy (util/exec-sess
            "CREATE (m:Movie $Data) 
            return m as film"
            arg)))

(defn get-all-movies
  []
  (unlazy (util/exec-sess
            "MATCH (m:Movie) 
            RETURN m as flick")))

(dotest-focus
  (util/with-conn
    ; "bolt://localhost:7687" "neo4j" "secret"
    "neo4j+s://4ca9bb9b.databases.neo4j.io" "neo4j" "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY"

      (util/with-session
        (newline)
        (spyx-pretty util/NEOCONN)
        (newline)
        (spyx (util/auto-version))

        (newline)
        (delete-all-movies!)

        (spyx (create-movie {:Data {:title "The Matrix"}}))
        (spyx (create-movie {:Data {:title "Star Wars"}}))
        (spyx (create-movie {:Data {:title "Raiders"}}))
        (newline)
        (spyx-pretty (get-all-movies))
        (newline)
        (flush)

        (is= [] (util/exec-sess "drop constraint cnstrUniqueMovieTitle if exists ;"))
        (is=
          []
          (util/exec-sess
            "create constraint  cnstrUniqueMovieTitle  if not exists
                           on (m:Movie) assert m.title is unique;"))
        (let [result (only (util/exec-sess "show constraints  ;"))]
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
          (util/exec-sess
            "create index  idxMovieTitle  if not exists
                           for (m:Movie) on (m.title);"))
        (let [result (only (util/exec-sess "show indexes;")) ]
          (is (wild-match?  
                {:entityType "NODE"
                 :id :*
                 :indexProvider "native-btree-1.0"
                 :labelsOrTypes ["Movie"]
                 :name "cnstrUniqueMovieTitle"
                 :populationPercent 100.0
                 :properties ["title"]
                 :state "ONLINE"
                 :type "BTREE"
                 :uniqueness "UNIQUE"}
                result)))

      )))

