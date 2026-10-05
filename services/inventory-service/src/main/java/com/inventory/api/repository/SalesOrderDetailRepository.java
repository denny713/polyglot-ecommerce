package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrderDetail;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for sales order lines. */
@Repository
public interface SalesOrderDetailRepository extends CommonRepository<SalesOrderDetail, Long>, JpaSpecificationExecutor<SalesOrderDetail> {

    /**
     * The products on a sales order, read as bare ids so no product, and with it no
     * stock position, is loaded before those positions are locked.
     */
    @Query(value = """
            SELECT DISTINCT product_id
              FROM sales_order_detail
             WHERE sales_order_id = :salesOrderId
               AND is_deleted = false
            """, nativeQuery = true)
    List<Long> findProductIds(@Param("salesOrderId") Long salesOrderId);
}
