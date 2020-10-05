package de.terrestris.shogun.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.apache.http.Header;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Log4j2
public class Migrator {

    private static JsonNode findMapModule(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.has("xtype") && node.get("xtype").asText().equals("shogun-component-map")) {
            return node;
        }
        if (node.has("subModules")) {
            for (JsonNode sub : node.get("subModules")) {
                JsonNode possibleNode = findMapModule(sub);
                if (possibleNode != null) {
                    return possibleNode;
                }
            }
        }
        return null;
    }

    private static void migrateApplication(JsonNode node, ObjectMapper mapper) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode clientConfig = mapper.createObjectNode();
        root.put("name", node.get("name").asText());
        JsonNode mapNode = findMapModule(node.get("viewport"));
        if (mapNode != null) {
            JsonNode mapConfig = mapNode.get("mapConfig");
            ObjectNode mapView = mapper.createObjectNode();
            ArrayNode center = mapper.createArrayNode();
            JsonNode oldCenter = mapConfig.get("center");
            if (oldCenter.isArray()) {
                center.add(oldCenter.get(0).asDouble());
                center.add(oldCenter.get(1).asDouble());
            } else {
                center.add(oldCenter.get("x"));
                center.add(oldCenter.get("y"));
            }
            mapView.set("center", center);
            ArrayNode extent = mapper.createArrayNode();
            JsonNode oldExtent = mapConfig.get("extent");
            extent.add(oldExtent.get("lowerLeft").get("x").asDouble());
            extent.add(oldExtent.get("lowerLeft").get("y").asDouble());
            extent.add(oldExtent.get("upperRight").get("x").asDouble());
            extent.add(oldExtent.get("upperRight").get("y").asDouble());
            mapView.set("mapExtent", extent);
            String oldProjection = mapConfig.get("projection").asText();
            mapView.put("projection", oldProjection.startsWith("EPSG:") ? oldProjection : ("EPSG:" + oldProjection));
            JsonNode resolutions = mapConfig.get("resolutions");
            ArrayNode newResolutions = mapper.createArrayNode();
            for (JsonNode res : resolutions) {
                newResolutions.add(res.asDouble());
            }
            mapView.set("resolutions", newResolutions);
            clientConfig.set("mapView", mapView);
        }
        root.set("clientConfig", clientConfig);
        mapper.writeValue(System.out, root);
    }

    private static void migrateApplications(String source, String sourceUser, String sourcePassword,
        String target, String targetUser, String targetPassword) throws IOException, KeyStoreException,
        NoSuchAlgorithmException, KeyManagementException {
        ObjectMapper mapper = new ObjectMapper();
        SSLContextBuilder builder = new SSLContextBuilder();
        builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
        try (CloseableHttpClient client = HttpClients.custom().setSSLSocketFactory(sslsf).build()) {
            if (!source.endsWith("/")) {
                source = source + "/";
            }
            source = source + "rest/projectapps";
            HttpGet get = new HttpGet(source);

            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(sourceUser, sourcePassword);
            Header header = null;
            try {
                header = new BasicScheme(UTF_8).authenticate(credentials, get, null);
            } catch (AuthenticationException e) {
                log.error("Error creating authentication: {}", e.getMessage());
                log.trace("Stack trace:", e);
            }
            get.addHeader(header);
            CloseableHttpResponse response = client.execute(get);

            JsonNode node = mapper.readTree(response.getEntity().getContent());
            for (JsonNode app : node) {
                migrateApplication(app, mapper);
            }
        }
    }

    public static void main(String[] args) {
        try {
            String source = args[0];
            String sourceUser = args[1];
            String sourcePassword = args[2];
            String target = args[3];
            String targetUser = args[4];
            String targetPassword = args[5];
            migrateApplications(source, sourceUser, sourcePassword, target, targetUser, targetPassword);
        } catch (Exception e) {
            log.error("Error when migrating applications: {}", e.getMessage());
            log.trace("Stack trace:", e);
        }
    }

}
