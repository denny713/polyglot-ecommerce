package com.inventory.api.dao;

import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.entity.PurchaseOrder;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderDao extends CommonDao {

    public Specification<PurchaseOrder> buildSearchPO(POSearchReq req) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.isNotEmpty(req.getDocumentNumber())) {
                add(predicates, like(req.getDocumentNumber(), "documentNumber", root, cb));
            }

            add(predicates, equals(req.getSupplierId(), "id", root.get("supplier"), cb));
            add(predicates, equals(req.getStatus(), "status", root, cb));

            if (isPositive(req.getMinGrandTotal())) {
                add(predicates, greaterThanEqualTo(req.getMinGrandTotal(), "grandTotal", root, cb));
            }

            if (isPositive(req.getMaxGrandTotal())) {
                add(predicates, lessThanEqualTo(req.getMaxGrandTotal(), "grandTotal", root, cb));
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
