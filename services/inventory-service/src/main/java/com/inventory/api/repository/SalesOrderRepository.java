package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrder;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Data access for sales orders. */
@Repository
public interface SalesOrderRepository extends CommonRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {
}
