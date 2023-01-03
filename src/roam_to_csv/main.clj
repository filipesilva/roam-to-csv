(ns roam-to-csv.main
  (:require [roam-to-csv.athens :as athens]
            [roam-to-csv.roam :as roam]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.data.csv :as csv]
            [clojure.tools.cli :as cli]
            [clojure.pprint :as pprint]
            [datascript.core :as d]
            [tick.alpha.api :as t])
  (:import
   [java.io StringWriter])
  ;; Needed for standalone jar to invoke with java
  (:gen-class))

(defn read-db [edn-string]
  (edn/read-string {:readers d/data-readers} edn-string))

(defn csv-str [data]
  (let [writer (StringWriter.)]
    (csv/write-csv writer data)
    (str writer)))

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

(def simple-pages-query
  '[:find ?uid ?title ?create-time
    :where
    [?p :block/uid ?uid]
    [?p :node/title ?title]
    [(get-else $ ?p :create/time 0) ?create-time]])

(def simple-blocks-query
  '[:find ?uid ?parent ?string ?order ?create-time
    :where
    [?b :block/uid ?uid]
    [?b :block/string ?string]
    [?b :block/order ?order]
    [?p :block/children ?b]
    [?p :block/uid ?parent]
    [(get-else $ ?p :create/time 0) ?create-time]])

(def maybe-time-and-user
  '[[(get-else $ ?b :create/time 0) ?create-time]
    [(get-else $ ?b :create/user 0) ?create-user]
    [(get-else $ ?create-user :user/uid "") ?create-user-uid]
    [(get-else $ ?create-user :user/display-name "") ?create-user-display-name]
    [(get-else $ ?b :edit/time 0) ?edit-time]
    [(get-else $ ?b :edit/user 0) ?edit-user]
    [(get-else $ ?edit-user :user/uid "") ?edit-user-uid]
    [(get-else $ ?edit-user :user/display-name "") ?edit-user-display-name]])

(def extra-pages-query
  (into
   '[:find ?uid ?title
           ?create-time ?create-user-uid ?create-user-display-name
           ?edit-time ?edit-user-uid ?edit-user-display-name
     :where
     [?b :block/uid ?uid]
     [?b :node/title ?title]]
   maybe-time-and-user))

(def extra-blocks-query
  (into
   '[:find ?uid ?parent ?string ?order
     ?create-time ?create-user-uid ?create-user-display-name
     ?edit-time ?edit-user-uid ?edit-user-display-name
     ?open
     :where
     [?b :block/uid ?uid]
     [?b :block/string ?string]
     [?b :block/order ?order]
     [?p :block/children ?b]
     [?p :block/uid ?parent]
     [?b :block/open ?open]
     [(get-else $ ?b :block/open false) ?open]]
   maybe-time-and-user))

(defn vec->csv-str
  [v]
  (when (seq v)
    (str/join "," v)))

(defn page-csv
  [[uid title
    create-time create-user-uid create-user-display-name
    edit-time edit-user-uid edit-user-display-name
    path refs
    :as v]]
  (if (-> v count (= 3)) ;; simple
    [uid title nil nil nil (format-ms create-time)]
    [uid title nil nil nil
     (format-ms create-time) create-user-uid create-user-display-name
     (when edit-time (format-ms edit-time)) edit-user-uid edit-user-display-name
     nil (vec->csv-str path) (vec->csv-str refs)]))

(defn block-csv
  [[uid parent string order
    create-time create-user-uid create-user-display-name
    edit-time edit-user-uid edit-user-display-name
    open path refs
    :as v]]
  (if (-> v count (= 5)) ;; simple
    [uid nil parent string order (format-ms create-time)]
    [uid nil parent string order
     (format-ms create-time) create-user-uid create-user-display-name
     (when edit-time (format-ms edit-time)) edit-user-uid edit-user-display-name
     (if open 1 0) (vec->csv-str path) (vec->csv-str refs)]))

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

(defmulti roam-db->csv-table
  "Return a CSV table vector from q over db."
  (fn [q _db]
    q))

(defmethod roam-db->csv-table
  :simple
  [_q db]
  (let [pages  (d/q simple-pages-query db)
        blocks (d/q simple-blocks-query db)]
    (vec (concat [simple-headers]
                 (map page-csv pages)
                 (map block-csv blocks)))))

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

(defmethod roam-db->csv-table
  :extra
  [_q db]
  (let [f      (partial add-path-and-refs db)
        pages  (map f (d/q extra-pages-query db))
        blocks (map f (d/q extra-blocks-query db))]
    (vec (concat [extra-headers]
                 (map page-csv pages)
                 (map block-csv blocks)))))

(defmethod roam-db->csv-table
  :default
  [q db]
  (let [res    (d/q q db)
        header (query->header q)]
    (into [header] (map vec res))))


(defn csv-data
  [csv-str]
  (let [csv    (csv/read-csv csv-str)
        header (first csv)
        data   (rest csv)]
    (if-not (= (take 5 header) (take 5 simple-headers))
      (throw (ex-info (str "Unsupported header format for conversion, header must start with"
                           (->> simple-headers (take 5) vec))
                      {:header header}))
      data)))

(defmulti convert-csv
  (fn [type _edn-string]
    type))

(defmethod convert-csv
  "athens.transit"
  [_format csv-str]
  (let [data (csv-data csv-str)
        conn (athens/create-conn)]
    (doseq [[uid title parent string order] data]
      (if (empty? title)
        (athens/add-block! conn uid parent string order)
        (athens/add-page! conn uid title)))
    (athens/conn-to-str conn)))

(defmethod convert-csv "roam.json"
  [_format csv-str]
  (let [data (csv-data csv-str)
        conn (roam/create-conn)]
    (doseq [[uid title parent string order create-user-time create-user-uid edit-user-time edit-user-uid] data]
      (if (empty? title)
        (roam/add-block! conn uid parent string order create-user-time create-user-uid edit-user-time edit-user-uid)
        (roam/add-page! conn uid title create-user-time create-user-uid edit-user-time edit-user-uid)))
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
          slurp
          xform
          (spit output-filename)))))

(defn -main [& args]
  (let [{:keys [options arguments summary]}             (cli/parse-opts args cli-options)
        {:keys [help pretty-print query extra convert]} options
        input-filename                                  (first arguments)]
    (cond
      help
      (print-help summary)

      (str/blank? input-filename)
      (println "Invalid filename, it must be non-empty")

      pretty-print
      (slurp-xform-spit input-filename ".pp.edn" (comp pprint/pprint read-db))

      convert
      (slurp-xform-spit input-filename (format-ext convert) (partial convert-csv convert))

      :else
      (slurp-xform-spit input-filename ".csv" (comp
                                               csv-str
                                               (partial roam-db->csv-table
                                                        (or query
                                                            (when extra :extra)
                                                            :simple))
                                               read-db)))))

(comment
  ;; Read the file as a Datascript database
  (def db (-> "./test-goldens/simple/backup.edn" slurp read-db))
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
