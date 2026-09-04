package com.inventory.api.util;

import java.util.UUID;

public class AccountUtil {

    private AccountUtil() {
        super();
    }

    public static UUID getUserLogin() {
        return UUID.randomUUID();
    }
}
