(ns demo.util
  (:use tupelo.core)
  (:require
    [neo4j-clj.core :as db]
    [schema.core :as s]
    [tupelo.string :as str])
  (:import
    [java.net URI]
  ))

; a neo4j connection map with the driver under `:db`
(def ^:dynamic *neo4j-conn-map* nil) ; #todo add earmuffs

; a neo4j Session object
(def ^:dynamic *neo4j-session* nil) ; #todo add earmuffs

;-----------------------------------------------------------------------------
(defn with-connection-impl
  [[uri user pass & forms]]
  `(binding [demo.util/*neo4j-conn-map* (db/connect (URI. ~uri) ~user ~pass)]
     (with-open [n4driver# (:db demo.util/*neo4j-conn-map*)]
       ; (println :drvr-open-enter n4driver#)
       ~@forms
       ; (println :drvr-open-leave n4driver#)
     )))

(defmacro with-connection
  [& args]
  (with-connection-impl args))

;-----------------------------------------------------------------------------
(defn with-session-impl
  [forms]
  `(binding [demo.util/*neo4j-session* (db/get-session demo.util/*neo4j-conn-map*)]
     (with-open [n4session# demo.util/*neo4j-session*]
       ; (println :sess-open-enter n4session#)
       ~@forms
       ; (println :sess-open-leave n4session#)
     )))

(defmacro with-session
  [& args]
  (with-session-impl args))

;-----------------------------------------------------------------------------
(defn exec-session
  "Within the context of `(with-session ...)`, execute a neo4j command."
  ([query] (db/execute demo.util/*neo4j-session* query))
  ([query params] (db/execute demo.util/*neo4j-session* query params))
)

(defn neo4j-version
  []
  (with-session
    (vec
      (exec-session
        "call dbms.components() yield name, versions, edition
         unwind versions as version
         return name, version, edition ;"))))

;-----------------------------------------------------------------------------
(def apoc-installed? false); assume APOC is not installed

(defn apoc-version
  [driver]
  (with-open [session (db/get-session driver)]
    (vec (db/execute session
                     "return apoc.version() as ApocVersion;"))))

(defn delete-all-nodes-simple!  ; works, but could overflow jvm heap for large db's
  [driver]
  (with-open [session (db/get-session driver)]
    (vec (db/execute session
                     "match (n) detach delete n;"))))

(defn delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
  [driver]
  (with-open [session (db/get-session driver)]
    (vec
      (db/execute
        session
        (str/quotes->double
          "call apoc.periodic.iterate( 'MATCH (n)  return n', 
                                     'DETACH DELETE n',
                                     {batchSize:1000} )
         yield  batches, total 
         return batches, total")))))

(defn delete-all-nodes!
  "Delete all nodes & edges in the graph.  Uses apoc.periodic.iterate() if installed."
  ([driver] (delete-all-nodes! driver false))
  ([driver verbose?]
   (when verbose?
     (print "Getting Neo4j version...  ")
     (flush))
   (let [neo4j-version (:version (only (neo4j-version driver)))]
     (when verbose?
       (println neo4j-version))
     (try
       (when verbose?
         (print "Getting APOC version info...  ")
         (flush))
       (let [apoc-version (grab :ApocVersion (only (apoc-version driver)))]
         (when verbose?
           (println apoc-version))
         (def apoc-installed? true)) ; no exception, so APOC is installed
       (catch Exception <>
         (println "  *** APOC not installed ***"))))
   (if apoc-installed?
     (delete-all-nodes-apoc! driver)
     (delete-all-nodes-simple! driver))))



