package com.tracedesk.ai.internal.soclaas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class TransportBoundaryTest {
    @Test
    void rawTransportCannotBeUsedAsAPublicApplicationFacade() {
        assertFalse(Modifier.isPublic(SoclaasClient.class.getModifiers()));
    }

    @Test
    void reusableFakeProviderDrivesARealBoundedCompletion() throws Exception {
        var settings = SoclaasSettings.from(new TestEnvironment()
                .withProperty("AI_ENABLED", "true")
                .withProperty("SOCLAAS_API_KEY", "synthetic-key")
                .withProperty("SOCLAAS_MODEL", "test"));
        try (var provider = new AiGatewayFakeProvider();
             var client = new SoclaasClient(settings, provider.origin())) {
            client.validateModel();
            assertEquals("suggestion", client.complete("Review this synthetic report.", "{}").text());
            assertEquals(1, provider.calls.get());
        }
    }
}
