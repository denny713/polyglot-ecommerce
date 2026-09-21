package com.order.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the per-request user holder, including the property that makes it safe:
 * the value belongs to one thread and does not leak to another.
 */
class AccountUtilTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
    }

    @Test
    void shouldReturnNullBeforeAnythingIsSet() {
        assertNull(AccountUtil.getUserLogin(), "a write outside a request must not invent a user");
    }

    @Test
    void shouldReturnWhatWasSet() {
        AccountUtil.setUserLogin(USER);

        assertEquals(USER, AccountUtil.getUserLogin());
    }

    @Test
    void shouldForgetTheUserOnceCleared() {
        AccountUtil.setUserLogin(USER);
        AccountUtil.clearUserLogin();

        // This is what stops a pooled thread carrying one user into the next request.
        assertNull(AccountUtil.getUserLogin());
    }

    @Test
    void shouldAcceptClearingWhenNothingWasSet() {
        AccountUtil.clearUserLogin();

        assertNull(AccountUtil.getUserLogin());
    }

    @Test
    void shouldNotBeVisibleFromAnotherThread() throws ExecutionException, InterruptedException {
        AccountUtil.setUserLogin(USER);

        UUID seenElsewhere = CompletableFuture.supplyAsync(AccountUtil::getUserLogin).get();

        // The documented limitation: a scheduled or async write audits nothing.
        assertNull(seenElsewhere);
        assertEquals(USER, AccountUtil.getUserLogin(), "the request thread keeps its own value");
    }
}
