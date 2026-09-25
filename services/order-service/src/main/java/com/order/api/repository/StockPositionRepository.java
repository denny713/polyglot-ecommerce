package com.order.api.repository;

import com.order.api.model.dto.response.checkout.ProductQuantity;
import com.order.api.model.entity.StockPosition;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Read access to per-product stock levels. */
@Repository
public interface StockPositionRepository extends CommonRepository<StockPosition, Long>, JpaSpecificationExecutor<StockPosition> {

    /**
     * The quantity on hand of each product, with its row locked until the transaction
     * ends, so two checkouts of the same product take turns instead of both seeing the
     * last one free. Locked in product order, so two checkouts of several products
     * cannot deadlock each other.
     */
    @Query(value = """
            SELECT product_id AS "productId", quantity AS "quantity"
              FROM stock_position
             WHERE product_id IN (:productIds)
               AND is_deleted = false
             ORDER BY product_id
               FOR UPDATE
            """, nativeQuery = true)
    List<ProductQuantity> lockQuantities(@Param("productIds") Collection<Long> productIds);
}
