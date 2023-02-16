(ns roam-to-csv.roam
  (:require [roam-to-csv.compat :as compat]
            [datascript.core :as d]
            [clojure.walk :as walk]
            [tick.alpha.api :as t]))

(def schema
  {:block/uid      {:db/unique :db.unique/identity}
   :node/title     {:db/unique :db.unique/identity}
   :block/children {:db/cardinality :db.cardinality/many
                    :db/valueType   :db.type/ref}
   :create/user    {:db/cardinality :db.cardinality/one
                    :db/valueType   :db.type/ref}
   :edit/user      {:db/cardinality :db.cardinality/one
                    :db/valueType   :db.type/ref}
   :user/uid       {:db/unique :db.unique/identity}})

(defn str->ms [s]
  (-> s
      t/instant
      #?(:clj  java.util.Date/from
         :cljs js/Date.)
      inst-ms))

(defn merge-time-and-user
  [x [create-time create-user-uid edit-time edit-user-uid]]
  (merge x
         (when create-time
           (try
             {:create/time (str->ms create-time)}
             (catch #?(:clj Exception :cljs :default) _)))
         (when create-user-uid
           {:create/user {:user/uid create-user-uid}})
         (when edit-time
           (try
             {:edit/time (str->ms edit-time)}
             (catch #?(:clj Exception :cljs :default) _)))
         (when edit-user-uid
           {:edit/user {:user/uid edit-user-uid}})))

(defn add-block! [conn block-uid parent-uid string order & rest]
  (d/transact! conn [{:block/uid      parent-uid
                      :block/children [(merge-time-and-user
                                        {:block/uid    block-uid
                                         :block/string (or string "")
                                         :block/order  (or order 0)
                                         ;; Would be better if we actually had this information.
                                         :block/open   true}
                                        rest)]}]))

(defn add-page! [conn page-uid page-title & rest]
  (d/transact! conn [(merge-time-and-user
                      {:node/title page-title
                       :block/uid  page-uid}
                      rest)]))

(defn create-conn []
  (d/create-conn schema))

(defn sort-children
  [{:keys [block/children] :as x}]
  (if children
    (assoc x :block/children (->> children
                                  (sort-by :block/order)
                                  (map #(dissoc % :block/order))))
    x))

(defn conn-to-json [conn]
  (let [db                    @conn
        page-eids             (->> (d/datoms @conn :avet :node/title) (map :e))
        with-children         (d/pull-many
                               db
                               '[:node/title :block/uid :block/string :block/order
                                 :create/time :edit/time
                                 {:create/user [:user/uid]}
                                 {:edit/user [:user/uid]}
                                 {:block/children ...}]
                               page-eids)
        with-ordered-children (walk/postwalk sort-children with-children)
        replacements          {:block/string   "string"
                               :node/title     "title"
                               :block/uid      "uid"
                               :block/children "children"
                               :create/time    "create-time"
                               :edit/time      "edit-time"
                               :create/user    ":create/user"
                               :edit/user      ":edit/user"
                               :user/uid       ":user/uid"}
        with-renamed-props    (walk/postwalk-replace replacements with-ordered-children)
        as-json               (compat/to-json with-renamed-props)]
    as-json))

(comment
  (let [conn (create-conn)]
    (add-page! conn "page-one" "Page One")
    (add-block! conn "block-one" "page-one" "Block One" 0)
    (add-block! conn "block-two" "page-one" "Block Two" 1)
    (add-block! conn "block-one-one" "block-one" "Block One One" 0)
    (add-block! conn "block-three" "page-missing" "Block Three" 0 123 "user-uid-1" 456 "user-uid-2")
    (conn-to-json conn))

 ;;
  )