package com.inventory.api.model.entity.base;

import com.inventory.api.model.entity.PurchaseOrder;
import com.inventory.api.model.entity.PurchaseOrderDetail;
import com.inventory.api.model.entity.PurchaseReturn;
import com.inventory.api.model.entity.PurchaseReturnDetail;
import com.inventory.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the auditing and soft-delete behaviour every entity inherits.
 * <p>
 * The cascade is the part worth pinning: deleting a document has to reach its
 * lines, or they would survive as orphans that the {@code @SQLRestriction} no
 * longer hides.
 */
class BaseTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
    }

    /** A leaf entity, standing in for anything without children. */
    private static class Leaf extends Base {
    }

    // ------------------------------------------------------------------
    // lifecycle callbacks
    // ------------------------------------------------------------------

    @Test
    void shouldStampANewRowAsActiveAndNotDeleted() {
        Leaf entity = new Leaf();

        entity.prePersist();

        assertTrue(entity.getIsActive());
        assertFalse(entity.getIsDeleted());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    void shouldStampTheLoggedInUserOnInsert() {
        AccountUtil.setUserLogin(USER);
        Leaf entity = new Leaf();

        entity.prePersist();

        assertEquals(USER, entity.getCreatedBy());
        assertEquals(USER, entity.getUpdatedBy());
    }

    @Test
    void shouldLeaveTheAuditColumnsEmptyOutsideARequest() {
        Leaf entity = new Leaf();

        entity.prePersist();

        // The documented consequence of the holder being thread bound.
        assertNull(entity.getCreatedBy());
        assertNull(entity.getUpdatedBy());
    }

    @Test
    void shouldOnlyTouchTheUpdateColumnsOnUpdate() {
        AccountUtil.setUserLogin(USER);
        Leaf entity = new Leaf();
        entity.prePersist();

        UUID originalAuthor = UUID.fromString("99999999-8888-7777-6666-555555555555");
        entity.setCreatedBy(originalAuthor);
        LocalDateTime originalCreatedAt = entity.getCreatedAt();

        entity.preUpdate();

        assertEquals(originalAuthor, entity.getCreatedBy(), "who created the row never changes");
        assertEquals(originalCreatedAt, entity.getCreatedAt());
        assertEquals(USER, entity.getUpdatedBy());
    }

    // ------------------------------------------------------------------
    // soft delete and visibility, with the cascade
    // ------------------------------------------------------------------

    @Test
    void shouldSoftDeleteALeafWithoutChildren() {
        Leaf entity = new Leaf();
        entity.prePersist();

        entity.doDelete();

        assertTrue(entity.getIsDeleted());
    }

    @Test
    void shouldCascadeADeleteToPurchaseOrderLines() {
        PurchaseOrderDetail first = new PurchaseOrderDetail();
        PurchaseOrderDetail second = new PurchaseOrderDetail();

        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderDetails(new ArrayList<>(List.of(first, second)));

        po.doDelete();

        assertTrue(po.getIsDeleted());
        assertTrue(first.getIsDeleted(), "a line left behind would become an orphan");
        assertTrue(second.getIsDeleted());
    }

    @Test
    void shouldCascadeActivateAndDeactivateToPurchaseOrderLines() {
        PurchaseOrderDetail line = new PurchaseOrderDetail();
        PurchaseOrder po = new PurchaseOrder();
        po.setPurchaseOrderDetails(new ArrayList<>(List.of(line)));

        po.doDeactivate();
        assertFalse(po.getIsActive());
        assertFalse(line.getIsActive());

        po.doActivate();
        assertTrue(po.getIsActive());
        assertTrue(line.getIsActive());
    }

    @Test
    void shouldCascadeToPurchaseReturnLines() {
        PurchaseReturnDetail line = new PurchaseReturnDetail();
        PurchaseReturn pr = new PurchaseReturn();
        pr.setPurchaseReturnDetails(new ArrayList<>(List.of(line)));

        pr.doDelete();

        assertTrue(pr.getIsDeleted());
        assertTrue(line.getIsDeleted());
    }

    @Test
    void shouldSurviveADocumentWhoseLinesWereNeverLoaded() {
        // A lazy association that was never touched is null, not empty.
        PurchaseOrder po = new PurchaseOrder();
        PurchaseReturn pr = new PurchaseReturn();

        po.doDelete();
        pr.doDelete();

        assertTrue(po.getIsDeleted());
        assertTrue(pr.getIsDeleted());
    }

    @Test
    void shouldReportNoChildrenForALeaf() {
        assertEquals(List.of(), new Leaf().cascadeChildren());
    }
}
