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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.logging.log4j.core.util.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

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

    private static JsonNode migrateLayerTree(JsonNode node, ObjectMapper mapper, Map<Integer, Integer> idMap) {
        ObjectNode folder = mapper.createObjectNode();
        folder.set("checked", node.get("checked"));
        folder.set("title", node.get("text"));
        if (node.has("layer")) {
            folder.put("layerId", idMap.get(node.get("layer").get("id").intValue()));
        }
        if (node.has("children")) {
            ArrayNode children = mapper.createArrayNode();
            folder.set("children", children);
            for (JsonNode child : node.get("children")) {
                children.add(migrateLayerTree(child, mapper, idMap));
            }
        }
        return folder;
    }

    public static byte[] migrateApplication(JsonNode node, Map<Integer, Integer> idMap) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode clientConfig = mapper.createObjectNode();
        root.put("name", node.get("name").asText());
        log.info("Migrating application {}", node.get("name").asText());
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
        JsonNode layerTree = migrateLayerTree(node.get("layerTree"), mapper, idMap);
        root.set("layerTree", layerTree);
        root.set("clientConfig", clientConfig);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        mapper.writeValue(bout, root);
        return bout.toByteArray();
    }

    private static int saveLayer(byte[] bs, String url, String user, String password)
        throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        HttpPost post = new HttpPost(url + "layers");
        log.info("Saving layer...");
        try (CloseableHttpClient client = HttpClients.custom()
            .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            Header header = null;
            try {
                header = new BasicScheme(UTF_8).authenticate(credentials, post, null);
            } catch (AuthenticationException e) {
                log.error("Error creating authentication: {}", e.getMessage());
                log.trace("Stack trace:", e);
            }
            post.addHeader(header);
            post.setEntity(new ByteArrayEntity(bs, APPLICATION_JSON));
            CloseableHttpResponse response = client.execute(post);
            JsonNode result = mapper.readTree(response.getEntity().getContent());
            response.close();
            return result.get("id").intValue();
        }
    }

    private static void saveApplication(byte[] bs, String url, String user, String password)
        throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
        HttpPost post = new HttpPost(url + "applications");
        log.info("Saving application...");
        try (CloseableHttpClient client = HttpClients.custom()
            .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()) {
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
            Header header = null;
            try {
                header = new BasicScheme(UTF_8).authenticate(credentials, post, null);
            } catch (AuthenticationException e) {
                log.error("Error creating authentication: {}", e.getMessage());
                log.trace("Stack trace:", e);
            }
            post.addHeader(header);
            post.setEntity(new ByteArrayEntity(bs, APPLICATION_JSON));
            CloseableHttpResponse response = client.execute(post);
            log.info("Response was {}", IOUtils.toString(new InputStreamReader(response.getEntity().getContent(), UTF_8)));
            response.close();
        }
    }

    private static void migrateApplications(String source, String sourceUser, String sourcePassword,
                                            String target, String targetUser, String targetPassword, Map<Integer, Integer> idMap) throws IOException, KeyStoreException,
        NoSuchAlgorithmException, KeyManagementException {
        JsonNode node = fetch(source, sourceUser, sourcePassword, "rest/projectapps");
        int i = 0;
        for (JsonNode app : node) {
            log.info("Migrating application...");
            byte[] bs = migrateApplication(app, idMap);
            saveApplication(bs, target, targetUser, targetPassword);
//            copyInputStreamToFile(new ByteArrayInputStream(bs), new File("/tmp/" + ++i + ".json"));
        }
    }

    private static Map<Integer, Integer> migrateLayers(String source, String sourceUser, String sourcePassword,
                                      String target, String targetUser, String targetPassword) throws IOException, KeyStoreException,
        NoSuchAlgorithmException, KeyManagementException {
        JsonNode node = fetch(source, sourceUser, sourcePassword, "rest/projectlayers");
        Map<Integer, Integer> layerIdMap = new HashMap<>();
        int i = 0;
        for (JsonNode layer : node) {
            log.info("Migrating layer...");
            byte[] bs = migrateLayer(layer);
            if (bs == null) {
                continue;
            }
            int newId = saveLayer(bs, target, targetUser, targetPassword);
            layerIdMap.put(layer.get("id").intValue(), newId);
            // use these to create new test files
//            new ObjectMapper().writeValue(new File("/tmp/layer" + ++i + ".json"), layer);
//            copyInputStreamToFile(new ByteArrayInputStream(bs), new File("/tmp/layer" + ++i + ".json"));
        }
        return layerIdMap;
    }

    private static String mapType(String type) {
        switch (type) {
            case "TileWMS":
                return "TILEWMS";
            case "OSMVectortile":
                return "VECTORTILE";
            case "ImageWMS":
            case "WMSTime":
                return "WMS";
            case "WMTS":
                return "WMTS";
            // AFAICT these two don't exist yet and might be replaced with the proper value once implemented
            case "WFS":
                return "WFS";
            case "XYZ":
                return "XYZ";
            default:
                log.warn("Unable to migrate layer of type {}.", type);
                return null;
        }
    }

    private static void migrateClientConfig(JsonNode node, ObjectNode config) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode appearance = node.get("appearance");
        config.put("minResolution", appearance.get("minResolution").textValue());
        config.put("maxResolution", appearance.get("maxResolution").textValue());
        JsonNode searchable = node.get("searchable");
        if (searchable != null && searchable.booleanValue()) {
            config.put("searchable", searchable.booleanValue());
            JsonNode oldConfig = node.get("searchConfig");
            ObjectNode searchConfig = mapper.createObjectNode();
            config.set("searchConfig", searchConfig);
            searchConfig.put("displayTemplate", oldConfig.get("displayTemplate").textValue());
            JsonNode icon = oldConfig.get("icon");
            if (icon != null) {
                searchConfig.put("icon", icon.textValue());
            }
            ArrayNode attributes = mapper.createArrayNode();
            oldConfig.get("attributes").forEach(attribute -> attributes.add(attribute.textValue()));
            searchConfig.set("attributes", attributes);
        } else {
            config.put("searchable", false);
        }
    }

    private static void migrateSourceConfig(JsonNode node, ObjectNode config) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode oldSource = node.get("source");
        config.put("url", oldSource.get("url").textValue());
        config.put("layerNames", oldSource.get("layerNames").textValue());
        JsonNode tileGrid = oldSource.get("tileGrid");
        if (tileGrid != null) {
            config.put("tileSize", tileGrid.get("tileSize").intValue());
            JsonNode oldOrigin = tileGrid.get("tileGridOrigin");
            ArrayNode tileOrigin = mapper.createArrayNode();
            tileOrigin.add(oldOrigin.get("x").doubleValue());
            tileOrigin.add(oldOrigin.get("y").doubleValue());
            config.set("tileOrigin", tileOrigin);
            JsonNode oldResolutions = tileGrid.get("tileGridResolutions");
            ArrayNode resolutions = mapper.createArrayNode();
            oldResolutions.forEach(resolution -> resolutions.add(resolution.doubleValue()));
            config.set("resolutions", resolutions);
        }
    }

    static byte[] migrateLayer(JsonNode node) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        JsonNode name = node.get("name");
        root.put("name", name.textValue());
        JsonNode type = node.get("source").get("type");
        if (type == null) {
            log.warn("Layer {} doesn't have a type set.", name);
            return null;
        }
        String mappedType = mapType(type.textValue());
        if (mappedType == null) {
            return null;
        }
        root.put("type", mappedType);

        ObjectNode clientConfig = mapper.createObjectNode();
        ObjectNode sourceConfig = mapper.createObjectNode();

        migrateClientConfig(node, clientConfig);
        migrateSourceConfig(node, sourceConfig);

        root.set("clientConfig", clientConfig);
        root.set("sourceConfig", sourceConfig);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        mapper.writeValue(bout, root);
        return bout.toByteArray();
    }

    private static JsonNode fetch(String source, String sourceUser, String sourcePassword, String resource) throws IOException, KeyStoreException,
        NoSuchAlgorithmException, KeyManagementException {
        ObjectMapper mapper = new ObjectMapper();
        try (CloseableHttpClient client = HttpClients.custom()
            .setSSLContext(new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()) {
            if (!source.endsWith("/")) {
                source += "/";
            }
            source = source + resource;
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

            return mapper.readTree(response.getEntity().getContent());
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
            Map<Integer, Integer> idMap = migrateLayers(source, sourceUser, sourcePassword, target, targetUser, targetPassword);
            migrateApplications(source, sourceUser, sourcePassword, target, targetUser, targetPassword, idMap);
        } catch (Exception e) {
            log.error("Error when migrating applications: {}", e.getMessage());
            log.trace("Stack trace:", e);
        }
    }

}
