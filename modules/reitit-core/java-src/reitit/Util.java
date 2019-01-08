package reitit;

import clojure.lang.Keyword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {

  public static Map<?, String> matchURI(String uri, List<?> patternTokens) {
    final MatchResult result = matchURI(uri, 0, patternTokens, false);
    return result == null ? null : result.getParams();
  }

  public static MatchResult matchURI(String uri, int beginIndex, List<?> patternTokens) {
    return matchURI(uri, beginIndex, patternTokens, false);
  }

  /**
   * Match a URI against URI-pattern tokens and return match result on successful match, {@code null} otherwise.
   * When argument {@code attemptPartialMatch} is {@code true}, both full and partial match are attempted without any
   * performance penalty. When argument {@code attemptPartialMatch} is {@code false}, only a full match is attempted.
   *
   * @param uri                 the URI string to match
   * @param beginIndex          beginning index in the URI string to match
   * @param patternTokens       URI pattern tokens to match the URI against
   * @param attemptPartialMatch whether attempt partial match when full match is not possible
   * @return a match result on successful match, {@literal null} otherwise
   */
  public static MatchResult matchURI(String uri, int beginIndex, List<?> patternTokens, boolean attemptPartialMatch) {
    if (beginIndex == MatchResult.FULL_MATCH_INDEX) { // if already a full-match then no need to match further
      return MatchResult.NO_MATCH;
    }
    final int tokenCount = patternTokens.size();
    // if length==1, then token must be string (static URI path)
    if (tokenCount == 1) {
      final String staticPath = (String) patternTokens.get(0);
      if (uri.startsWith(staticPath, beginIndex)) {  // URI begins with the path, so at least partial match exists
        if ((uri.length() - beginIndex) == staticPath.length()) {  // if full match exists, then return as such
          return MatchResult.FULL_MATCH_NO_PARAMS;
        }
        return attemptPartialMatch ? MatchResult.partialMatch(staticPath.length()) : MatchResult.NO_MATCH;
      } else {
        return MatchResult.NO_MATCH;
      }
    }
    final int uriLength = uri.length();
    final Map<Object, String> pathParams = new HashMap<Object, String>(tokenCount);
    int uriIndex = beginIndex;
    OUTER:
    for (final Object token : patternTokens) {
      if (uriIndex >= uriLength) {
        return attemptPartialMatch ? MatchResult.partialMatch(pathParams, uriIndex) : MatchResult.NO_MATCH;
      }
      if (token instanceof String) {
        final String tokenStr = (String) token;
        if (uri.startsWith(tokenStr, uriIndex)) {
          uriIndex += tokenStr.length();  // now i==n if last string token
        } else {  // 'string token mismatch' implies no match
          return MatchResult.NO_MATCH;
        }
      } else {
        final StringBuilder sb = new StringBuilder();
        for (int j = uriIndex; j < uriLength; j++) {  // capture param chars in one pass
          final char ch = uri.charAt(j);
          if (ch == '/') {  // separator implies we got param value, now continue
            pathParams.put(token, sb.toString());
            uriIndex = j;
            continue OUTER;
          } else {
            sb.append(ch);
          }
        }
        // 'separator not found' implies URI has ended
        pathParams.put(token, sb.toString());
        uriIndex = uriLength;
      }
    }
    if (uriIndex < uriLength) {  // 'tokens finished but URI still in progress' implies partial or no match
      return attemptPartialMatch ? MatchResult.partialMatch(pathParams, uriIndex) : MatchResult.NO_MATCH;
    }
    return MatchResult.fullMatch(pathParams);
  }

  public static void main(String[] args) {
    List<Object> list = new ArrayList<>();
    list.add("/user/");
    list.add(Keyword.intern("userId"));
    list.add("/profile/");
    list.add(Keyword.intern("type"));
    list.add("/");
    System.out.println(Util.matchURI("/user/1234/profile/compact/", list));
  }
}
