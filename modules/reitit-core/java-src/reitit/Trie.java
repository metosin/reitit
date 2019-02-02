package reitit;

// https://www.codeproject.com/Tips/1190293/Iteration-Over-Java-Collections-with-High-Performa

import clojure.lang.IPersistentMap;
import clojure.lang.ITransientMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

public class Trie {

  private static String decode(char[] chars, int begin, int end, boolean hasPercent, boolean hasPlus) {
    final String s = new String(chars, begin, end - begin);
    try {
      if (hasPercent) {
        return URLDecoder.decode(hasPlus ? s.replace("+", "%2B") : s, "UTF-8");
      }
    } catch (UnsupportedEncodingException ignored) {
    }
    return s;
  }

  private static String decode(char[] chars, int begin, int end) {
    boolean hasPercent = false;
    boolean hasPlus = false;
    for (int j = begin; j < end; j++) {
      final char c = chars[j];
      if (c == '%') {
        hasPercent = true;
      } else if (c == '+') {
        hasPlus = true;
      }
    }
    return decode(chars, begin, end, hasPercent, hasPlus);
  }

  public static class Match {
    final ITransientMap params = PersistentArrayMap.EMPTY.asTransient();
    public Object data;

    public IPersistentMap parameters() {
      return params.persistent();
    }

    @Override
    public String toString() {
      Map<Object, Object> m = new HashMap<>();
      m.put(Keyword.intern("data"), data);
      m.put(Keyword.intern("params"), parameters());
      return m.toString();
    }
  }

  public interface Matcher {
    Match match(int i, int max, char[] path, Match match);

    int depth();
  }

  public static StaticMatcher staticMatcher(String path, Matcher child) {
    return new StaticMatcher(path, child);
  }

  static class StaticMatcher implements Matcher {
    private final Matcher child;
    private final char[] path;
    private final int size;

    StaticMatcher(String path, Matcher child) {
      this.path = path.toCharArray();
      this.size = path.length();
      this.child = child;
    }

    @Override
    public Match match(int i, int max, char[] path, Match match) {
      if (max < i + size) {
        return null;
      }
      for (int j = 0; j < size; j++) {
        if (path[j + i] != this.path[j]) {
          return null;
        }
      }
      return child.match(i + size, max, path, match);
    }

    @Override
    public int depth() {
      return child.depth() + 1;
    }

    @Override
    public String toString() {
      return "[\"" + new String(path) + "\" " + child + "]";
    }
  }

  public static DataMatcher dataMatcher(Object data) {
    return new DataMatcher(data);
  }

  static final class DataMatcher implements Matcher {
    private final Object data;

    DataMatcher(Object data) {
      this.data = data;
    }

    @Override
    public Match match(int i, int max, char[] path, Match match) {
      if (i == max) {
        match.data = data;
        return match;
      }
      return null;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public String toString() {
      return (data != null ? data.toString() : "nil");
    }
  }

  public static WildMatcher wildMatcher(Keyword parameter, Matcher child) {
    return new WildMatcher(parameter, child);
  }

  static final class WildMatcher implements Matcher {
    private final Keyword key;
    private final Matcher child;

    WildMatcher(Keyword key, Matcher child) {
      this.key = key;
      this.child = child;
    }

    @Override
    public Match match(int i, int max, char[] path, Match match) {
      if (i < max && path[i] != '/') {
        boolean hasPercent = false;
        boolean hasPlus = false;
        for (int j = i; j < max; j++) {
          final char c = path[j];
          if (c == '/') {
            final Match m = child.match(j, max, path, match);
            if (m != null) {
              m.params.assoc(key, decode(path, i, j, hasPercent, hasPlus));
            }
            return m;
          } else if (c == '%') {
            hasPercent = true;
          } else if (c == '+') {
            hasPlus = true;
          }
        }
        final Match m = child.match(max, max, path, match);
        if (m != null) {
          m.params.assoc(key, decode(path, i, max, hasPercent, hasPlus));
        }
        return m;
      }
      return null;
    }

    @Override
    public int depth() {
      return child.depth() + 1;
    }

    @Override
    public String toString() {
      return "[" + key + " " + child + "]";
    }
  }

  public static CatchAllMatcher catchAllMatcher(Keyword parameter, Object data) {
    return new CatchAllMatcher(parameter, data);
  }

  static final class CatchAllMatcher implements Matcher {
    private final Keyword parameter;
    private final Object data;

    CatchAllMatcher(Keyword parameter, Object data) {
      this.parameter = parameter;
      this.data = data;
    }

    @Override
    public Match match(int i, int max, char[] path, Match match) {
      if (i < max) {
        match.params.assoc(parameter, decode(path, i, max));
        match.data = data;
        return match;
      }
      return null;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public String toString() {
      return "[" + parameter + " " + new DataMatcher(data) + "]";
    }
  }

  public static LinearMatcher linearMatcher(List<Matcher> childs) {
    return new LinearMatcher(childs);
  }

  static final class LinearMatcher implements Matcher {

    private final Matcher[] childs;
    private final int size;

    LinearMatcher(List<Matcher> childs) {
      this.childs = childs.toArray(new Matcher[0]);
      Arrays.sort(this.childs, Comparator.comparing(Matcher::depth).reversed());
      this.size = childs.size();
    }

    @Override
    public Match match(int i, int max, char[] path, Match match) {
      for (int j = 0; j < size; j++) {
        final Match m = childs[j].match(i, max, path, match);
        if (m != null) {
          return m;
        }
      }
      return null;
    }

    @Override
    public int depth() {
      return Arrays.stream(childs).mapToInt(Matcher::depth).max().orElseThrow(NoSuchElementException::new);
    }

    @Override
    public String toString() {
      return Arrays.toString(childs);
    }
  }

  public static Object lookup(Matcher matcher, String path) {
    return matcher.match(0, path.length(), path.toCharArray(), new Match());
  }

  public static Matcher scanner(List<Matcher> matchers) {
    return new LinearMatcher(matchers);
  }

  public static void main(String[] args) {
    Matcher matcher =
            linearMatcher(
                    Arrays.asList(
                            staticMatcher("/auth/",
                                    linearMatcher(
                                            Arrays.asList(
                                                    staticMatcher("login", dataMatcher(1)),
                                                    staticMatcher("recovery", dataMatcher(2)))))));
    System.err.println(matcher);
    System.out.println(lookup(matcher, "/auth/login"));
    System.out.println(lookup(matcher, "/auth/recovery"));
  }
}
