package de.terrestris.shogun.migrator.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.spi.ApplicationPostProcessor;
import de.terrestris.shogun.migrator.spi.LayerPostProcessor;
import lombok.extern.log4j.Log4j2;
import org.apache.http.Header;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ServiceLoader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

@Log4j2
public class ApiUtil {

  private ApiUtil() {
    // prevent instantiation
  }

  private static final List<LayerPostProcessor> LAYER_POSTPROCESSORS;

  private static final List<ApplicationPostProcessor> APPLICATION_POSTPROCESSORS;

  static {
    ServiceLoader<LayerPostProcessor> loader = ServiceLoader.load(LayerPostProcessor.class);
    LAYER_POSTPROCESSORS = loader.stream().map(ServiceLoader.Provider::get).toList();
    ServiceLoader<ApplicationPostProcessor> appLoader = ServiceLoader.load(ApplicationPostProcessor.class);
    APPLICATION_POSTPROCESSORS = appLoader.stream().map(ServiceLoader.Provider::get).toList();
  }

  public static void getToken(HostDto host) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
    if (host.getClientId() == null) {
      return;
    }
    ObjectMapper mapper = new ObjectMapper();
    String url = String.format("%sauth/realms/SHOGun/protocol/openid-connect/token", host.getHostname());
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      HttpPost post = new HttpPost(url);
      log.debug("Fetching token.");

      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(List.of(
        new BasicNameValuePair("username", host.getUsername()),
        new BasicNameValuePair("password", host.getPassword()),
        new BasicNameValuePair("grant_type", "password"),
        new BasicNameValuePair("client_id", host.getClientId())
      ));
      post.setEntity(entity);
      post.addHeader("Content-Type", "application/x-www-form-urlencoded");

      CloseableHttpResponse response = client.execute(post);

      host.setToken(mapper.readTree(response.getEntity().getContent()).get("access_token").asText());
    }
  }

  public static JsonNode fetch(HostDto host, String resource, boolean isBoot) throws IOException, KeyStoreException,
    NoSuchAlgorithmException, KeyManagementException {
    ObjectMapper mapper = new ObjectMapper();
    String source = host.getHostname();
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      source = source + resource;
      HttpGet get = new HttpGet(source);
      log.debug("Fetching: {}", get.toString());

      if (isBoot) {
        get.addHeader("Authorization", "Bearer " + host.getToken());
      } else {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(host.getUsername(), host.getPassword());
        Header header = null;
        try {
          header = new BasicScheme(UTF_8).authenticate(credentials, get, null);
        } catch (AuthenticationException e) {
          log.error("Error creating authentication: {}", e.getMessage());
          log.trace("Stack trace:", e);
        }
        get.addHeader(header);
      }
      CloseableHttpResponse response = client.execute(get);
      log.debug("Status code: {}", response.getStatusLine().getStatusCode());

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
      delete.addHeader("Authorization", "Bearer " + host.getToken());
      CloseableHttpResponse response = client.execute(delete);
      log.trace("Status code: {}", response.getStatusLine().getStatusCode());
      response.close();
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
      post.addHeader("Authorization", "Bearer " + host.getToken());
      post.setEntity(new ByteArrayEntity(bs, APPLICATION_JSON));
      try (CloseableHttpResponse response = client.execute(post)) {
        return mapper.readTree(response.getEntity().getContent());
      }
    }
  }

  public static int saveLayer(byte[] bs, HostDto host)
    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(bs);
    LAYER_POSTPROCESSORS.forEach(processor -> {
      processor.postprocess(node);
    });
    bs = mapper.writeValueAsBytes(node);
    JsonNode result = saveEntity(host, bs, "layer");
    return result.get("id").intValue();
  }

  public static void makeLayerPublic(HostDto host, int id) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
    HttpPost post = new HttpPost(String.format("%slayers/%s/permissions/public", host.getHostname(), id));
    try (CloseableHttpClient client = HttpClients.custom()
      .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
      .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
      .build()) {
      post.addHeader("Authorization", "Bearer " + host.getToken());
      try (CloseableHttpResponse response = client.execute(post)) {
        if (response.getStatusLine().getStatusCode() != 200) {
          log.warn("Unable to make layer public.");
        }
      }
    }
  }

  public static void saveApplication(byte[] bs, HostDto host)
    throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(bs);
    APPLICATION_POSTPROCESSORS.forEach(processor -> {
      processor.postprocess(node);
    });
    bs = mapper.writeValueAsBytes(node);
    saveEntity(host, bs, "application");
  }

}
