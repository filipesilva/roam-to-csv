(ns roam-to-csv.main
  (:require [roam-to-csv.compat :as compat]
            [roam-to-csv.athens :as athens]
            [roam-to-csv.roam :as roam]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.tools.cli :as cli]
            [clojure.pprint :as pprint]
            [datascript.core :as d]
            [tick.alpha.api :as t])
  ;; Needed for standalone jar to invoke with java
  #?(:clj (:gen-class)))

(defn read-db [edn-string]
  (edn/read-string {:readers d/data-readers} edn-string))

(defn read-query [edn-string]
  (edn/read-string edn-string))

;; TODO:
;; --query-file
;; --query-params
(def cli-options
  [["-e" "--extra" "Include extra information: user, edit, open, path, refs."]
   ["-q" "--query QUERY" "Use a custom Datalog query to create the CSV."
    :parse-fn read-query]
   ["-p" "--pretty-print" "Pretty print the EDN export only."]
   ["-c" "--convert FORMAT" "Convert an input csv to another format [athens.transit roam.json]"]
   ["-h" "--help" "Show this message."]])

(defn print-help [summary]
  (println "Convert a Roam Research EDN export into CSV format.")
  (println "Given ./backup.edn, creates ./backup.csv with pages and blocks.")
  (println)
  (println "Usage:")
  (println "  roam-to-csv ./backup.edn")
  (println)
  (println "Options:")
  ;; Tools.cli provides a :summary key that can be used for printing:
  (println summary))

(defn format-ms [ms]
  (-> ms (t/new-duration :millis) t/instant str))

(defn page?
  [{:keys [block/uid node/title]}]
  (and uid title))

(defn page-v
  [{:keys [block/uid node/title create/time]}]
  [uid title nil nil nil (-> time (or 0) format-ms)])

(defn block?
  [{:block/keys [uid string order _children]}]
  (and uid string order (first _children)))

(defn block-v
  [{:block/keys [uid string order _children] :keys [:create/time]}]
  [uid nil (-> _children first :block/uid) string order (-> time (or 0) format-ms)])

(defn uid->refs
  [db uid]
  (->> [:block/uid uid]
       (d/pull db '[{:block/refs [:block/uid]}])
       :block/refs
       (map :block/uid)))

(defn collect-uids
  [m]
  (loop [{:keys [block/uid block/_children]} m
         uids []]
    (if uid
      (recur (first _children) (conj uids uid))
      uids)))

(defn uid->path
  [db uid]
  (->> [:block/uid uid]
       (d/pull db '[:block/uid {:block/_children ...}])
       collect-uids
       reverse
       vec))

(defn add-path-and-refs
  [db [uid :as v]]
  (into v [(uid->path db uid)
           (uid->refs db uid)]))

(defn vec->csv-str
  [v]
  (when (seq v)
    (str/join "," v)))

(defn add-extra-fields
  [db [uid title :as v]]
  (let [e (d/entity db [:block/uid uid])]
    (into v [(-> e :create/user :user/uid (or ""))
             (-> e :create/user :user/display-name (or ""))
             (-> e :edit/time (or 0) format-ms)
             (-> e :edit/user :user/uid (or ""))
             (-> e :edit/user :user/display-name (or ""))
             (when-not title (if (:block/open e) 1 0))
             (-> db (uid->path uid) vec->csv-str)
             (-> db (uid->refs uid) vec->csv-str)])))

(defn query->header
  "Extract the :find bindings as strings, stripped of the initial ? if any."
  [query]
  (->> (partition-by keyword? query)
       second
       (map str)
       (map #(if (str/starts-with? % "?") (subs % 1) %))
       vec))

(def simple-headers ["uid" "title" "parent" "string" "order" "create-time"])

(def extra-headers ["uid" "title" "parent" "string" "order"
                    "create-time" "create-user-uid" "create-user-display-name"
                    "edit-time" "edit-user-uid" "edit-user-display-name"
                    "open" "path" "refs"])

(defn entity-xf
  [db attr & xfs]
  (sequence
   (apply comp (map first) (map (partial d/entity db)) xfs)
   (d/datoms db :avet attr)))

(defmulti roam-db->csv-table
  "Return a CSV table vector from q over db."
  (fn [q _db]
    q))

(defmethod roam-db->csv-table
  :simple
  [_q db]
  (vec (concat [simple-headers]
               (entity-xf db :node/title (filter page?) (map page-v))
               (entity-xf db :block/uid (filter block?) (map block-v)))))

(defmethod roam-db->csv-table
  :extra
  [_q db]
  (let [add-extra-fields' (partial add-extra-fields db)]
    (vec (concat [extra-headers]
                 (entity-xf db :node/title (filter page?) (map page-v) (map add-extra-fields'))
                 (entity-xf db :block/uid (filter block?) (map block-v) (map add-extra-fields'))))))

(defmethod roam-db->csv-table
  :default
  [q db]
  (let [res    (d/q q db)
        header (query->header q)]
    (into [header] (map vec res))))

(defn csv-row->map
  [header row]
  (zipmap header row))

(defn csv-data
  [csv-str]
  (let [csv    (compat/read-csv csv-str)
        header (first csv)
        data   (rest csv)
        min-headers (->> simple-headers (take 5) vec)]
    (if-not (every? (set header) min-headers)
      (throw (ex-info (str "Unsupported header format for conversion, header must contain at least" min-headers)
                      {:header header}))
      (map (partial csv-row->map header) data))))

(defmulti convert-csv
  (fn [type _edn-string]
    type))

(defmethod convert-csv
  "athens.transit"
  [_format csv-str]
  (let [data (csv-data csv-str)
        conn (athens/create-conn)]
    (doseq [{:strs [uid title parent string order]} data]
      (if (empty? title)
        (athens/add-block! conn uid parent string order)
        (athens/add-page! conn uid title)))
    (athens/conn-to-str conn)))

(defmethod convert-csv "roam.json"
  [_format csv-str]
  (let [data (csv-data csv-str)
        conn (roam/create-conn)]
    (doseq [{:strs [uid title parent string order create-time create-user-uid edit-time edit-user-uid]} data]
      (let [order (compat/parse-int order)]
        (when (seq uid)
          (if (empty? title)
            (roam/add-block! conn uid parent string order create-time create-user-uid edit-time edit-user-uid)
            (roam/add-page! conn uid title create-time create-user-uid edit-time edit-user-uid)))))
    (roam/conn-to-json conn)))

(defmethod convert-csv :default
  [format _csv-str]
  (throw (ex-info "Unknown convert format" {:format format})))

(defn format-ext
  [format]
  (case format
    "athens.transit" ".transit"
    "roam.json"      ".json"
    (throw (ex-info "Unsupported format" {:format format}))))

(defn slurp-xform-spit
  ([input-filename new-ext xform]
   (let [filename-without-ext (subs input-filename 0 (str/last-index-of input-filename "."))
         output-filename      (str filename-without-ext new-ext)]
     (->> input-filename
          compat/slurp
          xform
          (compat/spit output-filename)))))

(defn roam-to-csv 
  [input-filename options]
  (let [{:keys [pretty-print query extra convert]} options] 
    (cond
      (str/blank? input-filename)
      (println "Invalid filename, it must be non-empty")

      pretty-print
      (slurp-xform-spit input-filename ".pp.edn" (comp pprint/pprint read-db))

      convert
      (slurp-xform-spit input-filename (format-ext convert) (partial convert-csv convert))

      :else
      (slurp-xform-spit input-filename ".csv" (comp
                                               compat/csv-str
                                               (partial roam-db->csv-table
                                                        (or query
                                                            (when extra :extra)
                                                            :simple))
                                               read-db)))))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [help]}                      options
        input-filename                      (first arguments)]
    (cond
      help
      (print-help summary)
      
      :else
      (roam-to-csv input-filename options))))

#?(:cljs
   (def ^:export node-exports
     #js {:roamToCsv (fn node-roam-to-csv
                       [input-filename options]
                       (roam-to-csv input-filename (js->clj options :keywordize-keys true)))
          :main -main}))

(comment
  ;; Read the file as a Datascript database
  (def db (-> "./test-goldens/simple/backup.edn" compat/slurp read-db))
  db

  ;; Simple and extra query
  (roam-db->csv-table :simple db)
  (roam-db->csv-table :extra db)

  ;; Custom query for uids+blocks
  (roam-db->csv-table
   '[:find ?uid ?string ?p
     :where
     [?b :block/uid ?uid]
     [?b :block/string ?string]
     [?p :block/children ?b]]
   db)
  
  ; Custom query for all attrs
  (roam-db->csv-table
   '[:find (distinct ?attr)
     :where
     [?e ?attr]]
   db)

  )
