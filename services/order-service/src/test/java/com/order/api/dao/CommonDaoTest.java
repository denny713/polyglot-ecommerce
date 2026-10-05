package com.order.api.dao;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Tests the predicate helpers directly, including the ones the two search DAOs do not currently use. */
class CommonDaoTest extends CommonDao {

    private CriteriaBuilder cb;
    private Path<Object> path;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        path = mock(Path.class, RETURNS_DEEP_STUBS);
    }

    // ------------------------------------------------------------------
    // the null-means-skip convention
    // ------------------------------------------------------------------

    @Test
    void shouldProduceNoPredicateForANullValue() {
        assertNull(like(null, "name", path, cb));
        assertNull(equals(null, "id", path, cb));
        assertNull(greaterThan(null, "createdAt", path, cb));
        assertNull(lessThan(null, "createdAt", path, cb));
        assertNull(greaterThanEqualTo(null, "createdAt", path, cb));
        assertNull(lessThanEqualTo(null, "createdAt", path, cb));

        // Nothing may be asked of the builder for a filter that is not set.
        verifyNoInteractions(cb);
    }

    @Test
    void shouldLowerBothSidesOfALike() {
        assertNotNull(like("Kopi", "name", path, cb));

        verify(cb).lower(any());
        verify(cb).like(any(), org.mockito.ArgumentMatchers.eq("%kopi%"));
    }

    @Test
    void shouldBuildAnEqualForAPresentValue() {
        assertNotNull(equals(42L, "id", path, cb));

        verify(cb).equal(any(), org.mockito.ArgumentMatchers.eq(42L));
    }

    // ------------------------------------------------------------------
    // the strict comparisons, kept for callers that need them
    // ------------------------------------------------------------------

    @Test
    void shouldBuildStrictComparisons() {
        LocalDateTime when = LocalDateTime.of(2026, 9, 7, 12, 0);

        assertNotNull(greaterThan(when, "createdAt", path, cb));
        assertNotNull(lessThan(when, "createdAt", path, cb));

        verify(cb).greaterThan(any(), org.mockito.ArgumentMatchers.eq(when));
        verify(cb).lessThan(any(), org.mockito.ArgumentMatchers.eq(when));
    }

    @Test
    void shouldBuildInclusiveComparisons() {
        assertNotNull(greaterThanEqualTo(5, "quantity", path, cb));
        assertNotNull(lessThanEqualTo(9, "quantity", path, cb));

        verify(cb).greaterThanOrEqualTo(any(), org.mockito.ArgumentMatchers.eq(5));
        verify(cb).lessThanOrEqualTo(any(), org.mockito.ArgumentMatchers.eq(9));
    }

    @Test
    void shouldBuildBooleanChecksWithoutAValue() {
        assertNotNull(isTrue("isActive", path, cb));
        assertNotNull(isFalse("isActive", path, cb));

        verify(cb).isTrue(any());
        verify(cb).isFalse(any());
    }

    // ------------------------------------------------------------------
    // isPositive: what makes a zero bound mean "not set"
    // ------------------------------------------------------------------

    @Test
    void shouldTreatOnlyAPositiveAmountAsSet() {
        assertTrue(isPositive(new BigDecimal("0.01")));
        assertTrue(isPositive(new BigDecimal("1000")));

        assertFalse(isPositive(null));
        assertFalse(isPositive(BigDecimal.ZERO));
        assertFalse(isPositive(new BigDecimal("0.00")));
        assertFalse(isPositive(new BigDecimal("-1")));
    }

    // ------------------------------------------------------------------
    // add and or, the two collectors
    // ------------------------------------------------------------------

    @Test
    void shouldDropANullPredicateOnAdd() {
        List<Predicate> predicates = new ArrayList<>();

        add(predicates, null);
        assertEquals(0, predicates.size());

        add(predicates, mock(Predicate.class));
        assertEquals(1, predicates.size());
    }

    @Test
    void shouldSkipNullsWhenBuildingADisjunction() {
        Predicate first = mock(Predicate.class);
        Predicate second = mock(Predicate.class);

        or(cb, first, null, second, null);

        ArgumentCaptor<Predicate[]> passed = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).or(passed.capture());

        assertEquals(2, passed.getValue().length);
        assertTrue(List.of(passed.getValue()).containsAll(List.of(first, second)));
    }

    @Test
    void shouldBuildAnEmptyDisjunctionWhenEverythingIsNull() {
        or(cb, null, null);

        ArgumentCaptor<Predicate[]> passed = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).or(passed.capture());
        assertEquals(0, passed.getValue().length);
    }
}
