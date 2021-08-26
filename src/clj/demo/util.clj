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

; for debugging
(def ^:dynamic *verbose* false) ; #todo add earmuffs

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
    (vec (exec-session
           "call dbms.components() yield name, versions, edition
            unwind versions as version
            return name, version, edition ;"))))

;-----------------------------------------------------------------------------
(defn apoc-version
  []
  (with-session
    (vec (exec-session "return apoc.version() as ApocVersion;"))))

(defn apoc-installed?
  []
  (let [vers (:ApocVersion (only (apoc-version)))]
    (string? vers)))

(defn delete-all-nodes-simple!  ; works, but could overflow jvm heap for large db's
  []
  (with-session
    (vec (exec-session "match (n) detach delete n;"))))

(defn delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
  []
  (with-session
    (vec (exec-session
           (str/quotes->double
             "call apoc.periodic.iterate( 'MATCH (n)  return n',
                                        'DETACH DELETE n',
                                        {batchSize:1000} )
              yield  batches, total
              return batches, total")))))

(defn delete-all-nodes!
  "Delete all nodes & edges in the graph.  Uses apoc.periodic.iterate() if installed."
  []
  (try
    (when *verbose* (println "Getting APOC version info...  "))
    (let [apoc-version (grab :ApocVersion (only (apoc-version)))]
      (when *verbose* (println apoc-version)))
    (catch Exception <>
      (println "  *** APOC not installed ***")))
  (if apoc-installed?
    (delete-all-nodes-apoc!)
    (delete-all-nodes-simple!)))



