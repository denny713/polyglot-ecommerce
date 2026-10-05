package com.inventory.api.dao;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.entity.PurchaseReturn;
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

/** Tests which filters a purchase return search turns into. */
class PurchaseReturnDaoTest {

    private final PurchaseReturnDao dao = new PurchaseReturnDao();

    private CriteriaBuilder cb;
    private Root<PurchaseReturn> root;
    private CriteriaQuery<?> query;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
    }

    private void build(PRSearchReq req) {
        dao.buildSearchPR(req).toPredicate(root, query, cb);
    }

    @Test
    void shouldApplyNothingForAnEmptyRequest() {
        build(new PRSearchReq());

        verify(cb, never()).like(any(), anyString());
        verify(cb, never()).equal(any(), any());
        verify(cb, never()).isTrue(any());
        verify(cb, never()).isFalse(any());
    }

    @Test
    void shouldMatchTheDocumentNumberCaseInsensitively() {
        PRSearchReq req = new PRSearchReq();
        req.setDocumentNumber("PR/2026");

        build(req);

        verify(cb).like(any(), eq("%pr/2026%"));
    }

    @Test
    void shouldSkipABlankDocumentNumber() {
        PRSearchReq req = new PRSearchReq();
        req.setDocumentNumber("");

        build(req);

        verify(cb, never()).like(any(), anyString());
    }

    @Test
    void shouldFilterBySupplierAndStatus() {
        PRSearchReq req = new PRSearchReq();
        req.setSupplierId(3L);
        req.setStatus(DocStatus.CANCELLED);

        build(req);

        verify(cb).equal(any(), eq(3L));
        verify(cb).equal(any(), eq(DocStatus.CANCELLED));
    }

    @Test
    void shouldFilterTheSingleGrandTotalRange() {
        PRSearchReq req = new PRSearchReq();
        req.setMinGrandTotal(new BigDecimal("100"));
        req.setMaxGrandTotal(new BigDecimal("900"));

        build(req);

        verify(cb).greaterThanOrEqualTo(any(), eq(new BigDecimal("100")));
        verify(cb).lessThanOrEqualTo(any(), eq(new BigDecimal("900")));
    }

    @Test
    void shouldSkipANonPositiveGrandTotalBound() {
        PRSearchReq req = new PRSearchReq();
        req.setMinGrandTotal(BigDecimal.ZERO);
        req.setMaxGrandTotal(new BigDecimal("-1"));

        build(req);

        verify(cb, never()).greaterThanOrEqualTo(any(), any(BigDecimal.class));
        verify(cb, never()).lessThanOrEqualTo(any(), any(BigDecimal.class));
    }

    @Test
    void shouldFilterTheCreatedRange() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);

        PRSearchReq req = new PRSearchReq();
        req.setCreatedFrom(from);

        build(req);

        verify(cb).greaterThanOrEqualTo(any(), eq(from));
    }

    @Test
    void shouldFilterOnActiveRows() {
        PRSearchReq req = new PRSearchReq();
        req.setIsActive(true);

        build(req);

        verify(cb).isTrue(any());
    }

    @Test
    void shouldFilterOnInactiveRows() {
        PRSearchReq req = new PRSearchReq();
        req.setIsActive(false);

        build(req);

        verify(cb).isFalse(any());
    }

    @Test
    void shouldCombineEveryFilterIntoOneConjunction() {
        PRSearchReq req = new PRSearchReq();
        req.setDocumentNumber("PR");
        req.setSupplierId(3L);
        req.setStatus(DocStatus.DRAFT);
        req.setIsActive(false);

        build(req);

        ArgumentCaptor<Predicate[]> applied = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(applied.capture());
        assertEquals(4, applied.getValue().length);
    }
}
