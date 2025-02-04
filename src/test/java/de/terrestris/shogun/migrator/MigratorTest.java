package de.terrestris.shogun.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.terrestris.shogun.migrator.shogun2.Shogun2Migrator;
import org.apache.commons.io.IOUtils;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.HashMap;

class MigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"/1.json", "/2.json", "/3.json"})
    void testMigration(String file) throws IOException, FactoryException, TransformException {
        JsonNode node = mapper.readTree(MigratorTest.class.getResource(file));
        byte[] bs = Shogun2Migrator.migrateApplication(node, new HashMap<>(), null);
        byte[] expected = IOUtils.toByteArray(MigratorTest.class.getResource("/migrated" + file));
        Assertions.assertArrayEquals(expected, bs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/layer1.json", "/layer2.json", "/layer3.json", "/layer4.json", "/layer5.json", "/layer6.json", "/layer7.json", "/layer8.json"})
    void testLayerMigration(String file) throws IOException {
        JsonNode node = mapper.readTree(MigratorTest.class.getResource(file));
        byte[] bs = Shogun2Migrator.migrateLayer(node, null);
        byte[] expected = IOUtils.toByteArray(MigratorTest.class.getResource("/migratedlayer" + file));
        Assertions.assertArrayEquals(expected, bs);
    }

}
