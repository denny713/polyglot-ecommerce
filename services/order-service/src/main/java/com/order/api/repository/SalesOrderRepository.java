package com.order.api.repository;

import com.order.api.model.entity.SalesOrder;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface SalesOrderRepository extends CommonRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    /**
     * Expires every pending order checked out at or before {@code cutoff}, in one
     * statement, and says how many it expired. Nobody is signed in behind the job, so
     * {@code updated_by} is left empty rather than naming the last person to touch it.
     */
    @Transactional
    @Modifying
    @Query(value = """
            UPDATE sales_order
               SET status = :expired,
                   updated_by = NULL,
                   updated_at = NOW()
             WHERE status = :pending
               AND created_at <= :cutoff
               AND is_deleted = false
            """, nativeQuery = true)
    int expirePending(@Param("pending") String pending,
                      @Param("expired") String expired,
                      @Param("cutoff") LocalDateTime cutoff);
}
