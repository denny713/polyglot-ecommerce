package com.inventory.api.repository;

import com.inventory.api.model.entity.PurchaseOrderDetail;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access for purchase order lines.
 * <p>
 * Lines are normally reached through their parent document; this repository exists
 * for the bulk saves and soft-deletes the service performs when a submitted
 * details list is reconciled against what is stored.
 */
@Repository
public interface PurchaseOrderDetailRepository extends CommonRepository<PurchaseOrderDetail, Long>, JpaSpecificationExecutor<PurchaseOrderDetail> {
}
