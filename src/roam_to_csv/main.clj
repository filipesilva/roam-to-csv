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
        {:keys [help pretty-print]}         options
        input-filename                      (first arguments)]
    (cond
      help
      (print-help summary)

      (or (str/blank? input-filename)
          (not (str/ends-with? input-filename ".edn")))
      (println "Invalid filename, it must be non-empty and have .edn extension.")

      pretty-print
      (slurp-convert-spit input-filename ".pp.edn" read-db pprint/pprint)

      :else
      (slurp-convert-spit input-filename ".csv" roam-edn->csv-table write-csv))))

(comment
  (-> "./backup.edn" slurp roam-edn->csv-table)
  (slurp-convert-spit "./backup.edn" ".csv" roam-edn->csv-table write-csv)
  ;
  )
