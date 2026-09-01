package com.inventory.api.dao;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CommonDao {

    protected static <T> Predicate like(String value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.like(cb.lower(path.get(fieldName)), "%" + value.toLowerCase() + "%") : null;
    }

    protected static <T, V> Predicate equals(V value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.equal(path.get(fieldName), value) : null;
    }

    protected static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    protected static Predicate or(CriteriaBuilder cb, Predicate... predicates) {
        List<Predicate> nonNullPredicates = new ArrayList<>();
        for (Predicate predicate : predicates) {
            if (predicate != null) {
                nonNullPredicates.add(predicate);
            }
        }

        return cb.or(nonNullPredicates.toArray(new Predicate[0]));
    }

    protected static <T> Predicate isTrue(String fieldName, Path<T> path, CriteriaBuilder cb) {
        return cb.isTrue(path.get(fieldName));
    }

    protected static <T> Predicate isFalse(String fieldName, Path<T> path, CriteriaBuilder cb) {
        return cb.isFalse(path.get(fieldName));
    }

    protected static <T, V extends Comparable<? super V>> Predicate greaterThan(
            V value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.greaterThan(path.get(fieldName), value) : null;
    }

    protected static <T, V extends Comparable<? super V>> Predicate lessThan(
            V value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.lessThan(path.get(fieldName), value) : null;
    }

    protected static <T, V extends Comparable<? super V>> Predicate greaterThanEqualTo(
            V value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.greaterThanOrEqualTo(path.get(fieldName), value) : null;
    }

    protected static <T, V extends Comparable<? super V>> Predicate lessThanEqualTo(
            V value, String fieldName, Path<T> path, CriteriaBuilder cb) {
        return value != null ? cb.lessThanOrEqualTo(path.get(fieldName), value) : null;
    }

    protected static void add(List<Predicate> predicates, Predicate predicate) {
        if (predicate != null) {
            predicates.add(predicate);
        }
    }
}
