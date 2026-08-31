package de.terrestris.shogun.migrator.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MigrationExceptionTest {

    @Test
    void containsFixedMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");

        MigrationException exception = new MigrationException(cause);

        Assertions.assertEquals("Unable to migrate", exception.getMessage());
        Assertions.assertSame(cause, exception.getCause());
    }

}
