(ns tst.demo.indexes
  (:use tupelo.core tupelo.test)
  (:require
    [demo.util :as util]
    [neo4j-clj.core :as db]
    [tupelo.string :as str])
  (:import
    [java.net URI])
)

(def ^:dynamic DRIVER nil)
(def ^:dynamic SESSION nil)

;-----------------------------------------------------------------------------
(defn with-driver-impl 
  [[uri user pass & forms]]
 `(binding [~'DRIVER  (db/connect (URI. ~uri) ~user ~pass)]
    (with-open [driver#  (:db ~'DRIVER)]
      (println :with-open-enter driver#)
      ~@forms
    (println :with-open-leave driver#)))
 )

(defmacro with-driver 
  [& args] (with-driver-impl args))

#_(dotest
  (spy-pretty :impl
    (with-driver-impl '[
      (URI. "bolt://localhost:7687") "neo4j" "secret"
      (form1 DRIVER)
      (form2)
    ] ))
)

#_(dotest
  (with-driver "bolt://localhost:7687" "neo4j" "secret"
    ; (println :aa DRIVER )
    (println :version (util/neo4j-version DRIVER))
    ; (println :zz DRIVER )
    ))


(defn with-driver-impl 
  [[uri user pass & forms]]
 `(binding [~'DRIVER  (db/connect (URI. ~uri) ~user ~pass)]
    (with-open [driver#  (:db ~'DRIVER)]
      (println :with-open-enter driver#)
      ~@forms
    (println :with-open-leave driver#)))
 )

(defmacro with-driver 
  [& args] (with-driver-impl args))

#_(dotest-focus
  (spy-pretty :impl
    (with-driver-impl '[
      (URI. "bolt://localhost:7687") "neo4j" "secret"
      (form1 DRIVER)
      (form2)
    ] ))
)

#_(dotest
  (with-driver "bolt://localhost:7687" "neo4j" "secret"
    ; (println :aa DRIVER )
    (println :version (util/neo4j-version DRIVER))
    ; (println :zz DRIVER )
    ))


(defn with-driver-impl 
  [[uri user pass & forms]]
 `(binding [~'DRIVER  (db/connect (URI. ~uri) ~user ~pass)]
    (with-open [driver#  (:db ~'DRIVER)]
      (println :with-open-enter driver#)
      ~@forms
    (println :with-open-leave driver#))))

(defmacro with-driver 
  [& args] (with-driver-impl args))

#_(dotest-focus
  (spy-pretty :impl
    (with-driver-impl '[
      (URI. "bolt://localhost:7687") "neo4j" "secret"
      (form1 DRIVER)
      (form2)
    ] ))
)

#_(dotest
  (when false
    (with-driver "bolt://localhost:7687" "neo4j" "secret"
      ; (println :aa DRIVER )
      (println :version (util/neo4j-version DRIVER))
      ; (println :zz DRIVER )
      )))

;-----------------------------------------------------------------------------
(defn with-session-impl 
  [forms]
 `(binding [~'SESSION  (db/get-session ~'DRIVER )]
    (println :binding-aa ~'SESSION)
    (with-open [session#  ~'SESSION ]
      (println :sess-with-open-enter session#)
      ~@forms
      (println :sess-with-open-leave session#))))

(defmacro with-session 
  [& args] (with-session-impl args))

#_(dotest-focus
  (spy-pretty :impl
    (with-session-impl '[
      (form1 SESSION)
      (form2)
    ] ))
)


(defn exec 
  ([query] (db/execute SESSION query))
  ([query params] (db/execute SESSION query params))
)

(defn get-vers
  []
  (vec
    (exec
      "call dbms.components() yield name, versions, edition
       unwind versions as version
       return name, version, edition ;")))

(dotest-focus
  (with-driver "bolt://localhost:7687" "neo4j" "secret"
    (with-session 
      (println :use-aa SESSION )
      (println :use-version (get-vers))
      (println :use-zz SESSION )
      )))



;-----------------------------------------------------------------------------
(comment 
    (def driver
      (db/connect (URI. "bolt://localhost:7687")  ; uri
                  "neo4j"
                  "secret")); user/pass

    (defn delete-all-movies!  ; works, but could overflow jvm heap for large db's
      [driver]
      (with-open [ss (db/get-session driver)]
        (doall (db/execute ss "match (m:Movie) detach delete m;"))))

    (db/defquery create-movie ; creates a global Var function, taking a session or tx as 1st arg
                 "CREATE (m:Movie $Data) 
                  return m as film")

    (db/defquery get-all-movies ; creates a global Var function, taking a session or tx as 1st arg
                 "MATCH (m:Movie) 
                  RETURN m as flick")

    #_(dotest-focus
      (delete-all-movies! driver)

      (with-open [session (db/get-session driver)]
         (spyx (create-movie session {:Data {:title "The Matrix" }}))
         (spyx (create-movie session {:Data {:title "Star Wars"}}))
         (spyx (create-movie session {:Data {:title "Raiders" }}))
      )

     (with-open [sess (db/get-session driver)]
       (is= [] (db/execute sess "drop constraint cnstrUniqueMovieTitle if exists ;"))
       (is= [] (db/execute sess "create constraint  cnstrUniqueMovieTitle
                               if not exists
                               on (m:Movie) assert m.title is unique;"))
       (let [result (only (db/execute sess "show constraints  ;")) ]
         (spyx result)
         (is (wild-match? 
              '{:id :*, :name "cnstrUniqueMovieTitle", :type "UNIQUENESS", :entityType "NODE", 
                :labelsOrTypes ("Movie"), :properties ("title"), :ownedIndexId :* }
               result)))

       )

    )

)
