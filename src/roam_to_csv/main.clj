(ns roam-to-csv.main
  (:require [clojure.tools.cli :as cli]
            [datascript.core :as d])
  ;; Needed for standalone jar to invoke with java
  (:gen-class))

(def cli-options
  [["-h" "--help" "Show this message."]])

(defn -main [& args]
  (let [parsed (cli/parse-opts args cli-options)
        {:keys [:help]}
        (:options parsed)]
    (if help
      (do
        (println "Convert a Roam Research EDN export into CSV format.")
        (println)
        (println "Usage:")
        (println "  roam-to-csv backup.edn")
        (println)
        (println "Options:")
        ;; Tools.cli provides a :summary key that can be used for printing:
        (println (:summary parsed)))
      (let []
        (println "Hello world!")
        #_(prn (func
              ;; *in* represents a reader from stdin in Clojure
              ;; json/read can handle readers
              ;; :key-fn transforms JSON string keys
              (json/read *in* :key-fn key-fn)))))))

