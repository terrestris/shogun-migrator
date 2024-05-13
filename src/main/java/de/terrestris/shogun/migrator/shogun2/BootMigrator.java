package de.terrestris.shogun.migrator.shogun2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.spi.ShogunMigrator;
import de.terrestris.shogun.migrator.util.MigrationException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.MetaInfServices;

import java.io.IOException;
import java.util.*;

import static de.terrestris.shogun.migrator.util.ApiUtil.*;

@Log4j2
@MetaInfServices
public class BootMigrator implements ShogunMigrator {

  public static final String LAYER_ID = "layerId";

  public static final String BACKGROUND_LAYERS = "backgroundLayers";

  private HostDto source;

  private HostDto target;

  private static void migrateLayerTree(ObjectNode node, Map<Integer, Integer> idMap) {
    if (node.has(LAYER_ID)) {
      node.put(LAYER_ID, idMap.get(node.get(LAYER_ID).intValue()));
    }
    if (node.has("children")) {
      for (JsonNode jsonNode : node.get("children")) {
        migrateLayerTree((ObjectNode) jsonNode, idMap);
      }
    }
  }

  public static byte[] migrateApplication(ObjectNode node, Map<Integer, Integer> idMap) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    node.remove("id");
    log.info("Migrating application {}", node.get("name").asText());

    JsonNode clientConfig = node.get("clientConfig");
    if (clientConfig.has(BACKGROUND_LAYERS)) {
      ArrayNode backgroundLayers = mapper.createArrayNode();
      for (JsonNode jsonNode : clientConfig.get(BACKGROUND_LAYERS)) {
        backgroundLayers.add(idMap.get(jsonNode.asInt()));
      }
      ((ObjectNode) clientConfig).set(BACKGROUND_LAYERS, backgroundLayers);
    }
    JsonNode layerTree = node.get("layerTree");
    migrateLayerTree((ObjectNode) layerTree, idMap);

    return mapper.writeValueAsBytes(node);
  }

  @Override
  public void initialize(HostDto source, HostDto target) {
    this.source = source;
    this.target = target;
  }

  @Override
  public Map<Integer, Integer> migrateLayers() {
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode node = fetch(source, "layers", true);
      Map<Integer, Integer> layerIdMap = new HashMap<>();
      for (JsonNode layer : node) {
        int id = layer.get("id").intValue();
        log.info("Migrating layer {}...", layer.get("name"));
        ObjectNode on = (ObjectNode) layer;
        on.remove("id");
        byte[] bs = mapper.writeValueAsBytes(on);
        if (bs == null) {
          log.error("Unable to serialize layer {}!", layer.get("name"));
          continue;
        }
        int newId = saveLayer(bs, target);
        layerIdMap.put(id, newId);
      }
      return layerIdMap;
    } catch (Exception e) {
      log.warn("Unable to migrate layers: {}", e.getMessage());
      log.trace("Stack trace:", e);
      throw new MigrationException(e);
    }
  }

  @Override
  public void migrateApplications(Map<Integer, Integer> idMap) {
    try {
      JsonNode node = fetch(source, "applications", true);
      for (JsonNode app : node) {
        log.info("Migrating application...");
        byte[] bs = migrateApplication((ObjectNode) app, idMap);
        saveApplication(bs, target);
      }
    } catch (Exception e) {
      log.warn("Unable to migrate applications: {}", e.getMessage());
      log.trace("Stack trace:", e);
      throw new MigrationException(e);
    }
  }

  @Override
  public boolean handlesSourceType(String sourceType) {
    return sourceType.equalsIgnoreCase("boot");
  }

}
