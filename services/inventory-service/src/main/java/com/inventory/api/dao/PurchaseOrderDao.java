package com.inventory.api.dao;

import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.entity.PurchaseOrder;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Turns a {@code POSearchReq} into the JPA {@code Specification} behind {@code POST /po/list}. */
public class PurchaseOrderDao extends CommonDao {

    public Specification<PurchaseOrder> buildSearchPO(POSearchReq req) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.isNotEmpty(req.getDocumentNumber())) {
                add(predicates, like(req.getDocumentNumber(), "documentNumber", root, cb));
            }

            add(predicates, equals(req.getSupplierId(), "id", root.get("supplier"), cb));
            add(predicates, equals(req.getStatus(), "status", root, cb));

            if (isPositive(req.getMinOrderGrandTotal())) {
                add(predicates, greaterThanEqualTo(req.getMinOrderGrandTotal(), "orderGrandTotal", root, cb));
            }

            if (isPositive(req.getMaxOrderGrandTotal())) {
                add(predicates, lessThanEqualTo(req.getMaxOrderGrandTotal(), "orderGrandTotal", root, cb));
            }

            if (isPositive(req.getMinRealGrandTotal())) {
                add(predicates, greaterThanEqualTo(req.getMinRealGrandTotal(), "realGrandTotal", root, cb));
            }

            if (isPositive(req.getMaxRealGrandTotal())) {
                add(predicates, lessThanEqualTo(req.getMaxRealGrandTotal(), "realGrandTotal", root, cb));
            }

            add(predicates, greaterThanEqualTo(req.getCreatedFrom(), "createdAt", root, cb));
            add(predicates, lessThanEqualTo(req.getCreatedTo(), "createdAt", root, cb));

            if (req.getIsActive() != null) {
                add(predicates, req.getIsActive()
                        ? isTrue("isActive", root, cb)
                        : isFalse("isActive", root, cb));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
