package com.inventory.api.repository;

import com.inventory.api.model.entity.PurchaseOrder;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Data access for purchase orders. */
@Repository
public interface PurchaseOrderRepository extends CommonRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {
}
