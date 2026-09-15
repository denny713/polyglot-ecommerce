package com.inventory.api.repository;

import com.inventory.api.model.entity.SalesOrder;
import com.inventory.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Data access for sales orders.
 * <p>
 * Only {@code CommonRepository#doGet} is used: a submit message arrives with an id,
 * and searching or listing sales orders belongs to the order service that owns
 * them. That is why no query methods are declared here and no {@code Dao} sits
 * beside this repository, unlike the purchase documents.
 */
@Repository
public interface SalesOrderRepository extends CommonRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {
}
