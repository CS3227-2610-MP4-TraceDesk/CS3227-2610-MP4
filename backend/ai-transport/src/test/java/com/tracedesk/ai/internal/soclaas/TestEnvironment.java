package com.tracedesk.ai.internal.soclaas;

import java.util.HashMap;

/** Mutable synthetic configuration used to prove settings snapshot their values. */
final class TestEnvironment extends HashMap<String, String> {
    TestEnvironment withProperty(String name, String value) {
        put(name, value);
        return this;
    }
}
