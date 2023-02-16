(ns roam-to-csv.compat
  (:refer-clojure :exclude [slurp spit])
  (:require
   #?@(:clj  [[clojure.data.csv :as csv]
              [clojure.data.json :as json]]
       :cljs [[clojure.string :as str]
              [testdouble.cljs.csv :as csv]
              [cljs-node-io.core :as io]]))
  #?(:clj (:import [java.io StringWriter])))

(defn to-json [x]
  ;; json/write-str escapes slash and unicode by default, but js/JSON.stringify doesn't
  ;; https://stackoverflow.com/questions/1580647/json-why-are-forward-slashes-escaped
  ;; Matching js/JSON.stringify is the right approach here because the tools we target
  ;; are JS native.
  #?(:clj (json/write-str x :escape-slash false :escape-unicode false)
     :cljs (-> x clj->js js/JSON.stringify)))

(defn csv-str [data]
  #?(:clj (let [writer (StringWriter.)]
            (csv/write-csv writer data)
            (str writer))
     :cljs (str (csv/write-csv data) "\n")))

(def read-csv
  #?(:clj csv/read-csv
     :cljs (fn [data & options]
             (csv/read-csv data
                           (if (and (-> options :newline not)
                                    (str/ends-with? (re-find #"^.*\r?\n" data) "\r\n"))
                             (assoc options :newline :cr+lf)
                             options)))))

(def slurp #?(:clj clojure.core/slurp :cljs io/slurp))

(def spit #?(:clj clojure.core/spit :cljs io/spit))

(defn parse-int [x]
  #?(:clj (try
            (Integer/parseInt x)
            (catch Exception _))
     :cljs (let [r (js/parseInt x)]
             (when-not (js/Number.isNaN r)
               r))))
