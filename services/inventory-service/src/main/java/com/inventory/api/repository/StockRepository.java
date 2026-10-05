package com.inventory.api.repository;

import com.inventory.api.enums.StockActivity;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for the stock movement ledger. */
@Repository
public interface StockRepository extends CommonRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    /** Whether a sales order has already moved stock this way. */
    boolean existsBySalesOrderIdAndActivity(Long salesOrderId, StockActivity activity);

    /** The movements a sales order made one way, in the order they were written. */
    List<Stock> findBySalesOrderIdAndActivityOrderByIdAsc(Long salesOrderId, StockActivity activity);
}
