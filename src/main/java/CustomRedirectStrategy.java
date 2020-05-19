import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.protocol.HttpContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public class CustomRedirectStrategy extends LaxRedirectStrategy {
  private Set<String> allowedHosts = new HashSet<>();


  public static final String NEWLINE = System.getProperty("line.separator");

  private static Map<String,String> ENCODE_URL_REPLACEMENTS = new ConcurrentHashMap<String,String>() {{
    put(" ", "%20");
    put("\"", "%22");
    put("<", "%3C");
    put(">", "%3E");
    put("#", "%23");
    put("{", "%7B");
    put("}", "%7D");
    put("|", "%7C");
    put("\\", "%5C");
    put("^", "%5E");
    put("[", "%5B");
    put("]", "%5D");
    put("`", "%60");
  }};
  private static ThreadLocal<Set<Matcher>> ENCODE_URL_MATCHERS = makeThreadLocalMatchers(
      ENCODE_URL_REPLACEMENTS.keySet(), Pattern.LITERAL);

  @SuppressWarnings({"unchecked", "deprecation"})
  public static File url2File(URL u) {
    if (!"file".equalsIgnoreCase(u.getProtocol())) {
      return null;
    }
    try {
      return new File(URLDecoder.decode(u.getFile(), "UTF-8"));
    } catch (Exception e) {
      return new File(URLDecoder.decode(u.getFile()));
    }
  }

  public static boolean isValidURL(String input, String... validURLProtocols) {
    try {
      URI uri = new URI(input);
      return Stream.of(validURLProtocols).anyMatch(protocol -> protocol.equals(uri.getScheme()));
    } catch (URISyntaxException e) {
      // It is expected to have lots of Syntax exceptions. Do not fill the logs with errors.
      return false;
    }
  }

  public static boolean timeout(long nanoTimeStart, int timeoutMS) {
    return (timeoutMS > 0) && (TimeUnit.NANOSECONDS.convert((long) timeoutMS, TimeUnit.MILLISECONDS)
        <= System.nanoTime() - nanoTimeStart);
  }

  public static void handleInterrupt(InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException(e);
  }

  public static void sleep(int ms) {
    try {
      Thread.sleep(ms);
    }
    catch (InterruptedException e) { handleInterrupt(e); }
  }

  public static byte[] compress(byte[] bytes) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    GZIPOutputStream gzip = new GZIPOutputStream(baos);
    gzip.write(bytes);
    gzip.flush();
    gzip.close();
    return baos.toByteArray();
  }

  public static String encodeURL(String url) {
    Set<Matcher> matchers = ENCODE_URL_MATCHERS.get();
    for (Matcher matcher : matchers) {
      url = matcher.reset(url).replaceAll(Matcher.quoteReplacement(
          ENCODE_URL_REPLACEMENTS.get(matcher.pattern().pattern())));
    }
    return url;
  }

  public static ThreadLocal<Set<Matcher>> makeThreadLocalMatchers(final Set<String> regexes,
                                                                  final int flags) {
    return new ThreadLocal<Set<Matcher>>() {
      @Override
      public Set<Matcher> initialValue() {
        Set<Matcher> matchers = new LinkedHashSet<>(regexes.size());
        for (String regex : regexes) {
          matchers.add(Pattern.compile(regex, flags).matcher(""));
        }
        return matchers;
      }
    };
  }

  public CustomRedirectStrategy(String [] startLinks) {
      for (String startLink : startLinks) {
        URI uri;
        try {
          uri = new URI(startLink);
        } catch (final URISyntaxException ex) {
          try {
            uri = new URI(encodeURL(startLink));
          } catch (URISyntaxException e) {
            throw new RuntimeException("Could not create custom redirect strategy.", e);
          }
        }
        allowedHosts.add(uri.getHost());
      }
  }

  @Override
  protected URI createLocationURI(final String location) throws ProtocolException {
      String newLocation = location;
      try {
          new URI(newLocation);
      } catch (final URISyntaxException ex) {
          newLocation = encodeURL(newLocation);
      }
      return super.createLocationURI(newLocation);
  }

  @Override
  public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
    boolean isRedirectedSuper = super.isRedirected(request, response, context);
    if (isRedirectedSuper) {
      URI uri;
      try {
        uri = new URI(request.getRequestLine().getUri());
      } catch (URISyntaxException e) {
        return true;
      }
      if (!allowedHosts.contains(uri.getHost())) {
        return false;
      }
    }
    return isRedirectedSuper;
  }
}