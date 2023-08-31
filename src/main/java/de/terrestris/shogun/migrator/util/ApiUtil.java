package de.terrestris.shogun.migrator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.terrestris.shogun.migrator.model.HostDto;
import lombok.extern.log4j.Log4j2;
import org.apache.http.Header;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

@Log4j2
public class ApiUtil {

  private ApiUtil() {
    // prevent instantiation
  }

  public static JsonNode fetch(HostDto host, String resource) throws IOException, KeyStoreException,
    NoSuchAlgorithmException, KeyManagementException {
    ObjectMapper mapper = new ObjectMapper();
    String source = host.getHostname();
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      source = source + resource;
      HttpGet get = new HttpGet(source);

      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(host.getUsername(), host.getPassword());
      Header header = null;
      try {
        header = new BasicScheme(UTF_8).authenticate(credentials, get, null);
      } catch (AuthenticationException e) {
        log.error("Error creating authentication: {}", e.getMessage());
        log.trace("Stack trace:", e);
      }
      get.addHeader(header);
      CloseableHttpResponse response = client.execute(get);

      return mapper.readTree(response.getEntity().getContent());
    }
  }

  public static void delete(HostDto host, String resource) throws IOException, KeyStoreException,
    NoSuchAlgorithmException, KeyManagementException {
    String source = host.getHostname();
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      source = source + resource;
      HttpDelete delete = new HttpDelete(source);

      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(host.getUsername(), host.getPassword());
      Header header = null;
      try {
        header = new BasicScheme(UTF_8).authenticate(credentials, delete, null);
      } catch (AuthenticationException e) {
        log.error("Error creating authentication: {}", e.getMessage());
        log.trace("Stack trace:", e);
      }
      delete.addHeader(header);
      client.execute(delete).close();
    }
  }

  private static JsonNode saveEntity(HostDto host, byte[] bs, String entity) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    HttpPost post = new HttpPost(host.getHostname() + entity + "s");
    log.info("Saving {}...", entity);
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(host.getUsername(), host.getPassword());
      Header header = null;
      try {
        header = new BasicScheme(UTF_8).authenticate(credentials, post, null);
      } catch (AuthenticationException e) {
        log.error("Error creating authentication: {}", e.getMessage());
        log.trace("Stack trace:", e);
      }
      post.addHeader(header);
      post.setEntity(new ByteArrayEntity(bs, APPLICATION_JSON));
      try (CloseableHttpResponse response = client.execute(post)) {
        return mapper.readTree(response.getEntity().getContent());
      }
    }
  }

  public static int saveLayer(byte[] bs, HostDto host)
    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
    JsonNode result = saveEntity(host, bs, "layer");
    return result.get("id").intValue();
  }

  public static void saveApplication(byte[] bs, HostDto host)
    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
    saveEntity(host, bs, "application");
  }

}
