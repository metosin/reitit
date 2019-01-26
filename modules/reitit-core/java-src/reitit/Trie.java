package reitit;

// https://www.codeproject.com/Tips/1190293/Iteration-Over-Java-Collections-with-High-Performa

import clojure.lang.Keyword;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

public class Trie {

  private static String decode(char[] chars, int i, int j, boolean hasPercent, boolean hasPlus) {
    final String s = new String(chars, i, j);
    try {
      if (hasPercent) {
        return URLDecoder.decode(hasPlus ? s.replace("+", "%2B") : s, "UTF-8");
      }
    } catch (UnsupportedEncodingException ignored) {
    }
    return s;
  }

  public static class Match {
    public final List<Object> params = new ArrayList<>();
    public Object data;

    @Override
    public String toString() {
      Map<Object, Object> m = new HashMap<>();
      m.put(Keyword.intern("data"), data);
      m.put(Keyword.intern("params"), params);
      return m.toString();
    }
  }

  public static class Path {
    final char[] value;
    final int size;

    Path(String value) {
      this.value = value.toCharArray();
      this.size = value.length();
    }
  }

  public interface Matcher {
    Match match(int i, Path path, Match match);
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
    public Match match(int i, Path path, Match match) {
      final char[] value = path.value;
      if (path.size < i + size) {
        return null;
      }
      for (int j = 0; j < size; j++) {
        if (value[j + i] != this.path[j]) {
          return null;
        }
      }
      return child.match(i + size, path, match);
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
    public Match match(int i, Path path, Match match) {
      if (i == path.size) {
        match.data = data;
        return match;
      }
      return null;
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
    public Match match(int i, Path path, Match match) {
      final char[] value = path.value;
      if (i < path.size && value[i] != '/') {
        boolean hasPercent = false;
        boolean hasPlus = false;
        for (int j = i; j < path.size; j++) {
          if (value[j] == '/') {
            final Match m = child.match(j, path, match);
            if (m != null) {
              m.params.add(key);
              m.params.add(decode(value, i, j - i, hasPercent, hasPlus));
            }
            return m;
          } else if (value[j] == '%') {
            hasPercent = true;
          } else if (value[j] == '+') {
            hasPlus = true;
          }
        }
        if (child instanceof DataMatcher) {
          final Match m = child.match(path.size, path, match);
          m.params.add(key);
          m.params.add(decode(value, i, path.size - i, hasPercent, hasPlus));
          return m;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return "[" + key + " " + child + "]";
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
      this.size = childs.size();
    }

    @Override
    public Match match(int i, Path path, Match match) {
      for (int j = 0; j < size; j++) {
        final Match m = childs[j].match(i, path, match);
        if (m != null) {
          return m;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return Arrays.toString(childs);
    }
  }

  public static Object lookup(Matcher matcher, String path) {
    return matcher.match(0, new Path(path), new Match());
  }

  public static void main(String[] args) {

    //Matcher matcher = new StaticMatcher("/kikka", new StaticMatcher("/kukka", new DataMatcher(1)));
//    Matcher matcher =
//            staticMatcher("/kikka/",
//                    wildMatcher(Keyword.intern("kukka"),
//                            staticMatcher("/kikka",
//                                    dataMatcher(1))));
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
