package com.inventory.api.repository;

import com.inventory.api.model.entity.StockPosition;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Data access for per-product stock levels. */
@Repository
public interface StockPositionRepository extends CommonRepository<StockPosition, Long>, JpaSpecificationExecutor<StockPosition> {
}
