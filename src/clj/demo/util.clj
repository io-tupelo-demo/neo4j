(ns demo.util
  (:use tupelo.core)
  (:require
    [neo4j-clj.core :as neolib]
    [schema.core :as s]
    [tupelo.string :as str]
    [tupelo.schema :as tsk])
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
  `(binding [demo.util/*neo4j-conn-map* (neolib/connect (URI. ~uri) ~user ~pass)]
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
  `(binding [demo.util/*neo4j-session* (neolib/get-session demo.util/*neo4j-conn-map*)]
     (with-open [n4session# demo.util/*neo4j-session*]
       ; (println :sess-open-enter n4session#)
       ~@forms
       ; (println :sess-open-leave n4session#)
       )))

(defmacro with-session
  [& args]
  (with-session-impl args))

;-----------------------------------------------------------------------------
(s/defn session-run :- tsk/Vec
  "Within the context of `(with-session ...)`, run a neo4j cypher command."
  [query & args] (apply neolib/execute demo.util/*neo4j-session* query args))

(s/defn neo4j-info :- tsk/KeyMap
  []
  (with-session
    (only (session-run
            "call dbms.components() yield name, versions, edition
             unwind versions as version
             return name, version, edition ;"))))

(s/defn neo4j-version :- s/Str
  [] (grab :version (neo4j-info)))

;-----------------------------------------------------------------------------
(defn ^:no-doc apoc-version-str-impl
  []
  (with-session ; may throw if APOC not present
    (grab :ApocVersion (only (session-run "return apoc.version() as ApocVersion;")))))

(s/defn apoc-version :- s/Str
  "Returns the APOC version string, else `*** APOC not installed ***`"
  []
  (try
    (apoc-version-str-impl)
    (catch Exception <>
      "*** APOC not installed ***")))

(s/defn apoc-installed? :- s/Bool
  []
  (try
    (let [version-str (apoc-version-str-impl)]
      ; didn't throw, check version
      (str/increasing-or-equal? "4.0" version-str))
    (catch Exception <>
      false))) ; threw, so assume not installed

(s/defn all-nodes :- tsk/Vec
  []
  (with-session
    (vec (session-run "match (n) return n as node;"))))

(defn delete-all-nodes-simple! ; works, but could overflow jvm heap for large db's
  []
  (with-session
    (vec (session-run "match (n) detach delete n;"))))

(defn delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
  []
  (with-session
    (vec (session-run
           (str/quotes->double
             "call apoc.periodic.iterate( 'MATCH (n)  return n',
                                          'DETACH DELETE n',
                                          {batchSize:1000} )
              yield  batches, total
              return batches, total")))))

(defn delete-all-nodes!
  "Delete all nodes & edges in the graph.  Uses apoc.periodic.iterate() if installed."
  []
  (if (apoc-installed?)
    (delete-all-nodes-apoc!)
    (delete-all-nodes-simple!)))



