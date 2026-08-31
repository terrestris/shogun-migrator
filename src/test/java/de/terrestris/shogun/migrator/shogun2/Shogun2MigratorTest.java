package de.terrestris.shogun.migrator.shogun2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class Shogun2MigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void handlesSourceTypeIsCaseInsensitive() {
        Shogun2Migrator migrator = new Shogun2Migrator();
        Assertions.assertTrue(migrator.handlesSourceType("shogun2"));
        Assertions.assertTrue(migrator.handlesSourceType("SHOGUN2"));
        Assertions.assertFalse(migrator.handlesSourceType("boot"));
    }

    @Test
    void migrateLayerMapsTypeAndReplacesSourceUrl() throws IOException {
        ObjectNode layer = mapper.createObjectNode();
        layer.put("name", "demo-layer");

        ObjectNode appearance = mapper.createObjectNode();
        appearance.put("minResolution", "0");
        appearance.put("maxResolution", "1000");
        appearance.put("hoverable", true);
        appearance.put("attribution", "demo attribution");
        layer.set("appearance", appearance);

        ObjectNode source = mapper.createObjectNode();
        source.put("type", "ImageWMS");
        source.put("url", "http://old.example/geoserver/wms");
        source.put("layerNames", "workspace:layer");
        layer.set("source", source);

        byte[] migratedBytes = Shogun2Migrator.migrateLayer(
            layer,
            "http://old.example/geoserver/wms::http://new.example/geoserver/wms"
        );

        JsonNode migrated = mapper.readTree(migratedBytes);
        Assertions.assertEquals("WMS", migrated.get("type").asText());
        Assertions.assertEquals("http://new.example/geoserver/wms", migrated.get("sourceConfig").get("url").asText());
        Assertions.assertEquals("workspace:layer", migrated.get("sourceConfig").get("layerNames").asText());
        Assertions.assertFalse(migrated.get("clientConfig").get("searchable").asBoolean());
    }

    @Test
    void migrateLayerReturnsNullForUnknownType() throws IOException {
        ObjectNode layer = mapper.createObjectNode();
        layer.put("name", "demo-layer");

        ObjectNode appearance = mapper.createObjectNode();
        appearance.put("minResolution", "0");
        appearance.put("maxResolution", "1000");
        appearance.put("hoverable", true);
        appearance.put("attribution", "demo attribution");
        layer.set("appearance", appearance);

        ObjectNode source = mapper.createObjectNode();
        source.put("type", "UnknownType");
        source.put("url", "http://old.example/geoserver/wms");
        layer.set("source", source);

        byte[] migratedBytes = Shogun2Migrator.migrateLayer(layer, null);
        Assertions.assertNull(migratedBytes);
    }

}
