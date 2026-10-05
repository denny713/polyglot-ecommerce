package com.order.api.model.entity.base;

import com.order.api.model.entity.Product;
import com.order.api.model.entity.SalesOrder;
import com.order.api.model.entity.SalesOrderDetail;
import com.order.api.util.AccountUtil;
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

/** Tests the auditing and soft-delete behaviour every entity inherits. */
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
    void shouldCascadeADeleteToSalesOrderLines() {
        SalesOrderDetail first = new SalesOrderDetail();
        SalesOrderDetail second = new SalesOrderDetail();

        SalesOrder so = new SalesOrder();
        so.setSalesOrderDetails(new ArrayList<>(List.of(first, second)));

        so.doDelete();

        assertTrue(so.getIsDeleted());
        assertTrue(first.getIsDeleted(), "a line left behind would become an orphan");
        assertTrue(second.getIsDeleted());
    }

    @Test
    void shouldCascadeActivateAndDeactivateToSalesOrderLines() {
        SalesOrderDetail line = new SalesOrderDetail();
        SalesOrder so = new SalesOrder();
        so.setSalesOrderDetails(new ArrayList<>(List.of(line)));

        so.doDeactivate();
        assertFalse(so.getIsActive());
        assertFalse(line.getIsActive());

        so.doActivate();
        assertTrue(so.getIsActive());
        assertTrue(line.getIsActive());
    }

    @Test
    void shouldSurviveAnOrderWhoseLinesWereNeverLoaded() {
        // A lazy association that was never touched is null, not empty.
        SalesOrder so = new SalesOrder();

        so.doDelete();

        assertTrue(so.getIsDeleted());
    }

    @Test
    void shouldReportNoChildrenForALeaf() {
        assertEquals(List.of(), new Leaf().cascadeChildren());
    }

    @Test
    void shouldTreatAProductAsALeaf() {
        // Products are read from another service and own nothing here.
        Product product = new Product();

        product.doDelete();

        assertTrue(product.getIsDeleted());
    }
}
