package de.terrestris.shogun.migrator.spi;

import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.model.Legal;
import de.terrestris.shogun.migrator.model.Theme;

import java.util.Map;

public interface ShogunMigrator {

  /**
   * Initialize the mapper using the source and target host specifications.
   *
   * @param source the source host
   * @param target the target host
   */
  void initialize(HostDto source, HostDto target);

  /**
   * Migrate the layers.
   *
   * @return a map mapping the old ids to the new ids
   */
  Map<Integer, Integer> migrateLayers(boolean makePublic, String replaceLayerUrls);

  /**
   * Migrate the applications.
   *
   * @param idMap              a map mapping the old layer ids to the new ids
   * @param legal              The legal information (http links) to set in all clients
   * @param theme              The theme information (colors, logo, favicon) to set in all clients
   * @param toolConfigFile A relative path to a json file in the classpath that will be used as the tool config for all new apps
   */
  void migrateApplications(Map<Integer, Integer> idMap, Legal legal, Theme theme, String toolConfigFile);

  /**
   * SPI: returns true if this migrator can handle the specified source API type.
   *
   * @param sourceType the source type
   * @return true if this migrator can handle the API type
   */
  boolean handlesSourceType(String sourceType);

}
