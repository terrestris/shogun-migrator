# About the migrator


Its purpose is to quickly load applications and layers into a SHOGun boot instance. You can either migrate from a shogun2
based instance or from another SHOGun boot instance. By implementing the `ShogunMigrator` interface you can extend this
to support other sources as well, just add the implementation to the classpath and it will be loaded automatically via SPI
(don't forget to add an entry to the `de.terrestris.shogun.migrator.spi.ShogunMigrator` file in `META-INF/services` in
your jar).

## Run the migrator

You can run the application directly or in an IDE. Run with `-h` to get help:

`java -jar shogun-migrator-0.0.1-SNAPSHOT-jar-with-dependencies.jar -h`

## Command line options

| Parameter | Description                   |
|-----------|-------------------------------|
| `sh`      | Source host                   |
| `su`      | Source user                   |
| `sp`      | Source password               |
| `th`      | Target host                   |
| `tu`      | Target user                   |
| `tp`      | Target password               |
| `tc`      | Target client                 |
| `c`       | Clear (override existing data)|
