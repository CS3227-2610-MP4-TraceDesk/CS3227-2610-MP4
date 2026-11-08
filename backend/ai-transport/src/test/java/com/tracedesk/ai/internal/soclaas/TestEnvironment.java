package com.tracedesk.ai.internal.soclaas;

import java.util.HashMap;

/** Mutable synthetic configuration used to prove settings snapshot their values. */
final class TestEnvironment extends HashMap<String, String> {
    TestEnvironment withProperty(String name, String value) {
        put(name, value);
        return this;
    }
    TestEnvironment withoutProperty(String name) { remove(name); return this; }
    TestEnvironment withSponsoredAccess() {
        return withProperty("AI_SPONSORED_ACCESS_ACKNOWLEDGED", "true")
                .withProperty("AI_SPONSORED_APPROVAL_REFERENCE", "synthetic-approval")
                .withProperty("AI_SPONSORED_APPROVAL_EXPIRES_AT", "2099-01-01T00:00:00Z")
                .withProperty("AI_GLOBAL_DAILY_UNITS", "100000")
                .withProperty("AI_ACTOR_DAILY_UNITS", "20000");
    }
}
