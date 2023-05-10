package de.terrestris.shogun.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import de.terrestris.shogun.migrator.model.HostDto;
import de.terrestris.shogun.migrator.spi.ShogunMigrator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ServiceLoader;

import static de.terrestris.shogun.migrator.util.ApiUtil.delete;
import static de.terrestris.shogun.migrator.util.ApiUtil.fetch;

@Log4j2
public class Migrator {

  private static final Options OPTIONS = new Options()
      .addOption(Option.builder()
        .hasArg(true)
        .argName("type")
        .optionalArg(true)
        .option("t")
        .longOpt("type")
        .desc("specify the type of the source (shogun2, boot), default is shogun2. Note that other types may be added via plugins")
        .build())
      .addOption("h", "help", false, "display this help message and exit")
      .addOption("c", "clear", false, "DANGER: delete all layer and application entities from the target system before the migration")
      .addRequiredOption("sh", "source-host", true, "the source API endpoint, e.g. https://my-shogun.com/shogun2-webapp/")
      .addRequiredOption("su", "source-user", true, "the source admin username")
      .addRequiredOption("sp", "source-password", true, "the source admin password")
      .addRequiredOption("th", "target-host", true, "the target API endpoint, e.g. https://my-shogun-boot.com/")
      .addRequiredOption("tu", "target-user", true, "the target admin username")
      .addRequiredOption("tp", "target-password", true, "the target admin password");

  private static CommandLine parseOptions(String[] args) throws ParseException {
    CommandLineParser parser = new DefaultParser();
    return parser.parse(OPTIONS, args);
  }

  private static void printHelp() {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp(
      150,
      "java -jar shogun-migrator.jar",
      "SHOGun migrator",
      OPTIONS,
      "Check out the source: https://github.com/terrestris/shogun-migrator/",
      true
    );
  }

  private static ShogunMigrator getMigrator(String type) {
    ServiceLoader<ShogunMigrator> loader = ServiceLoader.load(ShogunMigrator.class);
    for (ShogunMigrator migrator : loader) {
      if (migrator.handlesSourceType(type)) {
        return migrator;
      }
    }
    return null;
  }

  private static void clear(HostDto target) throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    JsonNode node = fetch(target, "applications");
    for (JsonNode app : node) {
      delete(target, String.format("applications/%s", app.get("id").asInt()));
    }
    node = fetch(target, "layers");
    for (JsonNode app : node) {
      delete(target, String.format("layers/%s", app.get("id").asInt()));
    }
  }

  private static void handleOptions(CommandLine cmd) throws IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    if (cmd.hasOption("h")) {
      printHelp();
      System.exit(0);
    }
    HostDto source = new HostDto(cmd.getOptionValue("sh"), cmd.getOptionValue("su"), cmd.getOptionValue("sp"));
    if (!source.getHostname().endsWith("/")) {
      source.setHostname(source.getHostname() + "/");
    }
    HostDto target = new HostDto(cmd.getOptionValue("th"), cmd.getOptionValue("tu"), cmd.getOptionValue("tp"));
    if (!target.getHostname().endsWith("/")) {
      target.setHostname(target.getHostname() + "/");
    }
    String type = cmd.getOptionValue("t", "shogun2");
    boolean clear = cmd.hasOption("c");
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
  }

  public static void main(String[] args) {
    try {
      CommandLine cmd = parseOptions(args);
      handleOptions(cmd);
    } catch (Exception e) {
      log.error("Error when migrating applications: {}", e.getMessage());
      log.trace("Stack trace:", e);
      printHelp();
    }
  }

}
