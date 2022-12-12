(ns roam-to-csv.roam
  (:require [datascript.core :as d]
            [clojure.data.json :as json]
            [clojure.walk :as walk]))

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

(def orphans-page-uid "orphans")
(def orphans-page-title "orphans")

(defn merge-time-and-user
  [x [create-user-time create-user-uid edit-user-time edit-user-uid]]
  (merge x
         (when create-user-time
           {:create/time create-user-time})
         (when create-user-uid
           {:create/user {:user/uid create-user-uid}})
         (when edit-user-time
           {:edit/time edit-user-time})
         (when edit-user-uid
           {:edit/user {:user/uid edit-user-uid}})))

(defn add-block! [conn block-uid parent-uid string order & rest]
  (let [parent-uid' (:block/uid
                     (or (d/entity @conn [:block/uid parent-uid])
                         ;; NB: this page is created in create-conn
                         (d/entity @conn [:block/uid orphans-page-uid])))]
    (d/transact! conn [{:block/uid      parent-uid'
                        :block/children [(merge-time-and-user
                                          {:block/uid    block-uid
                                           :block/string string
                                           :block/order  order
                                           ;; Would be better if we actually had this information.
                                           :block/open   true}
                                          rest)]}])))

(defn add-page! [conn page-uid page-title & rest]
  (d/transact! conn [(merge-time-and-user
                      {:node/title page-title
                       :block/uid  page-uid}
                      rest)]))

(defn create-conn []
  (let [conn (d/create-conn schema)]
    ;; We put all orphans in a special page.
    (add-page! conn orphans-page-uid orphans-page-title)
    conn))

(defn conn-to-json [conn]
  (let [db                 @conn
        page-eids          (->> (d/datoms @conn :avet :node/title) (map :e))
        with-children      (d/pull-many
                            db
                            '[:node/title :block/uid :block/string
                              :create/time :edit/time
                              {:create/user [:user/uid]}
                              {:edit/user [:user/uid]}
                              {:block/children ...}]
                            page-eids)
        replacements       {:block/string   "string"
                            :node/title     "title"
                            :block/uid      "uid"
                            :block/children "children"
                            :create/time    "create-time"
                            :edit/time      "edit/time"
                            :create/user    ":create/user"
                            :edit/user      ":edit/user"
                            :user/uid       ":user/uid"}
        with-renamed-props (walk/postwalk-replace replacements with-children)
        as-json            (json/write-str with-renamed-props)]
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
