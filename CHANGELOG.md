## 0.1.1-SNAPSHOT

### `reitit-core`

* `match-by-path` encodes parameters into strings using (internal) `reitit.impl/IntoString` protocol. Handles all of: numbers, keywords, booleans, uuids. Fixes [#75](https://github.com/metosin/reitit/issues/75).

```clj
(require '[reitit.core :as r])

(r/match-by-name
  (r/router
    ["/coffee/:type" ::coffee])
  ::coffee
  {:type :luwak})
;#Match{:template "/coffee/:type",
;       :data {:name :user/coffee},
;       :result nil,
;       :path-params {:type "luwak"},
;       :path "/coffee/luwa"}
```

## 0.1.0 (2018-2-19)

* First release
