package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrder;
import com.inventory.api.repository.common.CommonRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Data access for sales orders. */
@Repository
public interface SalesOrderRepository extends CommonRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    /**
     * Loads a sales order and holds its row until the transaction ends, so the submit
     * and the cancel of one order, which arrive on different queues, run one after the
     * other and each sees what the other wrote.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT so FROM SalesOrder so WHERE so.id = :id AND so.isDeleted = false")
    Optional<SalesOrder> lockById(@Param("id") Long id);
}
