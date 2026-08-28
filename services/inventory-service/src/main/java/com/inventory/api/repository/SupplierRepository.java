package com.inventory.api.repository;

import com.inventory.api.model.entity.Supplier;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends CommonRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {
}
