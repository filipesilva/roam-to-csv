(ns roam-to-csv.main
  (:require [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.data.csv :as csv]
            [clojure.tools.cli :as cli]
            [clojure.pprint :as pprint]
            [datascript.core :as d])
  (:import
   [java.io StringWriter])
  ;; Needed for standalone jar to invoke with java
  (:gen-class))

(def cli-options
  [["-h" "--help" "Show this message."]
   ["-p" "--pretty-print" "Pretty print the EDN export only."]])

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

(defn read-db [edn-string]
  (edn/read-string {:readers d/data-readers} edn-string))

(def pages-query
  '[:find ?page-uid ?title
    :where
    [?p :block/uid ?page-uid]
    [?p :node/title ?title]])

(def blocks-query
  '[:find  ?block-uid ?parent-uid ?string ?order
    :where
    [?b :block/uid ?block-uid]
    [?b :block/string ?string]
    [?b :block/order ?order]
    [?p :block/children ?b]
    [?p :block/uid ?parent-uid]])

(defn roam-edn->csv-table [edn-string]
  (let [db       (read-db edn-string)
        pages    (d/q pages-query db)
        blocks   (d/q blocks-query db)
        header   ["uid" "title" "parent" "string" "order"]
        no-title (fn [[uid & rest]] (apply vector uid nil rest))]
    (vec (concat [header]
                 pages
                 (map no-title blocks)))))

(defn pretty-print-edn [input-filename]
  (let [output-filename (str/replace input-filename #".edn$" ".pp.edn")
        string-writer   (StringWriter.)
        db              (-> input-filename slurp read-db)]
    (pprint/pprint db string-writer)
    (spit output-filename (str string-writer))))

(defn convert [input-filename]
  (let [output-filename (str/replace input-filename #".edn$" ".csv")
        string-writer   (StringWriter.)
        table           (-> input-filename slurp roam-edn->csv-table)]
    (csv/write-csv string-writer table)
    (spit output-filename (str string-writer))))

(defn -main [& args]
  (let [{:keys [options arguments summary]} (cli/parse-opts args cli-options)
        {:keys [help pretty-print]}         options
        input-filename                      (first arguments)]
    (cond
      help
      (print-help summary)

      (or (str/blank? input-filename)
          (not (str/ends-with? input-filename ".edn")))
      (println "Invalid filename, it must be non-empty and have .edn extension.")

      pretty-print
      (pretty-print-edn input-filename)

      :else
      (convert input-filename))))

(comment
  (-> "./backup.edn" slurp roam-edn->csv-table)
  (convert "./backup.edn")
  ;
  )
