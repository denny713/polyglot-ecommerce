package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrderDetail;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access for sales order lines.
 * <p>
 * Lines are read through their parent document, which is loaded whole by id, so
 * nothing is declared here. The repository exists as the seam for writing against
 * the lines directly, the way the purchase document repositories are used when a
 * submitted details list is reconciled against what is stored.
 */
@Repository
public interface SalesOrderDetailRepository extends CommonRepository<SalesOrderDetail, Long>, JpaSpecificationExecutor<SalesOrderDetail> {
}
