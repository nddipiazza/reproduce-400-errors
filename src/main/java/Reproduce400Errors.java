import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class Reproduce400Errors {

  private CloseableHttpClient httpClient;
  private HttpClientConnectionManager manager;
  private BasicCookieStore basicCookieStore;

  private Sardine sardine;
  private String sharePointUrlString;
  private boolean doNotEncodeUrls;
  private String[] startLinks;
  private String username;
  private String password;
  private String domain;

  public Reproduce400Errors(String[] startLinks, String username, String password, String domain) {
    this.startLinks = startLinks;
    this.username = username;
    this.password = password;
    this.domain = domain;
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder
        .<AuthSchemeProvider>create()
        .register("ntlm", new NTLMSchemeFactory())
        .register(AuthSchemes.BASIC, new BasicSchemeFactory())
        .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
        .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory())
        .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory())
        .build();

    NTCredentials ntCredentials = new NTCredentials(
        username,
        password,
        null,
        domain);
    credentialsProvider.setCredentials(AuthScope.ANY, ntCredentials);

    SSLConnectionSocketFactory sslsf;
    try {
      SSLContextBuilder sslContextBuilder = SSLContexts.custom();
      sslContextBuilder.loadTrustMaterial(null, new TrustStrategy() {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          return true;
        }
      });
      SSLContext sslContext = sslContextBuilder.build();
      sslsf = new SSLConnectionSocketFactory(
          sslContext, new X509HostnameVerifier() {
        @Override
        public void verify(String host, SSLSocket ssl)
            throws IOException {
        }

        @Override
        public void verify(String host, X509Certificate cert)
            throws SSLException {
        }

        @Override
        public void verify(String host, String[] cns,
                           String[] subjectAlts) throws SSLException {
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {
          return true;
        }
      });
    } catch (Exception e) {
      throw new RuntimeException("Could not ignore SSL verification", e);
    }

    PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager(
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http",
                PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslsf).build());
    manager.setDefaultMaxPerRoute(1000);
    manager.setMaxTotal(5 * 1000);

    this.manager = manager;

    basicCookieStore = new BasicCookieStore();
    HttpClientBuilder builder = HttpClients.custom()
        .setConnectionManager(this.manager)
        .setDefaultAuthSchemeRegistry(authSchemeRegistry)
        .setRedirectStrategy(new CustomRedirectStrategy(startLinks))
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(60000)
            .setConnectTimeout(60000)
            .setSocketTimeout(25000)
            .build()
        )
        .setDefaultCredentialsProvider(credentialsProvider);
    this.httpClient = builder.build();

    sardine = SardineFactory.begin(username, password);
    sardine.setCredentials(username, password, domain, "");
  }

  public DocCallable getDocument(String url) throws Exception {
    try {
      return new DocCallable(() -> {
        HttpGet getRequest = new HttpGet(url);
        HttpResponse getResponse;

        getResponse = httpClient.execute(getRequest);
        // Important: Do *NOT* release this connection. Unless you throw an exception. Needs to stay open for parsers.
        int status = getResponse.getStatusLine().getStatusCode();
        if (status == HttpStatus.SC_OK) {
          return new InputStreamWrapper(getRequest, getResponse.getEntity().getContent());
        } else if (status == HttpStatus.SC_NOT_FOUND) {
          HttpClientUtils.closeQuietly(getResponse);
          throw new RuntimeException("Url " + url + " not found");
        } else {
          HttpClientUtils.closeQuietly(getResponse);
          throw new RuntimeException("Got unexpected status " + status);
        }
      });
    } catch (Throwable e) {
      String message = "Error when retrieving document for URL: " + url;
      throw new RuntimeException(message, e);
    }
  }

  public DocCallable getWebdavDocument(String url) throws Exception {
    return new DocCallable(() -> sardine.get(url));
  }

  public void close() {
    try {
      httpClient.close();
    } catch (IOException e) {

    }
    if (sardine != null) {
      try {
        sardine.shutdown();
      } catch (IOException e) {

      }
    }
  }

}
