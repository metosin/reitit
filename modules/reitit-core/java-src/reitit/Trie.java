package reitit;

// https://www.codeproject.com/Tips/1190293/Iteration-Over-Java-Collections-with-High-Performa

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

public class Trie {

  private static String decode(String s, boolean hasPercent, boolean hasPlus) {
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
      switch (chars[j]) {
        case '%':
          hasPercent = true;
          break;
        case '+':
          hasPlus = true;
          break;
      }
    }
    return decode(new String(chars, begin, end - begin), hasPercent, hasPlus);
  }

  public final static class Match {
    public final IPersistentMap params;
    public final Object data;

    public Match(IPersistentMap params, Object data) {
      this.params = params;
      this.data = data;
    }

    Match assoc(Object key, Object value) {
      return new Match(params.assoc(key, value), data);
    }

    @Override
    public String toString() {
      Map<Object, Object> m = new HashMap<>();
      m.put(Keyword.intern("data"), data);
      m.put(Keyword.intern("params"), params);
      return m.toString();
    }
  }

  public interface Matcher {
    Match match(int i, int max, char[] path);

    int depth();

    int length();
  }

  public static StaticMatcher staticMatcher(String path, Matcher child) {
    return new StaticMatcher(path, child);
  }

  static final class StaticMatcher implements Matcher {
    private final Matcher child;
    private final char[] path;
    private final int size;

    StaticMatcher(String path, Matcher child) {
      this.path = path.toCharArray();
      this.size = path.length();
      this.child = child;
    }

    @Override
    public Match match(int i, int max, char[] path) {
      if (max < i + size) {
        return null;
      }
      for (int j = 0; j < size; j++) {
        if (path[j + i] != this.path[j]) {
          return null;
        }
      }
      return child.match(i + size, max, path);
    }

    @Override
    public int depth() {
      return child.depth() + 1;
    }

    @Override
    public int length() {
      return path.length;
    }

    @Override
    public String toString() {
      return "[\"" + new String(path) + "\" " + child + "]";
    }
  }

  public static DataMatcher dataMatcher(IPersistentMap params, Object data) {
    return new DataMatcher(params, data);
  }

  static final class DataMatcher implements Matcher {
    private final Match match;

    DataMatcher(IPersistentMap params, Object data) {
      this.match = new Match(params, data);
    }

    @Override
    public Match match(int i, int max, char[] path) {
      if (i == max) {
        return match;
      }
      return null;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public String toString() {
      return (match.data != null ? match.data.toString() : "nil");
    }
  }

  public static WildMatcher wildMatcher(Keyword parameter, char end, Matcher child) {
    return new WildMatcher(parameter, end, child);
  }

  static final class WildMatcher implements Matcher {
    private final Keyword key;
    private final char end;
    private final Matcher child;

    WildMatcher(Keyword key, char end, Matcher child) {
      this.key = key;
      this.end = end;
      this.child = child;
    }

    @Override
    public Match match(int i, int max, char[] path) {
      boolean hasPercent = false;
      boolean hasPlus = false;
      if (i < max && path[i] != end) {
        int stop = max;
        for (int j = i; j < max; j++) {
          final char c = path[j];
          hasPercent = hasPercent || c == '%';
          hasPlus = hasPlus || c == '+';
          if (c == end) {
            stop = j;
            break;
          }
        }
        final Match m = child.match(stop, max, path);
        return m != null ? m.assoc(key, decode(new String(path, i, stop - i), hasPercent, hasPlus)) : null;
      }
      return null;
    }

    @Override
    public int depth() {
      return child.depth() + 1;
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public String toString() {
      return "[" + key + " " + child + "]";
    }
  }

  public static CatchAllMatcher catchAllMatcher(Keyword parameter, IPersistentMap params, Object data) {
    return new CatchAllMatcher(parameter, params, data);
  }

  static final class CatchAllMatcher implements Matcher {
    private final Keyword parameter;
    private final IPersistentMap params;
    private final Object data;

    CatchAllMatcher(Keyword parameter, IPersistentMap params, Object data) {
      this.parameter = parameter;
      this.params = params;
      this.data = data;
    }

    @Override
    public Match match(int i, int max, char[] path) {
      if (i <= max) {
        return new Match(params, data).assoc(parameter, decode(path, i, max));
      }
      return null;
    }

    @Override
    public int depth() {
      return 1;
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public String toString() {
      return "[" + parameter + " " + new DataMatcher(null, data) + "]";
    }
  }

  public static LinearMatcher linearMatcher(List<Matcher> childs, boolean inOrder) {
    return new LinearMatcher(childs, inOrder);
  }

  static final class LinearMatcher implements Matcher {
    private final Matcher[] childs;
    private final int size;

    LinearMatcher(List<Matcher> childs, boolean inOrder) {
      this.childs = childs.toArray(new Matcher[0]);
      if (!inOrder) {
        Arrays.sort(this.childs, Comparator.comparing(Matcher::depth).thenComparing(Matcher::length).reversed());
      }
      this.size = childs.size();
    }

    @Override
    public Match match(int i, int max, char[] path) {
      for (int j = 0; j < size; j++) {
        final Match m = childs[j].match(i, max, path);
        if (m != null) {
          return m;
        }
      }
      return null;
    }

    @Override
    public int depth() {
      return Arrays.stream(childs).mapToInt(Matcher::depth).max().orElseThrow(NoSuchElementException::new) + 1;
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public String toString() {
      return Arrays.toString(childs);
    }
  }

  public static Object lookup(Matcher matcher, String path) {
    return matcher.match(0, path.length(), path.toCharArray());
  }

  public static void main(String[] args) {
    Matcher matcher =
            linearMatcher(
                    Arrays.asList(
                            staticMatcher("/auth/",
                                    linearMatcher(
                                            Arrays.asList(
                                                    staticMatcher("login", dataMatcher(PersistentArrayMap.EMPTY, 1)),
                                                    staticMatcher("recovery", dataMatcher(PersistentArrayMap.EMPTY, 2))), true))), true);
    System.err.println(matcher);
    System.out.println(lookup(matcher, "/auth/login"));
    System.out.println(lookup(matcher, "/auth/recovery"));
  }
}
