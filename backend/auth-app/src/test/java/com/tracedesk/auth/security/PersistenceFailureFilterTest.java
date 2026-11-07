package com.tracedesk.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistenceFailureFilterTest {
    private final PersistenceFailureFilter filter = new PersistenceFailureFilter();

    @Test
    void unhandledApiFailureDoesNotCopyPartialResponseBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, (req, res) -> {
            res.getWriter().write("partial response");
            throw new IllegalStateException("synthetic non-persistence failure");
        }));

        assertEquals("", response.getContentAsString());
    }

    @Test
    void persistenceFailureReplacesBufferedApiBodyWithGenericUnavailableResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/csrf");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            res.getWriter().write("partial response");
            throw new DataAccessResourceFailureException("synthetic persistence failure");
        });

        assertEquals(503, response.getStatus());
        assertEquals("{\"error\":{\"code\":\"SERVICE_UNAVAILABLE\",\"message\":\"Service unavailable\"}}",
                response.getContentAsString());
        assertEquals("max-age=31536000; includeSubDomains", response.getHeader("Strict-Transport-Security"));
    }
}
