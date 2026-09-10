package com.inventory.api.util;

import java.util.UUID;

/**
 * Holds the id of the user behind the request currently being served.
 * <p>
 * It exists so {@code Base} can stamp {@code created_by} and {@code updated_by}
 * without every service passing an id down to it. {@code TokenFilter} sets the
 * value from the verified token and clears it when the request ends.
 * <p>
 * The value is bound to the thread, so it is only visible on the request thread:
 * anything writing from a scheduled task, an async dispatch or a different thread
 * reads null and audits nothing.
 */
public class AccountUtil {

    private static final ThreadLocal<UUID> USER_LOGIN = new ThreadLocal<>();

    private AccountUtil() {
        super();
    }

    public static void setUserLogin(UUID userLogin) {
        USER_LOGIN.set(userLogin);
    }

    public static UUID getUserLogin() {
        return USER_LOGIN.get();
    }

    public static void clearUserLogin() {
        USER_LOGIN.remove();
    }
}
