package com.tracedesk.testing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IsolatedPostgresGuardTest {
    private static Map<String, String> settings() {
        Map<String, String> values = new HashMap<>();
        values.put("TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED", "true");
        values.put("TRACEDESK_TEST_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/tracedesk_test");
        values.put("TRACEDESK_TEST_DATABASE_USER", "synthetic");
        values.put("TRACEDESK_TEST_DATABASE_PASSWORD", "synthetic");
        return values;
    }

    @Test
    void rejectsMissingConfirmationBeforeConnecting() {
        var values = settings();
        values.remove("TRACEDESK_TEST_DATABASE_CONFIRM_ISOLATED");
        assertThrows(IllegalArgumentException.class, () -> IsolatedPostgres.open(values));
    }

    @Test
    void rejectsProductionTargetEvenWithDifferentJdbcPrefix() {
        var values = settings();
        values.put("DATABASE_URL", "postgresql://127.0.0.1/tracedesk_test");
        assertThrows(IllegalArgumentException.class, () -> IsolatedPostgres.open(values));
    }

    @Test
    void rejectsNonTestNamesAndConnectionParameters() {
        for (String url : new String[]{"jdbc:postgresql://127.0.0.1/tracedesk",
                "jdbc:postgresql://127.0.0.1/tracedesk_test?currentSchema=public",
                "jdbc:postgresql://user:password@127.0.0.1/tracedesk_test"}) {
            var values = settings();
            values.put("TRACEDESK_TEST_JDBC_URL", url);
            var failure = assertThrows(IllegalArgumentException.class, () -> IsolatedPostgres.open(values));
            assertFalse(failure.toString().contains("synthetic"));
        }
    }
}
