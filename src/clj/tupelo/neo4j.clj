(ns tupelo.neo4j
  (:use tupelo.core)
  (:require
    [neo4j-clj.neo4j-impl :as neolib]
    [schema.core :as s]
    [tupelo.set :as set]
    [tupelo.string :as str]
    [tupelo.schema :as tsk])
  (:import
    [java.net URI]
    ))

; A neo4j connection map with the driver under `:db`
; #todo fork & cleanup from neo4j-clj.core to remove extraneous junk
(def ^:dynamic *neo4j-driver-map* nil) ; #todo add earmuffs
; Sample:
;   {:url        #object[java.net.URI 0x1d4d84fb "neo4j+s://4ca9bb9b.databases.neo4j.io"],
;    :user       "neo4j",
;    :password   "g6o2KIftFE6EIYMUCIY9a6DW0oVcwihh7m0Z5DP-jcY",
;    :db         #object[org.neo4j.driver.internal.InternalDriver 0x59e97f75 "org.neo4j.driver.internal.InternalDriver@59e97f75"],
;    :destroy-fn #object[neo4j_clj.core$connect$fn__19085 0x1d696b35 "neo4j_clj.core$connect$fn__19085@1d696b35"]}

; a neo4j Session object
(def ^:dynamic *neo4j-session* nil) ; #todo add earmuffs
; Sample:
;   #object[org.neo4j.driver.internal.InternalSession 0x2eba393 "org.neo4j.driver.internal.InternalSession@2eba393"]

; for debugging
(def ^:dynamic *verbose* false) ; #todo add earmuffs

;-----------------------------------------------------------------------------
(defn ^:no-doc with-driver-impl
  [[uri user pass & forms]]
  `(binding [tupelo.neo4j/*neo4j-driver-map* (neolib/connect (URI. ~uri) ~user ~pass)]
     (with-open [n4driver# (:db tupelo.neo4j/*neo4j-driver-map*)]
       (when *verbose* (spy :with-driver-impl--enter n4driver#))
       ~@forms
       (when *verbose* (spy :with-driver-impl--leave n4driver#)))))

(defmacro with-driver
  "Creates a Neo4j driver (cached as `*neo4j-driver-map*`) for use by the enclosed forms."
  [uri user pass & forms]
  (with-driver-impl (prepend uri user pass forms)))

;-----------------------------------------------------------------------------
(defn ^:no-doc with-session-impl
  [forms]
  `(binding [tupelo.neo4j/*neo4j-session* (neolib/get-session tupelo.neo4j/*neo4j-driver-map*)]
     (with-open [n4session# tupelo.neo4j/*neo4j-session*]
       (when *verbose* (spy :sess-open-enter--enter n4session#))
       ~@forms
       (when *verbose* (spy :sess-open-leave--leave n4session#)))))

(defmacro with-session
  "Creates a Neo4j session object (cached as `*neo4j-session*`) for use by the enclosed forms.
  Must be enclosed by a `(with-driver ...)` form."
  [& args]
  (with-session-impl args))

;-----------------------------------------------------------------------------
(s/defn run :- tsk/Vec
  "Runs a neo4j cypher command.  Must be enclosed by a `(with-session ...)` form."
  [query & args] (apply neolib/session-run tupelo.neo4j/*neo4j-session* query args))

(s/defn info-map :- tsk/KeyMap
  []
  (only (run
          "call dbms.components() yield name, versions, edition
           unwind versions as version
           return name, version, edition ;")))

(s/defn neo4j-version :- s/Str
  [] (grab :version (info-map)))

;-----------------------------------------------------------------------------
(s/defn ^:no-doc apoc-version-impl :- s/Str
  []
  ; may throw if APOC not present
  (grab :ApocVersion (only (run "return apoc.version() as ApocVersion;"))))

(s/defn apoc-version :- s/Str
  "Returns the APOC version string, else `*** APOC not installed ***`"
  []
  (try
    (apoc-version-impl)
    (catch Exception <>
      "*** APOC not installed ***")))

(s/defn apoc-installed? :- s/Bool
  []
  (try
    (let [version-str (apoc-version-impl)]
      ; didn't throw, check version
      (str/increasing-or-equal? "4.0" version-str))
    (catch Exception <>
      false))) ; it threw, so assume not installed

(s/defn nodes-all :- tsk/Vec
  [] (vec (run "match (n) return n as node;")))

;-----------------------------------------------------------------------------
(s/defn db-names-all :- [s/Str]
  []
  (mapv #(grab :name %) (run "show databases")))

(def core-db-names
  "Never delete these DBs! "
  #{"system" "neo4j"})
(s/defn drop-extraneous-dbs! :- [s/Str]
  []
  (let [drop-db-names (set/difference (set (db-names-all)) core-db-names)]
    (doseq [db-name drop-db-names]
      (run (format "drop database %s if exists" db-name)))))

;-----------------------------------------------------------------------------
; Identifies an Neo4j internal index
(def ^:no-doc org-neo4j-prefix "__org_neo4j")
(s/defn ^:no-doc internal-index? :- s/Bool
  "Identifies extraneous indexes (neo4j linux!) are also returned. See unit test for example"
  [idx-map :- tsk/KeyMap]
  (str/contains-str? (grab :name idx-map) org-neo4j-prefix))

(s/defn ^:no-doc extraneous-index? :- s/Bool
  "Identifies extraneous indexes which can exist even in a newly-created, empty db. See unit test for example "
  [idx-map :- tsk/KeyMap]
  (or
    (nil? (grab :labelsOrTypes idx-map))
    (nil? (grab :properties idx-map))))

(s/defn ^:no-doc user-index? :- s/Bool
  "A user-created index (not Neo4j-created). See unit test for example "
  [idx-map :- tsk/KeyMap]
  (not (or
         (internal-index? idx-map)
         (extraneous-index? idx-map))))

(s/defn indexes-all-details :- [tsk/KeyMap]
  [] (vec (run "show indexes;")))

(s/defn indexes-user-details :- [tsk/KeyMap]
  []
  (keep-if #(user-index? %) (indexes-all-details)))

(s/defn indexes-user-names :- [s/Str]
  []
  (mapv #(grab :name %) (indexes-user-details)))

(s/defn indexes-drop!
  [idx-name]
  (let [cmd (format "drop index %s if exists" idx-name)]
    (run cmd)))

(s/defn indexes-drop-all!
  []
  (doseq [idx-map (indexes-user-details)]
    (let [idx-name (grab :name idx-map)]
      (indexes-drop! idx-name))))

;-----------------------------------------------------------------------------
(s/defn constraints-all-details :- [tsk/KeyMap]
  [] (vec (run "show all constraints;")))

(s/defn constraints-all-names :- [s/Str]
  [] (mapv #(grab :name %) (constraints-all-details)))

(s/defn constraint-drop!
  [cnstr-name]
  (let [cmd (format "drop constraint %s if exists" cnstr-name)]
    (spyx cmd)
    (run cmd)))

(s/defn constraints-drop-all!
  []
  (doseq [cnstr-name (constraints-all-names)]
    (println "  dropping constraint: " cnstr-name)
    (constraint-drop! cnstr-name)))

;-----------------------------------------------------------------------------
(defn delete-all-nodes-simple! ; works, but could overflow jvm heap for large db's
  []
  (vec (run "match (n) detach delete n;")))

(defn delete-all-nodes-apoc! ; APOC function works in batches - safe for large db's
  []
  (vec (run
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



