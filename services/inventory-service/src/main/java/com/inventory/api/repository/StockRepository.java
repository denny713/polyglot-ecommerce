package com.inventory.api.repository;

import com.inventory.api.model.entity.Stock;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Data access for the stock movement ledger. */
@Repository
public interface StockRepository extends CommonRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {
}
