(ns tst.demo.indexes
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)


(defn delete-all-movies!  ; works, but could overflow jvm heap for large db's
  []
  (unlazy (util/exec-sess "match (m:Movie) detach delete m;")))

(defn create-movie
  [arg]
  (unlazy (util/exec-sess "CREATE (m:Movie $Data) 
                    return m as film" arg)))


(defn get-all-movies
  []
  (unlazy (util/exec-sess "MATCH (m:Movie) 
                      RETURN m as flick")))

(dotest-focus
  (util/with-conn
    "bolt://localhost:7687" "neo4j"
    "secret"
      (util/with-session
        (newline)
        (delete-all-movies!)

        (spyx (create-movie {:Data {:title "The Matrix"}}))
        (spyx (create-movie {:Data {:title "Star Wars"}}))
        (spyx (create-movie {:Data {:title "Raiders"}}))
        (newline)
        (flush)

        (is= [] (util/exec-sess "drop constraint cnstrUniqueMovieTitle if exists ;"))
        (is=
          []
          (util/exec-sess
            "create constraint  cnstrUniqueMovieTitle
                           if not exists
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

      )))

