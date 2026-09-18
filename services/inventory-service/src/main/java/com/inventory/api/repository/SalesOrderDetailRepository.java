package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrderDetail;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Data access for sales order lines. */
@Repository
public interface SalesOrderDetailRepository extends CommonRepository<SalesOrderDetail, Long>, JpaSpecificationExecutor<SalesOrderDetail> {
}
