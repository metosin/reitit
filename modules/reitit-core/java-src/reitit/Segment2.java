package reitit;

import java.util.*;

public class Segment2 {

  private List<Edge> edges = new ArrayList<>();
  private Object data;

  public boolean isLeaf() {
    return edges.isEmpty();
  }

  public static class Edge {
    String path;
    Segment2 segment;
  }

  public static Object lookup(Segment2 root, String path) {
    Segment2 segment = root;
    Integer pathLength = path.length();

    while (segment != null && !segment.isLeaf()) {
      Edge edge = null;
      for (Edge e : segment.edges) {
        System.out.println("EDGE:" + e.path + "/" + e.segment);
        if (path.equals(e.path)) {
          edge = e;
          break;
        }
      }
      if (edge != null) {
        segment = edge.segment;
      } else {
        return null;
      }
    }
    return segment != null ? segment.data : null;
  }

  public static Edge endpoint(String path) {
    Edge edge = new Edge();
    edge.path = path;
    Segment2 s = new Segment2();
    s.data = path;
    edge.segment = s;
    return edge;
  }

  public static Edge context(String path, Edge... edges) {
    Edge edge = new Edge();
    edge.path = path;
    Segment2 s = new Segment2();
    s.edges.addAll(Arrays.asList(edges));
    edge.segment = s;
    return edge;
  }

  public static void main(String[] args) {
    Segment2 root = new Segment2();

    root.edges.add(endpoint("/kikka"));
    root.edges.add(endpoint("/kukka"));
    root.edges.add(
            context("/api",
                    endpoint("/ping"),
                    endpoint("pong")));
    System.out.println(lookup(root, "/api/ping"));
  }

  public static Map<String, String> createHash(List<String> paths) {
    Map<String, String> m = new HashMap<>();
    for (String p : paths) {
      m.put(p, p);
    }
    return m;
  }

  public static List<String> createArray(List<String> paths) {
    return new ArrayList<>(paths);
  }

  public static Object hashLookup(Map m, String path) {
    return m.get(path);
  }

  public static Object arrayLookup(ArrayList<String> paths, String path) {
    Object data = null;
    for (String p : paths) {
      if (p.equals(path)) {
        data = path;
        break;
      }
    }
    return data;
  }
}
