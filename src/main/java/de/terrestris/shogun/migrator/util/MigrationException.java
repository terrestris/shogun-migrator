package de.terrestris.shogun.migrator.util;

public class MigrationException extends RuntimeException {

  public MigrationException(Throwable cause) {
    super("Unable to migrate", cause);
  }

}
