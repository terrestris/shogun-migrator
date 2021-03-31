package de.terrestris.shogun.migrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

public class MigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"/1.json", "/2.json", "/3.json"})
    public void testMigration(String file) throws IOException {
        JsonNode node = mapper.readTree(MigratorTest.class.getResource(file));
        byte[] bs = Migrator.migrateApplication(node);
        byte[] expected = IOUtils.toByteArray(MigratorTest.class.getResource("/migrated" + file));

        Assertions.assertArrayEquals(expected, bs);
    }

}
