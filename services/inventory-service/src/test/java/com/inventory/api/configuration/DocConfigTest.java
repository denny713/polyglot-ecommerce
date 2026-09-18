package com.inventory.api.configuration;

import com.inventory.api.constant.SecurityType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the OpenAPI description. */
class DocConfigTest {

    private final OpenAPI openAPI = new DocConfig().openAPI();

    @Test
    void shouldDescribeTheService() {
        assertEquals("API for Inventory Service", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertNotNull(openAPI.getInfo().getDescription());
    }

    @Test
    void shouldDeclareABearerJwtScheme() {
        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(SecurityType.SECURITY_SCHEME);

        assertNotNull(scheme, "the scheme name must match what the requirement refers to");
        assertEquals(SecurityScheme.Type.HTTP, scheme.getType());
        assertEquals("bearer", scheme.getScheme());
        assertEquals("JWT", scheme.getBearerFormat());
    }

    @Test
    void shouldApplyTheSchemeToEveryOperation() {
        // A scheme that is declared but not required leaves the button unused.
        assertEquals(1, openAPI.getSecurity().size());
        assertTrue(openAPI.getSecurity().get(0).containsKey(SecurityType.SECURITY_SCHEME));
    }
}
