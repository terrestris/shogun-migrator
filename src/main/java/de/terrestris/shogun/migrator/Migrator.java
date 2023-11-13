package de.terrestris.shogun.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.spi.ShogunMigrator;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

import static de.terrestris.shogun.migrator.util.ApiUtil.delete;
import static de.terrestris.shogun.migrator.util.ApiUtil.fetch;

@Log4j2
@Command(name = "SHOGun-Migrator", version = "0.0.1", mixinStandardHelpOptions = true)
public class Migrator implements Callable<Boolean> {

  enum Type { shogun2, boot }

  @Option(
    names = {"-t", "--type"},
    description = "specify the type of the source (${COMPLETION-CANDIDATES}), default is shogun2. Note that other types may be added via plugins"
  )
  private Type type = Type.shogun2;

  @Option(
    names = {"-c", "--clear"},
    description = "@|red DANGER|@: delete all layer and application entities from the target system before the migration"
  )
  private boolean clear = false;

  @Option(
    names = {"-sh", "--source-host"},
    required = true,
    description = "the source API endpoint, e.g. https://my-shogun.com/shogun2-webapp/"
  )
  private String sourceHost;

  @Option(
    names = {"-su", "--source-user"},
    required = true,
    description = "the source admin username"
  )
  private String sourceUser;

  @Option(
    names = {"-sp", "--source-password"},
    required = true,
    description = "the source admin password"
  )
  private String sourcePassword;

  @Option(
    names = {"-th", "--target-host"},
    required = true,
    description = "the target API endpoint, e.g. https://my-shogun-boot.com/"
  )
  private String targetHost;

  @Option(
    names = {"-tu", "--target-user"},
    required = true,
    description = "the target admin username"
  )
  private String targetUser;

  @Option(
    names = {"-tp", "--target-password"},
    required = true,
    description = "the target admin password"
  )
  private String targetPassword;

  private static ShogunMigrator getMigrator(Type type) {
    ServiceLoader<ShogunMigrator> loader = ServiceLoader.load(ShogunMigrator.class);
    for (ShogunMigrator migrator : loader) {
      if (migrator.handlesSourceType(type.toString())) {
        return migrator;
      }
    }
    return null;
  }

  private void clear(HostDto target) throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    JsonNode node = fetch(target, "applications");
    for (JsonNode app : node) {
      delete(target, String.format("applications/%s", app.get("id").asInt()));
    }
    node = fetch(target, "layers");
    for (JsonNode app : node) {
      delete(target, String.format("layers/%s", app.get("id").asInt()));
    }
  }

  @Override
  public Boolean call() throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    HostDto source = new HostDto(sourceHost, sourceUser, sourcePassword);
    if (!source.getHostname().endsWith("/")) {
      source.setHostname(source.getHostname() + "/");
    }
    HostDto target = new HostDto(targetHost, targetUser, targetPassword);
    if (!target.getHostname().endsWith("/")) {
      target.setHostname(target.getHostname() + "/");
    }
    ShogunMigrator migrator = getMigrator(type);
    if (migrator == null) {
      log.error("Unable to find migrator for type {}, exiting.", type);
      System.exit(1);
    }
    if (clear) {
      log.info("Deleting old entities...");
      clear(target);
      log.info("Done.");
    }
    migrator.initialize(source, target);
    migrator.migrateApplications(migrator.migrateLayers());
    return true;
  }

  public static void main(String[] args) {
    int code = new CommandLine(new Migrator()).execute(args);
    System.exit(code);
  }

}
