package com.tracedesk.auth.projects.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectNameTest {
    @Test void normalizesUnicodeAndEnforcesBoundaries() {
        assertEquals("Café",ProjectName.parse("\u00a0Cafe\u0301\u2003").value());
        assertThrows(IllegalArgumentException.class,()->ProjectName.parse("  \t "));
        assertThrows(IllegalArgumentException.class,()->ProjectName.parse("bad\nname"));
        assertThrows(IllegalArgumentException.class,()->ProjectName.parse("x".repeat(121)));
        assertThrows(IllegalArgumentException.class,()->ProjectName.parse("😀".repeat(121)));
    }
}
