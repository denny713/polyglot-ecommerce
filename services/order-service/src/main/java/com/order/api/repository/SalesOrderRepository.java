package com.order.api.repository;

import com.order.api.model.entity.SalesOrder;
import com.order.api.repository.common.CommonRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends CommonRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    /**
     * Loads a sales order and holds its row until the transaction ends, so a payment,
     * a cancel and an expiry of the same order run one after the other and each sees
     * the paid and outstanding the previous one left.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT so FROM SalesOrder so WHERE so.id = :id AND so.isDeleted = false")
    Optional<SalesOrder> lockById(@Param("id") Long id);

    /**
     * Expires every pending order checked out at or before {@code cutoff} that nothing
     * has been paid towards yet, in one statement, and returns the ids it expired so
     * each customer can be told. An order paid in part owes the customer a refund, so
     * it goes through {@link #findExpirablePaid} instead. Nobody is signed in behind
     * the job, so {@code updated_by} is left empty rather than naming the last person
     * to touch it.
     */
    @Transactional
    @Query(value = """
            UPDATE sales_order
               SET status = :expired,
                   updated_by = NULL,
                   updated_at = NOW()
             WHERE status = :pending
               AND created_at <= :cutoff
               AND paid = 0
               AND is_deleted = false
            RETURNING id
            """, nativeQuery = true)
    List<Long> expirePending(@Param("pending") String pending,
                             @Param("expired") String expired,
                             @Param("cutoff") LocalDateTime cutoff);

    /**
     * The ids of the pending orders checked out at or before {@code cutoff} that were
     * paid in part, each of which must be expired together with its refunds.
     */
    @Query(value = """
            SELECT id
              FROM sales_order
             WHERE status = :pending
               AND created_at <= :cutoff
               AND paid > 0
               AND is_deleted = false
             ORDER BY id
            """, nativeQuery = true)
    List<Long> findExpirablePaid(@Param("pending") String pending,
                                 @Param("cutoff") LocalDateTime cutoff);
}
