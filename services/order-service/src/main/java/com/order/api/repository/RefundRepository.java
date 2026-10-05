package com.order.api.repository;

import com.order.api.model.entity.Refund;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends CommonRepository<Refund, Long>, JpaSpecificationExecutor<Refund> {
}
