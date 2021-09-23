(ns roam-to-csv.main
  (:require [clojure.string :as str]
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

(def cli-options
  [["-q" "--query QUERY" "Use a custom Datalog query to create the CSV."
    :parse-fn read-query]
   ["-p" "--pretty-print" "Pretty print the EDN export only."]
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
    [?p :create/time ?create-time]])

(def blocks-query
  '[:find ?block-uid ?parent-uid ?string ?order ?create-time
    :where
    [?b :block/uid ?block-uid]
    [?b :block/string ?string]
    [?b :block/order ?order]
    [?p :block/children ?b]
    [?p :block/uid ?parent-uid]
    [?b :create/time ?create-time]])

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

(defn roam-edn->csv-table
  "Read edn-string as a database, and return a vector of vectors with the output of a query.
   If no query is provided, outputs blocks and pages."
  ([edn-string]
   (let [db       (read-db edn-string)
         pages    (d/q pages-query db)
         blocks   (d/q blocks-query db)
         header   ["uid" "title" "parent" "string" "order" "create-time"]]
     (vec (concat [header]
                  (map page-csv pages)
                  (map block-csv blocks)))))
  ([query edn-string]
   (let [db       (read-db edn-string)
         res      (d/q query db)
         header   (query->header query)]
     (into [header] (map vec res)))))

(defn write-csv [data writer]
  (csv/write-csv writer data))

(defn slurp-convert-spit [input-filename new-ext read-fn write-fn]
  (let [output-filename (str/replace input-filename #".edn$" new-ext)
        string-writer   (StringWriter.)
        data            (-> input-filename slurp read-fn)]
    (write-fn data string-writer)
    (spit output-filename (str string-writer))))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [help pretty-print query]}   options
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
  )
