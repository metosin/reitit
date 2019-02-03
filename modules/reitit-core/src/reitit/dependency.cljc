(ns reitit.dependency
  "Dependency resolution for middleware/interceptors.")

(defn- providers
  "Map from provision key to provider. `get-provides` should return the provision keys of a dependent."
  [get-provides nodes]
  (reduce (fn [acc dependent]
            (into acc
                  (map (fn [provide]
                         (when (contains? acc provide)
                           (throw (ex-info (str "multiple providers for: " provide)
                                           {::multiple-providers provide})))
                         [provide dependent]))
                  (get-provides dependent)))
          {} nodes))

(defn- get-provider
  "Get the provider for `k`, throw if no provider can be found for it."
  [providers k]
  (if (contains? providers k)
    (get providers k)
    (throw (ex-info (str "provider missing for dependency: " k)
                    {::missing-provider k}))))

(defn post-order
  "Put `nodes` in post-order. Can also be described as a reverse topological sort.
  `get-provides` and `get-requires` are callbacks that you can provide to compute the provide and depend
  key sets of nodes, the defaults are `:provides` and `:requires`."
  ([nodes] (post-order :provides :requires nodes))
  ([get-provides get-requires nodes]
   (let [providers-by-key (providers get-provides nodes)]
     (letfn [(toposort [node path colors]
               (case (get colors node)
                 :white (let [requires (get-requires node)
                              [nodes* colors] (toposort-seq (map (partial get-provider providers-by-key) requires)
                                                            (conj path node)
                                                            (assoc colors node :grey))]
                          [(conj nodes* node)
                           (assoc colors node :black)])
                 :grey (throw (ex-info "circular dependency"
                                       {:cycle (drop-while #(not= % node) (conj path node))}))
                 :black [() colors]))

             (toposort-seq [nodes path colors]
               (reduce (fn [[nodes* colors] node]
                         (let [[nodes** colors] (toposort node path colors)]
                           [(into nodes* nodes**) colors]))
                       [[] colors] nodes))]

       (first (toposort-seq nodes [] (zipmap nodes (repeat :white))))))))
