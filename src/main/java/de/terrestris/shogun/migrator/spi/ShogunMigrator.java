package de.terrestris.shogun.migrator.spi;

import de.terrestris.shogun.migrator.model.HostDto;

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
  Map<Integer, Integer> migrateLayers();

  /**
   * Migrate the applications.
   *
   * @param idMap a map mapping the old layer ids to the new ids
   */
  void migrateApplications(Map<Integer, Integer> idMap);

  /**
   * SPI: returns true if this migrator can handle the specified source API type.
   *
   * @param sourceType the source type
   * @return true if this migrator can handle the API type
   */
  boolean handlesSourceType(String sourceType);

}
