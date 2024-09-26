(ns simplemono.schema-migration.core
  (:require [simplemono.schema-migration.topo-sort :as topo-sort]
            [datomic.api :as d]
            ))

(defn migrations-graph
  [migrations]
  (into {}
        (map (fn [[k v]]
               (let [dependencies (or (:dependencies v)
                                      #{})]
                 (assert (keyword? k))
                 (assert (set? dependencies))
                 [k dependencies])))
        migrations))

(defn apply-order
  [migrations]
  (reverse
    (topo-sort/kahn-sort (migrations-graph migrations))))

(defn ensure-meta-schema!
  [{:keys [datomic/con]}]
  (let [db (d/db con)]
    (when-not (d/entid db
                       :schema/applied-migrations)
      @(d/transact
         con
         [{:db/ident :schema/applied-migrations
           :db/valueType :db.type/keyword
           :db/cardinality :db.cardinality/many
           :db/doc "Stores the keyword ids of the migrations that have been applied to the current Datomic database. The attribute is asserted to entity `0`."
           }]))))

(defn migrate!
  [{:keys [datomic/migrations] :as w}]
  (ensure-meta-schema! w)
  (let [con (:datomic/con w)]
    (loop [migration-keywords (apply-order migrations)]
      (when-let [migration-keyword (first migration-keywords)]
        (d/sync con)
        (let [db (d/db con)
              applied-migrations (:schema/applied-migrations
                                  (d/entity db
                                            0))]
          (when-not (contains? applied-migrations
                               migration-keyword)
            (let [tx-fn (get-in migrations
                                [migration-keyword
                                 :tx-fn])
                  tx (:datomic/tx (tx-fn (assoc w
                                                :datomic/db
                                                db)))]
              @(d/transact con
                           (cons
                             [:db/add 0 :schema/applied-migrations migration-keyword]
                             tx))))
          (recur (rest migration-keywords))
          )))))

(comment
  (def migrations
    {:a {:dependencies #{}
         :tx-fn (fn [w]
                  (assoc w
                         :datomic/tx
                         [{:db/ident :example/a
                           :db/valueType :db.type/string
                           :db/cardinality :db.cardinality/many
                           }]))}
     :b {:dependencies #{:a}
         :tx-fn (fn [w]
                  (assoc w
                         :datomic/tx
                         [{:db/ident :example/b
                           :db/valueType :db.type/string
                           :db/cardinality :db.cardinality/many
                           }]))}

     })

  (migrations-graph migrations)

  (apply-order migrations)

  (def example-con
    (let [uri (str "datomic:mem://"
                   (random-uuid))
          _ (d/create-database uri)]
      (d/connect uri)))

  (migrate! {:datomic/con example-con
             :datomic/migrations migrations})

  (d/q '[:find
         ?ident
         :where
         [_ :db/ident ?ident]]
       (d/db example-con))

  (:schema/applied-migrations
   (d/entity (d/db example-con)
             0))
  )
