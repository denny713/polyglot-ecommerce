package com.inventory.api.model.entity;

import com.inventory.api.enums.SalesStatus;
import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Goods sold to a customer.
 * <p>
 * Unlike a purchase order, this service never creates one. The document is written
 * when the order is placed upstream, and inventory reads it on the submit message
 * to learn what left the warehouse, which is also why there is a single
 * {@code grandTotal}: a sale has no ordered-versus-received split to keep, what was
 * sold is what goes out.
 * <p>
 * {@code status} is the order service's view of the document and is not advanced
 * from here — see {@link com.inventory.api.enums.SalesStatus}.
 * <p>
 * Overriding {@code cascadeChildren} is what makes delete, activate and deactivate
 * reach the detail lines.
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
