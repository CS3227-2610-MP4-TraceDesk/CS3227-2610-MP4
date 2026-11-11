package com.tracedesk.auth.projects.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectDirectoryCursorTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "00000000-0000-0000-0000-00000000000a",
            "00000000-0000-0000-0000-000000000001"
    })
    void cursorIsOpaqueBoundAndTamperEvident(String projectId) {
        String secret = "synthetic-project-cursor-secret-for-tests-0001";
        var signer = new ProjectDirectoryCursor(secret);
        UUID id = UUID.fromString(projectId);
        String cursor = signer.encode("alpha", id);
        assertEquals(new ProjectDirectoryCursor.Position("alpha", id), signer.decode(cursor));

        // Change a fully significant signature character; replacing the last one
        // with a fixed value can leave the original cursor unchanged.
        int signatureStart = cursor.indexOf('.') + 1;
        char replacement = cursor.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, signatureStart) + replacement + cursor.substring(signatureStart + 1);
        assertNotEquals(cursor, tampered);
        assertThrows(IllegalArgumentException.class, () -> signer.decode(tampered));
        assertThrows(IllegalArgumentException.class, () -> new ProjectDirectoryCursor(
                "different-synthetic-project-cursor-secret-0002").decode(cursor));
        assertEquals(new ProjectDirectoryCursor.Position("alpha", id),
                new ProjectDirectoryCursor(secret).decode(cursor),
                "replicas with the configured shared secret accept the same cursor");
    }
}
