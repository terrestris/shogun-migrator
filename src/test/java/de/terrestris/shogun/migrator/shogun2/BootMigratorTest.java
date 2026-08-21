package de.terrestris.shogun.migrator.shogun2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

class BootMigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void migrateApplicationRemapsLayerIdsAndRemovesId() throws IOException {
        ObjectNode app = mapper.createObjectNode();
        app.put("id", 42);
        app.put("name", "demo-app");

        ObjectNode clientConfig = mapper.createObjectNode();
        ArrayNode backgroundLayers = mapper.createArrayNode();
        backgroundLayers.add(1);
        backgroundLayers.add(2);
        clientConfig.set("backgroundLayers", backgroundLayers);
        app.set("clientConfig", clientConfig);

        ObjectNode layerTree = mapper.createObjectNode();
        layerTree.put("layerId", 2);
        ArrayNode children = mapper.createArrayNode();
        ObjectNode child = mapper.createObjectNode();
        child.put("layerId", 1);
        children.add(child);
        layerTree.set("children", children);
        app.set("layerTree", layerTree);

        byte[] migratedBytes = BootMigrator.migrateApplication(app, Map.of(1, 101, 2, 202));
        JsonNode migrated = mapper.readTree(migratedBytes);

        Assertions.assertFalse(migrated.has("id"));
        Assertions.assertEquals("demo-app", migrated.get("name").asText());
        Assertions.assertEquals(101, migrated.get("clientConfig").get("backgroundLayers").get(0).asInt());
        Assertions.assertEquals(202, migrated.get("clientConfig").get("backgroundLayers").get(1).asInt());
        Assertions.assertEquals(202, migrated.get("layerTree").get("layerId").asInt());
        Assertions.assertEquals(101, migrated.get("layerTree").get("children").get(0).get("layerId").asInt());
    }

}