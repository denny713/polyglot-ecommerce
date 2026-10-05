package com.order.api.repository;

import com.order.api.model.dto.response.checkout.ProductQuantity;
import com.order.api.model.entity.SalesOrderDetail;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface SalesOrderDetailRepository extends CommonRepository<SalesOrderDetail, Long>, JpaSpecificationExecutor<SalesOrderDetail> {

    /**
     * How much of each product is promised to orders the inventory service has not
     * deducted yet: pending orders still inside their payment window, and paid orders
     * whose stock movement has not been written. Anything else either holds no stock
     * any more or has already left the stock position.
     */
    @Query(value = """
            SELECT sod.product_id AS "productId", CAST(SUM(sod.quantity) AS INTEGER) AS "quantity"
              FROM sales_order_detail sod
              JOIN sales_order so ON so.id = sod.sales_order_id
             WHERE sod.product_id IN (:productIds)
               AND sod.is_deleted = false
               AND so.is_deleted = false
               AND ((so.status = :pending AND so.created_at > :pendingSince)
                    OR (so.status = :paid AND NOT EXISTS (
                            SELECT 1 FROM stock s
                             WHERE s.sales_order_id = so.id
                               AND s.is_deleted = false)))
             GROUP BY sod.product_id
            """, nativeQuery = true)
    List<ProductQuantity> sumReserved(@Param("productIds") Collection<Long> productIds,
                                      @Param("pending") String pending,
                                      @Param("paid") String paid,
                                      @Param("pendingSince") LocalDateTime pendingSince);
}
