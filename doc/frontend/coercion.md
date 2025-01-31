# Frontend coercion

The Reitit frontend leverages [coercion](../coercion/coercion.md) for path,
query, and fragment parameters. The coercion uses the input schema defined
in the match data under `:parameters`.

## Behavior of Coercion

1. **Route Matching**
   When matching a route from a path, the resulting match will include the
   coerced values (if coercion is enabled) under `:parameters`. If coercion is
   disabled, the parsed string values are stored in the same location.
   The original un-coerced values are always available under `:path-params`,
   `:query-params`, and `:fragment` (a single string).

2. **Creating Links and Navigating**
   When generating a URL (`href`) or navigating (`push-state`, `replace-state`, `navigate`)
   to a route, coercion can be
   used to encode query-parameter values into strings. This happens before
   Reitit performs basic URL encoding on the values. This feature is
   especially useful for handling the encoding of specific types, such as
   keywords or dates, into strings.

3. **Updating current query parameters**
  When using `set-query` to modify current query parameters, Reitit frontend
  first tries to find a match for the current path so the match can be used to
  first decode query parameters and then to encode them. If the current path
  doesn't match the routing tree, `set-query` keeps all the query parameter
  values as strings.

## Notes

- **Value Encoding Support**: Only Malli supports value encoding.
- **Limitations**: Path parameters and fragment values are not encoded using
  the match schema.

## Example

```cljs
(def router (r/router ["/"
                       ["" ::frontpage]
                       ["bar"
                        {:name ::bar
                         :coercion rcm/coercion
                         :parameters {:query [:map
                                              [:q {:optional true}
                                               [:keyword
                                                {:decode/string (fn [s] (keyword (subs s 2)))
                                                 :encode/string (fn [k] (str "__" (name k)))}]]]}}]]))

(rfe/href ::bar {} {:q :hello})
;; Result "/bar?q=__hello", the :q value is first encoded

(rfe/push-state ::bar {} {:q :world})
;; Result "/bar?q=__world"
;; The current match will contain both the original value and parsed & decoded parameters:
;; {:query-params {:q "__world"} 
;;  :parameters {:query {:q :world}}}
```
