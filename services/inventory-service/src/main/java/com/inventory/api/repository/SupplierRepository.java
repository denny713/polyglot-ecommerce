package com.inventory.api.repository;

import com.inventory.api.model.entity.Supplier;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Read access to suppliers.
 * <p>
 * This service does not maintain suppliers; it loads one to validate a document
 * against the products that supplier offers.
 */
@Repository
public interface SupplierRepository extends CommonRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {
}
