package com.order.api.util;

import java.util.UUID;

/** Holds the id of the user behind the request currently being served. */
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
