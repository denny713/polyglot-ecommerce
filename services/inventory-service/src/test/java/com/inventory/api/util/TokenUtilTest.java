package com.inventory.api.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the one rule this helper owns: anything that is not a usable bearer
 * token comes back as null, so the filter has a single case to handle.
 */
class TokenUtilTest {

    private static MockHttpServletRequest withAuthorization(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader("Authorization", value);
        }

        return request;
    }

    @Test
    void shouldReturnTheTokenAfterThePrefix() {
        assertEquals("abc.def.ghi", TokenUtil.getToken(withAuthorization("Bearer abc.def.ghi")));
    }

    @Test
    void shouldReturnNullWhenTheHeaderIsAbsent() {
        assertNull(TokenUtil.getToken(withAuthorization(null)));
    }

    @Test
    void shouldReturnNullWhenTheHeaderIsEmpty() {
        assertNull(TokenUtil.getToken(withAuthorization("")));
    }

    @Test
    void shouldReturnNullForADifferentScheme() {
        // Basic auth must not be mistaken for a token.
        assertNull(TokenUtil.getToken(withAuthorization("Basic YWRtaW46cGFzc3dvcmQ=")));
    }

    @Test
    void shouldReturnNullWhenThePrefixIsNotAtTheStart() {
        assertNull(TokenUtil.getToken(withAuthorization("token=Bearer abc")));
    }

    @Test
    void shouldReturnNullWhenNothingFollowsThePrefix() {
        assertNull(TokenUtil.getToken(withAuthorization("Bearer ")));
    }

    @Test
    void shouldReturnNullWhenOnlyWhitespaceFollowsThePrefix() {
        assertNull(TokenUtil.getToken(withAuthorization("Bearer    ")));
    }

    @Test
    void shouldTrimSurroundingWhitespace() {
        assertEquals("abc.def.ghi", TokenUtil.getToken(withAuthorization("Bearer  abc.def.ghi  ")));
    }

    @Test
    void shouldBeCaseSensitiveAboutTheScheme() {
        // The prefix check is exact, so a lower-case scheme is not accepted.
        assertNull(TokenUtil.getToken(withAuthorization("bearer abc.def.ghi")));
    }
}
