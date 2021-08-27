(ns tupelo.neo4j
  (:use tupelo.core)
  (:require
    [neo4j-clj.core :as neolib]
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
(defn with-driver-impl
  [[uri user pass & forms]]
  `(binding [tupelo.neo4j/*neo4j-driver-map* (neolib/connect (URI. ~uri) ~user ~pass)]
     (with-open [n4driver# (:db tupelo.neo4j/*neo4j-driver-map*)]
       (when *verbose* (spy :with-driver-impl--enter n4driver#))
       ~@forms
       (when *verbose* (spy :with-driver-impl--leave n4driver#)))))

(defmacro with-driver
  [& args]
  (with-driver-impl args))

;-----------------------------------------------------------------------------
(defn with-session-impl
  [forms]
  `(binding [tupelo.neo4j/*neo4j-session* (neolib/get-session tupelo.neo4j/*neo4j-driver-map*)]
     (with-open [n4session# tupelo.neo4j/*neo4j-session*]
       (when *verbose* (spy :sess-open-enter--enter n4session#))
       ~@forms
       (when *verbose* (spy :sess-open-leave--leave n4session#)))))

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
  [] (vec (session-run "match (n) return n as node;")))

;-----------------------------------------------------------------------------
(s/defn db-names-all :- [s/Str]
  []
  (mapv #(grab :name %) (session-run "show databases")))

(s/defn drop-extraneous-dbs! :- [s/Str]
  []
  (let [keep-db-names #{"system" "neo4j"} ; never delete these DBs!
        drop-db-names (set/difference (set (db-names-all)) keep-db-names)]
    (doseq [db-name drop-db-names]
      (session-run (format "drop database %s if exists" db-name)))))

;-----------------------------------------------------------------------------
(s/defn indexes-all :- [tsk/KeyMap]
  [] (vec (session-run "show indexes;")))

(s/defn indexes-user :- [tsk/KeyMap]
  []
  (let [idxs-user (drop-if
                    (fn [idx-map]
                      (let [idx-name (grab :name idx-map)]
                        (str/contains-str? idx-name "__org_neo4j")))
                    (indexes-all))]
    idxs-user))

(s/defn indexes-drop!
  [idx-name]
  (let [cmd (format "drop index %s if exists" idx-name)]
    (session-run cmd)))

(s/defn indexes-drop-all!
  []
  (doseq [idx-map (indexes-user)]
    (let [idx-name (grab :name idx-map)]
      (indexes-drop! idx-name))))

;-----------------------------------------------------------------------------
(s/defn constraints-all :- [tsk/KeyMap]
  [] (vec (session-run "show all constraints;")))

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

;-----------------------------------------------------------------------------
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



