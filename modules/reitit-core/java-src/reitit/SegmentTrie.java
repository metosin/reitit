package reitit;

import clojure.lang.Keyword;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

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

  static String encode(String s) {
    try {
      if (s.contains("%")) {
        String _s = s;
        if (s.contains("+")) {
          _s = s.replace("+", "%2B");
        }
        return URLEncoder.encode(_s, "UTF-8");
      }
    } catch (UnsupportedEncodingException ignored) {
    }
    return s;
  }

  public static class Match {
    public final Map<Keyword, String> params = new HashMap<>();
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

  private Matcher staticMatcher() {
    if (childs.size() == 1) {
      return new StaticMatcher(childs.keySet().iterator().next(), childs.values().iterator().next().matcher());
    } else {
      Map<String, Matcher> m = new HashMap<>();
      for (Map.Entry<String, Trie> e : childs.entrySet()) {
        m.put(e.getKey(), e.getValue().matcher());
      }
      return new StaticMapMatcher(m);
    }
  }

  public Matcher matcher() {
    Matcher m;
    if (!catchAll.isEmpty()) {
      m = new CatchAllMatcher(catchAll.keySet().iterator().next(), catchAll.values().iterator().next().data);
    } else if (!wilds.isEmpty()) {
      List<Matcher> matchers = new ArrayList<>();
      if (data != null) {
        matchers.add(new DataMatcher(data));
      }
      if (!childs.isEmpty()) {
        matchers.add(staticMatcher());
      }
      for (Map.Entry<Keyword, Trie> e : wilds.entrySet()) {
        matchers.add(new WildMatcher(e.getKey(), e.getValue().matcher()));
      }
      m = new LinearMatcher(matchers);
    } else if (!childs.isEmpty()) {
      m = staticMatcher();
    } else {
      return new DataMatcher(data);
    }
    if (data != null) {
      m = new LinearMatcher(Arrays.asList(new DataMatcher(data), m));
    }
    return m;
  }

  public interface Matcher {
    Match match(int i, List<String> segments, Match match);
  }

  public static final class StaticMatcher implements Matcher {
    private final String segment;
    private final Matcher child;

    StaticMatcher(String segment, Matcher child) {
      this.segment = segment;
      this.child = child;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      if (i < segments.size() && segment.equals(segments.get(i))) {
        return child.match(i + 1, segments, match);
      }
      return null;
    }

    @Override
    public String toString() {
      return "[\"" + segment + "\" " + child + "]";
    }
  }

  public static final class WildMatcher implements Matcher {
    private final Keyword parameter;
    private final Matcher child;

    WildMatcher(Keyword parameter, Matcher child) {
      this.parameter = parameter;
      this.child = child;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      final Match m = child.match(i + 1, segments, match);
      if (m != null) {
        m.params.put(parameter, encode(segments.get(i)));
        return m;
      }
      return null;
    }

    @Override
    public String toString() {
      return "[" + parameter + " " + child + "]";
    }
  }

  public static final class CatchAllMatcher implements Matcher {
    private final Keyword parameter;
    private final Object data;

    CatchAllMatcher(Keyword parameter, Object data) {
      this.parameter = parameter;
      this.data = data;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      match.params.put(parameter, encode(String.join("/", segments.subList(i, segments.size()))));
      match.data = data;
      return match;
    }

    @Override
    public String toString() {
      return "[" + parameter + " " + new DataMatcher(data) + "]";
    }
  }

  public static final class StaticMapMatcher implements Matcher {
    private final Map<String, Matcher> map;

    StaticMapMatcher(Map<String, Matcher> map) {
      this.map = map;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      final Matcher child = map.get(segments.get(i));
      if (child != null) {
        return child.match(i + 1, segments, match);
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append("{");
      List<String> keys = new ArrayList<>(map.keySet());
      for (int i = 0; i < keys.size(); i++) {
        String path = keys.get(i);
        Matcher value = map.get(path);
        b.append("\"").append(path).append("\" ").append(value);
        if (i < keys.size() - 1) {
          b.append(", ");
        }
      }
      b.append("}");
      return b.toString();
    }
  }

  public static final class LinearMatcher implements Matcher {

    private final List<Matcher> childs;

    LinearMatcher(List<Matcher> childs) {
      this.childs = childs;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      for (Matcher child : childs) {
        final Match m = child.match(i, segments, match);
        if (m != null) {
          return m;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return childs.toString();
    }
  }

  public static final class DataMatcher implements Matcher {
    private final Object data;

    DataMatcher(Object data) {
      this.data = data;
    }

    @Override
    public Match match(int i, List<String> segments, Match match) {
      if (i == segments.size()) {
        match.data = data;
        return match;
      }
      return null;
    }

    @Override
    public String toString() {
      return (data != null ? data.toString() : "null");
    }
  }

  public static Match lookup(Matcher matcher, String path) {
    return matcher.match(0, split(path), new Match());
  }

  public static Matcher sample() {
    Map<String, Matcher> m1 = new HashMap<>();
    m1.put("profile", new WildMatcher(Keyword.intern("type"), new DataMatcher(1)));
    m1.put("permissions", new DataMatcher(2));

    Map<String, Matcher> m2 = new HashMap<>();
    m2.put("user", new WildMatcher(Keyword.intern("id"), new StaticMapMatcher(m1)));
    m2.put("company", new WildMatcher(Keyword.intern("cid"), new StaticMatcher("dept", new WildMatcher(Keyword.intern("did"), new DataMatcher(3)))));
    m2.put("public", new CatchAllMatcher(Keyword.intern("*"), 4));
    m2.put("kikka", new LinearMatcher(Arrays.asList(new StaticMatcher("ping", new DataMatcher(5)), new WildMatcher(Keyword.intern("id"), new StaticMatcher("ping", new DataMatcher(6))))));
    return new StaticMapMatcher(m2);
  }

  public static void main(String[] args) {

    Trie trie = new Trie();
    //trie.add("/kikka/:id/permissions", 1);
    trie.add("/kikka/:id", 2);
    trie.add("/kakka/ping", 3);
    Matcher m = trie.matcher();
    System.err.println(m);
    System.out.println(lookup(m, "/kikka/1/permissions"));
    System.out.println(lookup(m, "/kikka/1"));

    /*
    Trie trie = new Trie();
    trie.add("/user/:id/profile/:type", 1);
    trie.add("/user/:id/permissions", 2);
    trie.add("/company/:cid/dept/:did", 3);
    trie.add("/this/is/a/static/route", 4);
    Matcher m = trie.matcher();
    System.out.println(m);

    System.err.println(lookup(m, "/this/is/a/static/route"));
    System.err.println(lookup(m, "/user/1234/profile/compact"));
    System.err.println(lookup(m, "/company/1234/dept/5678"));
    System.err.println();
    */
    /*
    System.err.println(lookup(sample(), "/user/1234/profile/compact"));
    System.err.println(lookup(sample(), "/public/images/logo.jpg"));
    System.err.println(lookup(sample(), "/kikka/ping"));
    System.err.println(lookup(sample(), "/kikka/kukka/ping"));
    */
  }
}
