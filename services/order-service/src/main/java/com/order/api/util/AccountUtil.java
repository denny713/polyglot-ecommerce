package com.order.api.util;

import com.order.api.exception.ForbiddenException;

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

    /** The user behind the request, or {@code null} outside one, such as in a scheduled job. */
    public static UUID getUserLogin() {
        return USER_LOGIN.get();
    }

    /**
     * The user behind the request, for work that must be done on someone's behalf.
     *
     * @throws ForbiddenException when no user is signed in
     */
    public static UUID requireUserLogin() {
        UUID userLogin = USER_LOGIN.get();
        if (userLogin == null) {
            throw new ForbiddenException("You don't have permission to access this resource");
        }

        return userLogin;
    }

    public static void clearUserLogin() {
        USER_LOGIN.remove();
    }
}
