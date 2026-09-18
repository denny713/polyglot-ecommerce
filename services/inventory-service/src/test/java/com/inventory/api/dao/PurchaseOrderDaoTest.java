package com.inventory.api.dao;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.entity.PurchaseOrder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Tests which filters a search request turns into. */
class PurchaseOrderDaoTest {

    private final PurchaseOrderDao dao = new PurchaseOrderDao();

    private CriteriaBuilder cb;
    private Root<PurchaseOrder> root;
    private CriteriaQuery<?> query;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
    }

    private void build(POSearchReq req) {
        dao.buildSearchPO(req).toPredicate(root, query, cb);
    }

    // ------------------------------------------------------------------
    // an empty request must not filter anything
    // ------------------------------------------------------------------

    @Test
    void shouldApplyNothingForAnEmptyRequest() {
        build(new POSearchReq());

        verify(cb, never()).like(any(), anyString());
        verify(cb, never()).equal(any(), any());
        verify(cb, never()).greaterThanOrEqualTo(any(), any(BigDecimal.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(BigDecimal.class));
        verify(cb, never()).isTrue(any());
        verify(cb, never()).isFalse(any());
    }

    @Test
    void shouldStillProduceAConjunctionForAnEmptyRequest() {
        build(new POSearchReq());

        // An empty AND matches everything, which is what an empty search means.
        // cb.and is varargs and the DAO passes an array, so the captor has to be
        // typed as the array rather than the element.
        ArgumentCaptor<Predicate[]> applied = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(applied.capture());
        assertEquals(0, applied.getValue().length);
    }

    // ------------------------------------------------------------------
    // individual filters
    // ------------------------------------------------------------------

    @Test
    void shouldMatchTheDocumentNumberCaseInsensitively() {
        POSearchReq req = new POSearchReq();
        req.setDocumentNumber("PO/2026");

        build(req);

        // Both sides are lowered, so the caller's casing does not matter.
        verify(cb).lower(any());
        verify(cb).like(any(), eq("%po/2026%"));
    }

    @Test
    void shouldSkipABlankDocumentNumber() {
        POSearchReq req = new POSearchReq();
        req.setDocumentNumber("");

        build(req);

        verify(cb, never()).like(any(), anyString());
    }

    @Test
    void shouldFilterBySupplier() {
        POSearchReq req = new POSearchReq();
        req.setSupplierId(3L);

        build(req);

        verify(cb).equal(any(), eq(3L));
    }

    @Test
    void shouldFilterByStatus() {
        POSearchReq req = new POSearchReq();
        req.setStatus(DocStatus.APPROVED);

        build(req);

        verify(cb).equal(any(), eq(DocStatus.APPROVED));
    }

    @Test
    void shouldFilterBothGrandTotalRanges() {
        POSearchReq req = new POSearchReq();
        req.setMinOrderGrandTotal(new BigDecimal("100"));
        req.setMaxOrderGrandTotal(new BigDecimal("900"));
        req.setMinRealGrandTotal(new BigDecimal("50"));
        req.setMaxRealGrandTotal(new BigDecimal("800"));

        build(req);

        verify(cb).greaterThanOrEqualTo(any(), eq(new BigDecimal("100")));
        verify(cb).lessThanOrEqualTo(any(), eq(new BigDecimal("900")));
        verify(cb).greaterThanOrEqualTo(any(), eq(new BigDecimal("50")));
        verify(cb).lessThanOrEqualTo(any(), eq(new BigDecimal("800")));
    }

    @Test
    void shouldSkipAZeroMoneyBound() {
        POSearchReq req = new POSearchReq();
        req.setMinOrderGrandTotal(BigDecimal.ZERO);
        req.setMaxOrderGrandTotal(BigDecimal.ZERO);

        build(req);

        // Zero is treated as "not set", so a free line is not excluded by accident.
        verify(cb, never()).greaterThanOrEqualTo(any(), any(BigDecimal.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(BigDecimal.class));
    }

    @Test
    void shouldSkipANegativeMoneyBound() {
        POSearchReq req = new POSearchReq();
        req.setMinOrderGrandTotal(new BigDecimal("-5"));

        build(req);

        verify(cb, never()).greaterThanOrEqualTo(any(), any(BigDecimal.class));
    }

    @Test
    void shouldFilterTheCreatedRange() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);

        POSearchReq req = new POSearchReq();
        req.setCreatedFrom(from);
        req.setCreatedTo(to);

        build(req);

        verify(cb).greaterThanOrEqualTo(any(), eq(from));
        verify(cb).lessThanOrEqualTo(any(), eq(to));
    }

    @Test
    void shouldFilterOnActiveRows() {
        POSearchReq req = new POSearchReq();
        req.setIsActive(true);

        build(req);

        verify(cb).isTrue(any());
        verify(cb, never()).isFalse(any());
    }

    @Test
    void shouldFilterOnInactiveRows() {
        POSearchReq req = new POSearchReq();
        req.setIsActive(false);

        build(req);

        verify(cb).isFalse(any());
        verify(cb, never()).isTrue(any());
    }

    // ------------------------------------------------------------------
    // combinations
    // ------------------------------------------------------------------

    @Test
    void shouldCombineEveryFilterIntoOneConjunction() {
        POSearchReq req = new POSearchReq();
        req.setDocumentNumber("PO");
        req.setSupplierId(3L);
        req.setStatus(DocStatus.DRAFT);
        req.setMinOrderGrandTotal(new BigDecimal("100"));
        req.setIsActive(true);

        build(req);

        ArgumentCaptor<Predicate[]> applied = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(applied.capture());

        // document number, supplier, status, min order total, active.
        assertEquals(5, applied.getValue().length);
    }
}
