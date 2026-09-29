package com.inventory.api.repository;

import com.inventory.api.model.entity.Refund;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/** Read access to the refunds the order service records. */
@Repository
public interface RefundRepository extends CommonRepository<Refund, Long> {

    /** The given refunds of a sales order, in the order they were recorded; ids of another order are left out. */
    List<Refund> findBySalesOrderIdAndIdInOrderByIdAsc(Long salesOrderId, Collection<Long> ids);
}
