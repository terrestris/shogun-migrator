package de.terrestris.shogun.migrator.shogun2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.model.Legal;
import de.terrestris.shogun.migrator.spi.ShogunMigrator;
import de.terrestris.shogun.migrator.util.MigrationException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.Position2D;
import org.geotools.referencing.CRS;
import org.kohsuke.MetaInfServices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static de.terrestris.shogun.migrator.util.ApiUtil.*;

@Log4j2
@MetaInfServices
public class Shogun2Migrator implements ShogunMigrator {

  public static final String CHILDREN = "children";
  public static final String RESOLUTIONS = "resolutions";
  public static final String SEARCHABLE = "searchable";
  public static final String DOUBLE_COLON = "::";
  public static final String COMMA = ",";
  private HostDto source;

  private HostDto target;

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
      JsonNode layerNode = node.get("layer");
      int id;
      if (layerNode.isObject()) {
        id = layerNode.get("id").intValue();
      } else {
        id = layerNode.intValue();
      }
      folder.put("layerId", idMap.get(id));
    }
    if (node.has(CHILDREN)) {
      ArrayNode children = mapper.createArrayNode();
      folder.set(CHILDREN, children);
      for (JsonNode child : node.get(CHILDREN)) {
        children.add(migrateLayerTree(child, mapper, idMap));
      }
    }
    return folder;
  }

  public static byte[] migrateApplication(JsonNode node, Map<Integer, Integer> idMap, Legal legal) throws IOException, FactoryException, TransformException {
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

      String oldProjection = mapConfig.get("projection").asText();
      mapView.put("projection", oldProjection.startsWith("EPSG:") ? oldProjection : ("EPSG:" + oldProjection));

      CoordinateReferenceSystem targetCrs = CRS.decode("EPSG:4326");
      CoordinateReferenceSystem sourceCrs = CRS.decode(mapView.get("projection").asText());
      MathTransform transform = CRS.findMathTransform(sourceCrs, targetCrs);

      final double lowerLeftX = oldExtent.get("lowerLeft").get("x").asDouble();
      final double lowerLeftY = oldExtent.get("lowerLeft").get("y").asDouble();
      Position lowerLeftPos = new Position2D(lowerLeftX, lowerLeftY);
      Position transformedLowerLeftPos = transform.transform(lowerLeftPos, null);
      extent.add(transformedLowerLeftPos.getOrdinate(1));
      extent.add(transformedLowerLeftPos.getOrdinate(0));

      final double upperRightX = oldExtent.get("upperRight").get("x").asDouble();
      final double upperRightY = oldExtent.get("upperRight").get("y").asDouble();
      Position upperRightPos = new Position2D(upperRightX, upperRightY);
      Position transformedUpperRightPos = transform.transform(upperRightPos, null);
      extent.add(transformedUpperRightPos.getOrdinate(1));
      extent.add(transformedUpperRightPos.getOrdinate(0));

      mapView.set("extent", extent);
      mapView.put("zoom", mapConfig.get("zoom").asInt());

      Position centerPos = new Position2D(center.get(0).asDouble(), center.get(1).asDouble());
      Position transformedCenterPos = transform.transform(centerPos, null);
      center.removeAll();
      center.add(transformedCenterPos.getOrdinate(1));
      center.add(transformedCenterPos.getOrdinate(0));

      JsonNode resolutions = mapConfig.get(RESOLUTIONS);
      ArrayNode newResolutions = mapper.createArrayNode();
      for (JsonNode res : resolutions) {
        newResolutions.add(res.asDouble());
      }
      mapView.set(RESOLUTIONS, newResolutions);
      clientConfig.set("mapView", mapView);
    }
    // set legal info for all apps
    if (legal != null) {
      final String contact = legal.getContact();
      final String imprint = legal.getImprint();
      final String privacy = legal.getPrivacy();
      if (contact != null || imprint != null || privacy != null) {
        ObjectNode legalNode = mapper.createObjectNode();
        if (contact != null && !contact.isEmpty()) {
          legalNode.put("contact", contact);
        }
        if (imprint != null && !imprint.isEmpty()) {
          legalNode.put("imprint", imprint);
        }
        if (privacy != null && !privacy.isEmpty()) {
          legalNode.put("privacy", privacy);
        }
        clientConfig.set("legal", legalNode);
      }
    }
    JsonNode layerTree = migrateLayerTree(node.get("layerTree"), mapper, idMap);
    root.set("layerTree", layerTree);
    root.set("clientConfig", clientConfig);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    mapper.writeValue(bout, root);
    return bout.toByteArray();
  }

  private static String mapType(String type) {
      return switch (type) {
          case "TileWMS" -> "TILEWMS";
          case "OSMVectortile" -> "VECTORTILE";
          case "ImageWMS", "WMSTime" -> "WMS";
          case "WMTS" -> "WMTS";
          // AFAICT these two don't exist yet and might be replaced with the proper value once implemented
          case "WFS" -> "WFS";
          case "XYZ" -> "XYZ";
          default -> {
              log.warn("Unable to migrate layer of type {}.", type);
              yield null;
          }
      };
  }

  private static void migrateLayerClientConfig(JsonNode node, ObjectNode config) {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode appearance = node.get("appearance");
    config.put("minResolution", appearance.get("minResolution").textValue());
    config.put("maxResolution", appearance.get("maxResolution").textValue());
    config.put("hoverable", appearance.get("hoverable").booleanValue());
    JsonNode propertyConfig = node.get("columnAliasesDe");
    if (propertyConfig != null && !propertyConfig.isEmpty()) {
      ArrayNode formConfig = mapper.createArrayNode();
      config.set("featureInfoFormConfig", formConfig);
      ObjectNode properties = mapper.createObjectNode();
      formConfig.add(properties);
      properties.put("title", node.get("name").asText());
      ArrayNode list = mapper.createArrayNode();
      properties.set("children", list);

      List<String> propertyList = new ArrayList<>();
      propertyConfig.fieldNames().forEachRemaining(propertyList::add);
      Collections.reverse(propertyList);

      propertyList.forEach(name -> {
        ObjectNode property = mapper.createObjectNode();
        property.put("propertyName", name);
        property.put("displayName", propertyConfig.get(name).asText());
        list.add(property);
      });
    }
    JsonNode searchable = node.get(SEARCHABLE);
    if (searchable != null && searchable.booleanValue()) {
      config.put(SEARCHABLE, searchable.booleanValue());
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
      config.put(SEARCHABLE, false);
    }
  }

  private static void migrateLayerSourceConfig(JsonNode node, ObjectNode config, String replaceLayerUrls) {
    config.put("attribution", node.get("appearance").get("attribution").textValue());
    JsonNode legendUrl = node.get("legendUrl");
    if (legendUrl != null) {
      config.put("legendUrl", legendUrl.asText());
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode oldSource = node.get("source");
    String url = oldSource.get("url").textValue();

    if (replaceLayerUrls != null && replaceLayerUrls.contains(DOUBLE_COLON)) {
      String[] replacements = replaceLayerUrls.split(COMMA);
      for (String replacement : replacements) {
        if (replacement.contains(DOUBLE_COLON)) {
          String[] replacementSplit =  replacement.split(DOUBLE_COLON);
          if (replacementSplit[0].equals(url)) {
            url = replacementSplit[1];
            break;
          }
        }
      }
    }

    if (url.startsWith("http")) {
      config.put("url", url);
    } else {
      config.put("url", "/geoserver/ows");
    }
    final JsonNode layerNames = oldSource.get("layerNames");
    if (layerNames != null) {
      config.put("layerNames", layerNames.textValue());
    } else {
      // check whether we have a wmtsLayer config
      final JsonNode wmtsLayer = oldSource.get("wmtsLayer");
      if(wmtsLayer != null) {
        config.put("layerNames", wmtsLayer.textValue());
      } else {
        config.put("layerNames", "");
      }

    }
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
      config.set(RESOLUTIONS, resolutions);
    }
  }

  public static byte[] migrateLayer(JsonNode node, String replaceLayerUrl) throws IOException {
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

    migrateLayerClientConfig(node, clientConfig);
    migrateLayerSourceConfig(node, sourceConfig, replaceLayerUrl);

    root.set("clientConfig", clientConfig);
    root.set("sourceConfig", sourceConfig);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    mapper.writeValue(bout, root);
    return bout.toByteArray();
  }

  @Override
  public void initialize(HostDto source, HostDto target) {
    this.source = source;
    this.target = target;
  }

  @Override
  public Map<Integer, Integer> migrateLayers(boolean makePublic, String replaceLayerUrls) {
    try {
      JsonNode node = fetch(source, "rest/projectlayers", false);
      Map<Integer, Integer> layerIdMap = new HashMap<>();
//      int i = 0;
      for (JsonNode layer : node) {
        log.info("Migrating layer...");
        byte[] bs = migrateLayer(layer, replaceLayerUrls);
        if (bs == null) {
          continue;
        }
        int newId = saveLayer(bs, target);
        if (makePublic) {
          makeLayerPublic(target, newId);
        }
        layerIdMap.put(layer.get("id").intValue(), newId);
        // use these to create new test files
//        new ObjectMapper().writeValue(new File("/tmp/layer" + ++i + ".json"), layer);
//        OutputStream outputStream = Files.newOutputStream(new File("/tmp/layer" + ++i + ".json").toPath());
//        copy(new ByteArrayInputStream(bs), outputStream);
//        outputStream.close();
      }
      return layerIdMap;
    } catch (Exception e) {
      log.warn("Unable to migrate layers: {}", e.getMessage());
      log.trace("Stack trace:", e);
      throw new MigrationException(e);
    }
  }

  @Override
  public void migrateApplications(Map<Integer, Integer> idMap, Legal legal) {
    try {
      JsonNode node = fetch(source, "rest/projectapps", false);
//      int i = 0;
      for (JsonNode app : node) {
        log.info("Migrating application...");
        byte[] bs = migrateApplication(app, idMap, legal);
        saveApplication(bs, target);
        // use these to create new test files
//        OutputStream outputStream = Files.newOutputStream(new File("/tmp/" + ++i + ".json").toPath());
//        copy(new ByteArrayInputStream(bs), outputStream);
//        outputStream.close();
      }
    } catch (Exception e) {
      log.warn("Unable to migrate applications: {}", e.getMessage());
      log.trace("Stack trace:", e);
      throw new MigrationException(e);
    }
  }

  @Override
  public boolean handlesSourceType(String sourceType) {
    return sourceType.equalsIgnoreCase("shogun2");
  }

}
