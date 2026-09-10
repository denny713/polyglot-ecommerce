package com.inventory.api.repository;

import com.inventory.api.model.entity.PurchaseReturn;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access for purchase returns.
 * <p>
 * Search comes from {@code CommonRepository#doSearch} combined with the
 * {@code Specification} that {@code PurchaseReturnDao} builds, which is why no
 * query methods are declared here.
 */
@Repository
public interface PurchaseReturnRepository extends CommonRepository<PurchaseReturn, Long>, JpaSpecificationExecutor<PurchaseReturn> {
}
