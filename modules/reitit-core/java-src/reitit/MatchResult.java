package reitit;

import java.util.Collections;
import java.util.Map;

public class MatchResult {

  public static final MatchResult NO_MATCH = null;

  @SuppressWarnings("unchecked")
  public static final Map<?, String> NO_PARAMS = Collections.EMPTY_MAP;

  public static final int FULL_MATCH_INDEX = -1;

  public static final MatchResult FULL_MATCH_NO_PARAMS = new MatchResult(NO_PARAMS, FULL_MATCH_INDEX);

  private final Map<?, String> params;

  /**
   * End index in the URI when match stopped. -1 implies match fully ended.
   */
  private final int endIndex; // excluding

  protected MatchResult(Map<?, String> params, int endIndex) {
    this.params = params;
    this.endIndex = endIndex;
  }

  // ----- factory methods -----

  public static MatchResult partialMatch(Map<?, String> params, int endIndex) {
    return new MatchResult(params, endIndex);
  }

  public static MatchResult partialMatch(int endIndex) {
    return new MatchResult(NO_PARAMS, endIndex);
  }

  public static MatchResult fullMatch(Map<?, String> params) {
    return new MatchResult(params, FULL_MATCH_INDEX);
  }

  // ----- utility methods -----

  public Map<?, String> getParams() {
    return params;
  }

  public int getEndIndex() {
    return endIndex;
  }

  public boolean isFullMatch() {
    return endIndex == FULL_MATCH_INDEX;
  }

  // ----- overridden methods -----

  @Override
  public String toString() {
    return String.format("params: %s, endIndex: %d", params, endIndex);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof MatchResult) {
      MatchResult other = (MatchResult) obj;
      return other.params.equals(params) && other.endIndex == endIndex;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }
}
