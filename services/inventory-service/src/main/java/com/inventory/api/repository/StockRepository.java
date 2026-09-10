package com.inventory.api.repository;

import com.inventory.api.model.entity.Stock;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Data access for the stock movement ledger.
 * <p>
 * Write-only in practice: the services append movements when a document is
 * approved and never revise them.
 */
@Repository
public interface StockRepository extends CommonRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {
}
