package com.inventory.api.constant;

/**
 * Names of the three state changes {@code CommonRepositoryImpl} guards.
 * <p>
 * They are passed to its {@code validate} method, which picks the precondition to
 * enforce: deleting only rejects an already-deleted row, while activating and
 * deactivating also reject a row that is already in the target state.
 */
public class ActionType {

    public static final String DEL = "delete";
    public static final String ACT = "activate";
    public static final String DCT = "deactivate";
}
