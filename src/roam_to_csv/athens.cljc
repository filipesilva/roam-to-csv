(ns roam-to-csv.athens
  (:require [datascript.core :as d]
            [datascript.transit :as dt]))

(def schema
  {:schema/version      {}
   :block/uid           {:db/unique :db.unique/identity}
   :node/title          {:db/unique :db.unique/identity}
   :attrs/lookup        {:db/cardinality :db.cardinality/many}
   :block/children      {:db/cardinality :db.cardinality/many
                         :db/valueType   :db.type/ref}
   :block/refs          {:db/cardinality :db.cardinality/many
                         :db/valueType   :db.type/ref}})

(def orphans-page-uid "orphans")
(def orphans-page-title "orphans")


(defn add-block! [conn block-uid parent-uid string order]
  (let [parent-uid' (:block/uid
                     (or (d/entity @conn [:block/uid parent-uid])
                         ;; NB: this page is created in create-conn
                         (d/entity @conn [:block/uid orphans-page-uid])))]
    (d/transact! conn [{:block/uid      parent-uid'
                        :block/children [{:block/uid    block-uid
                                          :block/string string
                                          :block/order  order
                                          ;; Would be better if we actually had this information.
                                          :block/open   true}]}])))


(defn add-page! [conn page-uid page-title]
  (d/transact! conn [{:node/title page-title
                      :block/uid  page-uid}]))


(defn create-conn []
  (let [conn (d/create-conn schema)]
    ;; We put all orphans in a special page.
    (add-page! conn orphans-page-uid orphans-page-title)
    conn))


(defn conn-to-str [conn]
  (dt/write-transit-str @conn))


(comment
 (let [conn (create-conn)]
   (add-page! conn "page-one" "Page One")
   (add-block! conn "block-one" "page-one" "Block One" 0)
   (add-block! conn "block-two" "page-one" "Block Two" 1)
   (add-block! conn "block-one-one" "block-one" "Block One One" 0)
   (add-block! conn "block-three" "page-missing" "Block Three" 0)
   (conn-to-str conn)
   (d/datoms @conn :eavt))

 ;;
  )
