package com.order.api.model.entity;

import com.order.api.enums.SalesStatus;
import com.order.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Goods sold to a customer.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "sales_order")
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrder extends Base {

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "status")
    private SalesStatus status;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "salesOrder", fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @BatchSize(size = 50)
    private List<SalesOrderDetail> salesOrderDetails;

    @Override
    protected List<? extends Base> cascadeChildren() {
        return (salesOrderDetails == null) ? List.of() : salesOrderDetails;
    }
}
