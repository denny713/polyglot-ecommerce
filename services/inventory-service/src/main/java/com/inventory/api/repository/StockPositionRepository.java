package com.inventory.api.repository;

import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Data access for per-product stock levels. */
@Repository
public interface StockPositionRepository extends CommonRepository<StockPosition, Long>, JpaSpecificationExecutor<StockPosition> {

    /**
     * Holds the stock position rows of these products until the transaction ends, so
     * two orders moving the same product never both start from the same quantity.
     * Taken in product order, so two transactions locking overlapping products cannot
     * deadlock. Must run before the positions are read, or the read is already stale.
     */
    @Query(value = """
            SELECT id
              FROM stock_position
             WHERE product_id IN (:productIds)
               AND is_deleted = false
             ORDER BY product_id
               FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockByProductIds(@Param("productIds") Collection<Long> productIds);
}
