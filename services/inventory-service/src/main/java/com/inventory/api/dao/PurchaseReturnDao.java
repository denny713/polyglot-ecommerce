package com.inventory.api.dao;

import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.entity.PurchaseReturn;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@code PRSearchReq} into the JPA {@code Specification} behind
 * {@code POST /pr/list}.
 * <p>
 * Same contract as {@code PurchaseOrderDao} over a single {@code grandTotal},
 * since a return has no ordered-versus-received distinction to filter on.
 */
public class PurchaseReturnDao extends CommonDao {

    public Specification<PurchaseReturn> buildSearchPR(PRSearchReq req) {
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
