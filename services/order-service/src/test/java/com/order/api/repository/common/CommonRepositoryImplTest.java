package com.order.api.repository.common;

import com.order.api.exception.BadRequestException;
import com.order.api.exception.NotFoundException;
import com.order.api.model.dto.request.PageReq;
import com.order.api.model.entity.SalesOrder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/** Tests the behaviour every repository in this service inherits. */
class CommonRepositoryImplTest {

    private CommonRepositoryImpl<SalesOrder, Long> repository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        JpaEntityInformation<SalesOrder, ?> entityInfo = mock(JpaEntityInformation.class, RETURNS_DEEP_STUBS);
        doReturn(SalesOrder.class).when(entityInfo).getJavaType();

        EntityManager entityMgr = mock(EntityManager.class, RETURNS_DEEP_STUBS);

        repository = spy(new CommonRepositoryImpl<>(entityInfo, entityMgr));
    }

    private static SalesOrder order(Boolean active, Boolean deleted) {
        SalesOrder po = new SalesOrder();
        po.setId(50L);
        po.setIsActive(active);
        po.setIsDeleted(deleted);

        return po;
    }

    private void stored(SalesOrder po) {
        doReturn(Optional.of(po)).when(repository).findById(50L);
        doReturn(po).when(repository).save(po);
    }

    // ------------------------------------------------------------------
    // doGet
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheStoredRow() {
        SalesOrder po = order(true, false);
        stored(po);

        assertSame(po, repository.doGet(50L));
    }

    @Test
    void shouldReportAMissingRowWithItsEntityAndId() {
        doReturn(Optional.empty()).when(repository).findById(404L);

        NotFoundException thrown = assertThrows(NotFoundException.class, () -> repository.doGet(404L));

        // The message names the API's own type, which is what the caller sees.
        assertEquals("Data SalesOrder with id 404 not found", thrown.getMessage());
    }

    // ------------------------------------------------------------------
    // doDelete
    // ------------------------------------------------------------------

    @Test
    void shouldSoftDeleteAndSave() {
        SalesOrder po = order(true, false);
        stored(po);

        repository.doDelete(50L);

        assertTrue(po.getIsDeleted(), "the row must be flagged, not removed");
        verify(repository).save(po);
    }

    @Test
    void shouldRefuseToDeleteTwice() {
        SalesOrder po = order(true, true);
        stored(po);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> repository.doDelete(50L));

        assertEquals("Data SalesOrder with id 50 already deleted, process cannot be continued",
                thrown.getMessage());
        verify(repository, never()).save(any(SalesOrder.class));
    }

    // ------------------------------------------------------------------
    // doActivate and doDeactivate
    // ------------------------------------------------------------------

    @Test
    void shouldActivateAnInactiveRow() {
        SalesOrder po = order(false, false);
        stored(po);

        repository.doActivate(50L);

        assertTrue(po.getIsActive());
        verify(repository).save(po);
    }

    @Test
    void shouldRefuseToActivateSomethingAlreadyActive() {
        SalesOrder po = order(true, false);
        stored(po);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> repository.doActivate(50L));

        // Reported rather than silently ignored, so a double click is visible.
        assertEquals("Data SalesOrder with id 50 already active, process cannot be continued",
                thrown.getMessage());
        verify(repository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldDeactivateAnActiveRow() {
        SalesOrder po = order(true, false);
        stored(po);

        repository.doDeactivate(50L);

        assertFalse(po.getIsActive());
        verify(repository).save(po);
    }

    @Test
    void shouldRefuseToDeactivateSomethingAlreadyInactive() {
        SalesOrder po = order(false, false);
        stored(po);

        BadRequestException thrown = assertThrows(BadRequestException.class, () -> repository.doDeactivate(50L));

        assertEquals("Data SalesOrder with id 50 already inactive, process cannot be continued",
                thrown.getMessage());
    }

    @Test
    void shouldRefuseAnyStateChangeOnADeletedRow() {
        SalesOrder po = order(false, true);
        stored(po);

        // The delete check runs first, whatever the action.
        assertThrows(BadRequestException.class, () -> repository.doActivate(50L));
        assertThrows(BadRequestException.class, () -> repository.doDeactivate(50L));
    }

    // ------------------------------------------------------------------
    // doSearch: the paging defaults
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Pageable capturedPageable(PageReq req) {
        Specification<SalesOrder> spec = mock(Specification.class);
        doReturn(new PageImpl<>(List.of())).when(repository).findAll(any(Specification.class), any(Pageable.class));

        repository.doSearch(spec, req);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(eq(spec), pageable.capture());

        return pageable.getValue();
    }

    @Test
    void shouldDefaultToTheFirstPageOfTenSortedByIdAscending() {
        Pageable pageable = capturedPageable(new PageReq());

        // A missing size must not turn into an unbounded query.
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.ASC, "id"), pageable.getSort());
    }

    @Test
    void shouldHonourAnExplicitPageAndSize() {
        PageReq req = new PageReq();
        req.setPage(3);
        req.setSize(25);

        Pageable pageable = capturedPageable(req);

        assertEquals(3, pageable.getPageNumber());
        assertEquals(25, pageable.getPageSize());
    }

    @Test
    void shouldFallBackToTenForANonPositiveSize() {
        PageReq req = new PageReq();
        req.setSize(0);

        assertEquals(10, capturedPageable(req).getPageSize());
    }

    @Test
    void shouldFallBackToTenForANegativeSize() {
        PageReq req = new PageReq();
        req.setSize(-5);

        assertEquals(10, capturedPageable(req).getPageSize());
    }

    @Test
    void shouldHonourAnExplicitSort() {
        PageReq req = new PageReq();
        req.setSortBy("documentNumber");
        req.setSort(Sort.Direction.DESC);

        assertEquals(Sort.by(Sort.Direction.DESC, "documentNumber"), capturedPageable(req).getSort());
    }

    @Test
    void shouldIgnoreABlankSortBy() {
        PageReq req = new PageReq();
        req.setSortBy("   ");
        req.setSort(Sort.Direction.DESC);

        assertEquals(Sort.by(Sort.Direction.DESC, "id"), capturedPageable(req).getSort());
    }

    // ------------------------------------------------------------------
    // doList
    // ------------------------------------------------------------------

    @Test
    void shouldLoadManyRowsById() {
        List<SalesOrder> rows = List.of(order(true, false));
        doReturn(rows).when(repository).findAllById(List.of(50L));

        assertEquals(rows, repository.doList(List.of(50L)));
    }
}
