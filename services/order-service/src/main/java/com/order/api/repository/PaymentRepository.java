package com.order.api.repository;

import com.order.api.model.entity.Payment;
import com.order.api.repository.common.CommonRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends CommonRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /** The payment already recorded under this reference, if any. A reference is unique across all orders. */
    Optional<Payment> findByReference(String reference);

    /** Every payment made towards a sales order, oldest first. */
    List<Payment> findBySalesOrderIdOrderByIdAsc(Long salesOrderId);
}
