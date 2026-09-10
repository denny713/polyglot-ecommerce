package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.AccountResponse;
import com.ecommerce.auth.model.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field-for-field copy, which is exactly what makes it worth a test: a
 * transposed pair here would put somebody's surname in the email column of
 * every registration and every {@code GET }, and nothing else in the stack
 * would notice.
 */
class AccountResponseMapperTest {

    private final AccountResponseMapper mapper = new AccountResponseMapper();

    @Test
    void shouldCopyEveryFieldIntoTheResponse() {
        AccountResponse response = mapper.toResponse(new Account(
                "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal", true));

        assertEquals("8f1a5c2e", response.id());
        assertEquals("denny.afrizal", response.username());
        assertEquals("denny@mail.com", response.email());
        assertEquals("Denny", response.firstName());
        assertEquals("Afrizal", response.lastName());
        assertTrue(response.enabled());
    }

    @Test
    void shouldRejectANullAccount() {
        assertEquals("account must not be null", assertThrows(IllegalArgumentException.class,
                () -> mapper.toResponse(null)).getMessage());
    }
}
