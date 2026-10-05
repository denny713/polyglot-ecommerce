package com.order.api.repository;

import com.order.api.model.entity.Product;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/** Read access to products. */
@Repository
public interface ProductRepository extends CommonRepository<Product, Long>, JpaSpecificationExecutor<Product> {
}
