(ns tupelo.neo4j
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
(def ^:dynamic *neo4j-driver-map* nil) ; #todo add earmuffs

; a neo4j Session object
(def ^:dynamic *neo4j-session* nil) ; #todo add earmuffs

; for debugging
(def ^:dynamic *verbose* false) ; #todo add earmuffs

;-----------------------------------------------------------------------------
(defn with-driver-impl
  [[uri user pass & forms]]
  `(binding [tupelo.neo4j/*neo4j-driver-map* (neolib/connect (URI. ~uri) ~user ~pass)]
     (with-open [n4driver# (:db tupelo.neo4j/*neo4j-driver-map*)]
       ; (println :drvr-open-enter n4driver#)
       ~@forms
       ; (println :drvr-open-leave n4driver#)
       )))

(defmacro with-driver
  [& args]
  (with-driver-impl args))

;-----------------------------------------------------------------------------
(defn with-session-impl
  [forms]
  `(binding [tupelo.neo4j/*neo4j-session* (neolib/get-session tupelo.neo4j/*neo4j-driver-map*)]
     (with-open [n4session# tupelo.neo4j/*neo4j-session*]
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
  [query & args] (apply neolib/execute tupelo.neo4j/*neo4j-session* query args))

(s/defn neo4j-info :- tsk/KeyMap
  []
  (only (session-run
          "call dbms.components() yield name, versions, edition
           unwind versions as version
           return name, version, edition ;")))

(s/defn neo4j-version :- s/Str
  [] (grab :version (neo4j-info)))

;-----------------------------------------------------------------------------
(defn ^:no-doc apoc-version-str-impl
  []
  ; may throw if APOC not present
  (grab :ApocVersion (only (session-run "return apoc.version() as ApocVersion;"))))

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

(s/defn nodes-all :- tsk/Vec
  []
  (vec (session-run "match (n) return n as node;")))

(s/defn indexes-all :- [tsk/KeyMap]
  []
  (let [indexes (vec (session-run "show indexes;")) ; #todo all?
        ]
    indexes))

(s/defn constraints-all :- [tsk/KeyMap]
  []
  (spyx (vec (session-run "show all constraints;"))))

(s/defn constraint-drop!
  [cnstr-name]
  (let [cmd (format "drop constraint %s if exists" cnstr-name)]
    (spyx cmd)
    (session-run cmd)))

(s/defn constraints-drop-all!
  []
  (doseq [cnstr-map (constraints-all)]
    (let [cnstr-name (grab :name cnstr-map)]
      (println "dropping constraint " cnstr-name)
      (constraint-drop! cnstr-name))))

(s/defn indexes-user :- [tsk/KeyMap]
  []
  (nl)
  (let [idxs-user (drop-if
                    (fn [idx-map]
                      (spyx idx-map)
                      (let [idx-name (grab :name idx-map)]
                        (str/contains-str? idx-name "__org_neo4j")))
                    (indexes-all))]
    (spyx-pretty idxs-user)
    )
  )

(s/defn indexes-drop!
  [idx-name]
  (let [cmd (format "drop index %s if exists" idx-name)]
    (spyx cmd)
    (session-run cmd)))

(s/defn indexes-drop-all!
  []
  (doseq [idx-map (indexes-all)]
    (let [idx-name (grab :name idx-map)]
      (println "dropping index " idx-name)
      (indexes-drop! idx-name))))

(defn delete-all-nodes-simple! ; works, but could overflow jvm heap for large db's
  []
  (vec (session-run "match (n) detach delete n;")))

(defn delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
  []
  (vec (session-run
         (str/quotes->double
           "call apoc.periodic.iterate( 'MATCH (n)  return n',
                                        'DETACH DELETE n',
                                        {batchSize:1000} )
            yield  batches, total
            return batches, total"))))

(defn delete-all-nodes!
  "Delete all nodes & edges in the graph.  Uses apoc.periodic.iterate() if installed."
  []
  (if (apoc-installed?)
    (delete-all-nodes-apoc!)
    (delete-all-nodes-simple!)))



