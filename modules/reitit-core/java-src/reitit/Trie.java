package reitit;

import clojure.lang.Keyword;

import java.util.*;

import static java.util.Arrays.asList;

public class Trie {

  public static ArrayList<String> split(final String path) {
    final ArrayList<String> segments = new ArrayList<>(4);
    final int size = path.length();
    int start = 1;
    for (int i = start; i < size; i++) {
      final char c = path.charAt(i);
      if (c == '/') {
        segments.add(path.substring(start, i));
        start = i + 1;
      }
    }
    segments.add(path.substring(start, size));
    return segments;
  }

  public static class Match {
    public Map<Keyword, String> params = new HashMap<>();
    public Object data;

    @Override
    public String toString() {
      Map<Object, Object> m = new HashMap<>();
      m.put(Keyword.intern("data"), data);
      m.put(Keyword.intern("params"), params);
      return m.toString();
    }
  }

  private Map<String, Trie> childs = new HashMap<>();
  private Map<Keyword, Trie> wilds = new HashMap<>();
  private Map<Keyword, Trie> catchAll = new HashMap<>();
  private Object data;

  @Override
  public String toString() {
    Map<Object, Object> m = new HashMap<>();
    m.put(Keyword.intern("childs"), childs);
    m.put(Keyword.intern("wilds"), wilds);
    m.put(Keyword.intern("catchAll"), catchAll);
    m.put(Keyword.intern("data"), data);
    return m.toString();
  }

  public static Match lookup(Trie root, String path) {
    return lookup(root, new Match(), split(path));
  }

  private static Match lookup(Trie root, Match match, List<String> parts) {
    Trie childTrie = null;
    if (parts.isEmpty()) {
      return match;
    } else {
      Trie trie = root;
      int i = 0;
      for (final String part : parts) {
        i++;
        childTrie = trie.childs.get(part);
        if (childTrie != null) {
          trie = childTrie;
        } else {
          for (final Map.Entry<Keyword, Trie> e : trie.wilds.entrySet()) {
            childTrie = e.getValue();
            match.data = childTrie.data;
            Match m = lookup(childTrie, match, parts.subList(i, parts.size()));
            if (m != null) {
              match.params.put(e.getKey(), part);
              return m;
            }
          }
          for (Map.Entry<Keyword, Trie> e : trie.catchAll.entrySet()) {
            childTrie = e.getValue();
            match.params.put(e.getKey(), String.join("/", parts.subList(i - 1, parts.size())));
            match.data = childTrie.data;
            return match;
          }
          break;
        }
      }
    }
    if (childTrie != null) {
      match.data = childTrie.data;
      return match;
    }
    return null;
  }

  public Trie add(String path, Object data) {
    List<String> paths = split(path);
    Trie pointer = this;
    for (String p : paths) {
      if (p.startsWith(":")) {
        Keyword k = Keyword.intern(p.substring(1));
        Trie s = pointer.wilds.get(k);
        if (s == null) {
          s = new Trie();
          pointer.wilds.put(k, s);
        }
        pointer = s;
      } else if (p.startsWith("*")) {
        Keyword k = Keyword.intern(p.substring(1));
        Trie s = pointer.catchAll.get(k);
        if (s == null) {
          s = new Trie();
          pointer.catchAll.put(k, s);
        }
        break;
      } else {
        Trie s = pointer.childs.get(p);
        if (s == null) {
          s = new Trie();
          pointer.childs.put(p, s);
        }
        pointer = s;
      }
    }
    pointer.data = data;
    return this;
  }

  }

  public static void main(String[] args) {
    Trie trie =
            new Trie()
                    .add("/kikka", 1)
                    .add("/kakka", 2)
                    .add("/api/ping", 3)
                    .add("/api/pong", 4)
                    .add("/api/ipa/ping", 5)
                    .add("/api/ipa/pong", 6)
                    .add("/users/:user-id", 7)
                    .add("/users/:user-id/orders", 8)
                    .add("/users/:user-id/price", 9)
                    .add("/orders/:id/price", 10)
                    .add("/orders/:super", 11)
                    .add("/orders/:super/hyper/:giga", 12);

    //System.out.println(lookup(trie, split("/kikka")));
    System.out.println(lookup(trie, "/orders/mies/hyper/peikko"));

    System.out.println(lookup(
            new Trie().add("/user/:id/profile/:type/", 1),
            "/user/1/profile/compat"));

    System.out.println(lookup(
            new Trie().add("/user/*path", 1),
            "/user/1/profile/compat"));
  }
}
