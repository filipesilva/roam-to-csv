(ns roam-to-csv.main
  (:require [roam-to-csv.athens :as athens]
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

(defn read-query [edn-string]
  (edn/read-string edn-string))

;; TODO:
;; --query-file
;; --query-params
(def cli-options
  [["-e" "--extra" "Include extra information, like edit time and user."]
   ["-q" "--query QUERY" "Use a custom Datalog query to create the CSV."
    :parse-fn read-query]
   ["-p" "--pretty-print" "Pretty print the EDN export only."]
   ["-c" "--convert" "Convert an input csv to another format [athens.transit]"]
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

(def pages-query
  '[:find ?page-uid ?title ?create-time
    :where
    [?p :block/uid ?page-uid]
    [?p :node/title ?title]
    [(get-else $ ?p :create/time 0) ?create-time]])

(def blocks-query
  '[:find ?block-uid ?parent-uid ?string ?order ?create-time
    :where
    [?b :block/uid ?block-uid]
    [?b :block/string ?string]
    [?b :block/order ?order]
    [?p :block/children ?b]
    [?p :block/uid ?parent-uid]
    [(get-else $ ?p :create/time 0) ?create-time]])

(def blocks-extra-query
  '[:find ?block-uid ?parent-uid ?string ?order
          ?create-time ?create-user-uid ?create-user-display-name
          ?edit-time ?edit-user-uid ?edit-user-display-name
    :where
    [?b :block/uid ?block-uid]
    [?b :block/string ?string]
    [?b :block/order ?order]
    [?p :block/children ?b]
    [?p :block/uid ?parent-uid]
    [(get-else $ ?p :create/time 0) ?create-time]
    [(get-else $ ?p :create/user 0) ?create-user]
    [(get-else $ ?create-user :user/uid "") ?create-user-uid]
    [(get-else $ ?create-user :user/display-name "") ?create-user-display-name]
    [(get-else $ ?p :edit/time 0) ?edit-time]
    [(get-else $ ?p :edit/user 0) ?edit-user]
    [(get-else $ ?edit-user :user/uid "") ?edit-user-uid]
    [(get-else $ ?edit-user :user/display-name "") ?edit-user-display-name]])

(defn page-csv [[uid title create-time]]
  [uid title nil nil nil (format-ms create-time)])

(defn block-csv [[uid parent string order create-time]]
  [uid nil parent string order (format-ms create-time)])

(defn query->header
  "Extract the :find bindings as strings, stripped of the initial ? if any."
  [query]
  (->> (partition-by keyword? query)
       second
       (map str)
       (map #(if (str/starts-with? % "?") (subs % 1) %))
       vec))

(def default-header ["uid" "title" "parent" "string" "order" "create-time"])

(defn roam-edn->csv-table
  "Read edn-string as a database, and return a vector of vectors with the output of a query.
   If no query is provided, outputs blocks and pages."
  ([edn-string]
   (let [db       (read-db edn-string)
         pages    (d/q pages-query db)
         blocks   (d/q blocks-query db)]
     (vec (concat [default-header]
                  (map page-csv pages)
                  (map block-csv blocks)))))
  ([query edn-string]
   (let [db       (read-db edn-string)
         res      (d/q query db)
         header   (query->header query)]
     (into [header] (map vec res)))))

(defn convert-csv [_format csv-str]
  ;; Only supports athens.transit format now
  (let [csv    (csv/read-csv csv-str)
        header (first csv)
        data   (rest csv)]
    (if-not (= header default-header)
      (do
        (println "Unsupported header format for conversion")
        (println (str "  got " header))
        (println (str "  but only support " default-header))
        ;; TODO: really should have force exit with error codes.
        "")
      (let [conn (athens/create-conn)]
        (doseq [[uid title parent string order] data]
          (if (empty? title)
            (athens/add-block! conn uid parent string order)
            (athens/add-page! conn uid title)))
        (athens/conn-to-str conn)))))

(defn write-csv [data writer]
  (csv/write-csv writer data))

(defn write-str [data writer]
  (.write writer data))

(defn slurp-convert-spit [input-filename new-ext read-fn write-fn]
  (let [filename-without-ext (subs input-filename 0 (str/last-index-of input-filename "."))
        output-filename      (str filename-without-ext new-ext)
        string-writer        (StringWriter.)
        data                 (-> input-filename slurp read-fn)]
    ;; TODO: this writer-fn thing is super weird, should be operating just on strings instead.
    (write-fn data string-writer)
    (spit output-filename (str string-writer))))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [help pretty-print query extra
                convert]}                   options
        input-filename                      (first arguments)]
    (cond
      help
      (print-help summary)

      (or (str/blank? input-filename)
          (not (str/ends-with? input-filename ".edn")))
      (println "Invalid filename, it must be non-empty and have .edn extension.")

      pretty-print
      (slurp-convert-spit input-filename ".pp.edn" read-db pprint/pprint)

      query
      (slurp-convert-spit input-filename ".csv"  (partial roam-edn->csv-table (:query options)) write-csv)


      extra
      (slurp-convert-spit input-filename ".csv"  (partial roam-edn->csv-table blocks-extra-query) write-csv)

      convert
      ;; Only supports athens.transit format now
      (slurp-convert-spit input-filename ".transit" (partial convert-csv convert) write-str)

      :else
      (slurp-convert-spit input-filename ".csv" roam-edn->csv-table write-csv))))

(comment
  ;; Read the file as a Datascript database
  (-> "./backup.edn" slurp read-db)

  ;; Basic query
  (-> "./backup.edn" slurp roam-edn->csv-table)

  ;; Custom query for uids+blocks
  (->> "./backup.edn" slurp 
      (roam-edn->csv-table
       '[:find ?uid ?string
         :where
         [?b :block/uid ?uid]
         [?b :block/string ?string]]))
  
  ; Custom query for all attrs
  (->> "./backup.edn" slurp
       (roam-edn->csv-table
        '[:find (distinct ?attr)
          :where
          [?e ?attr]]))

  ;; Output basic query as CSV
  (slurp-convert-spit "./backup.edn" ".csv" roam-edn->csv-table write-csv)

  ;; Output a query as CSV
  (slurp-convert-spit "./backup.edn" ".csv" (partial roam-edn->csv-table blocks-extra-query) write-csv)

  ;; Convert to athens transit
  (slurp-convert-spit "./backup.csv" ".transit" (partial convert-csv "athens.transit") write-str)

  ;;
  )
