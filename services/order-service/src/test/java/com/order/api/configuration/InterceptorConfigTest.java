package com.order.api.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the correlation id interceptor. */
class InterceptorConfigTest {

    private final InterceptorConfig interceptor = new InterceptorConfig();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPutACorrelationIdInTheLoggingContext() {
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

        String id = MDC.get("correlationId");

        assertNotNull(id, "the log pattern prints this key");
        // A malformed value would still print, so the shape is worth asserting.
        assertDoesNotThrow(() -> UUID.fromString(id));
    }

    @Test
    void shouldReturnTheIdToTheCallerAsAHeader() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertEquals(MDC.get("correlationId"), response.getHeader("X-Correlation-Id"),
                "the caller must be able to quote the same id back");
    }

    @Test
    void shouldAlwaysLetTheRequestProceed() {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    @Test
    void shouldGiveEachRequestItsOwnId() {
        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        String first = MDC.get("correlationId");

        interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        String second = MDC.get("correlationId");

        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }

    @Test
    void shouldRemoveTheIdWhenTheRequestEnds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        // Left behind, it would tag the next request on this pooled thread.
        assertNull(MDC.get("correlationId"));
    }

    // ------------------------------------------------------------------
    // the guards on the arguments themselves
    // ------------------------------------------------------------------

    @Test
    void shouldRefuseAMissingRequestOrResponseOrHandler() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Spring never passes null here, so these guards only fire when the
        // interceptor is wired up by hand or called from a test.
        assertThrows(NullPointerException.class, () -> interceptor.preHandle(null, response, new Object()));
        assertThrows(NullPointerException.class, () -> interceptor.preHandle(request, null, new Object()));
        assertThrows(NullPointerException.class, () -> interceptor.preHandle(request, response, null));
    }

    @Test
    void shouldRefuseAMissingArgumentWhenTheRequestEnds() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(NullPointerException.class,
                () -> interceptor.afterCompletion(null, response, new Object(), null));
        assertThrows(NullPointerException.class,
                () -> interceptor.afterCompletion(request, null, new Object(), null));
        assertThrows(NullPointerException.class,
                () -> interceptor.afterCompletion(request, response, null, null));
    }

    @Test
    void shouldStillCleanUpWhenTheRequestFailed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), new IllegalStateException("boom"));

        assertNull(MDC.get("correlationId"));
    }
}
