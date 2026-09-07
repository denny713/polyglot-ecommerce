package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;

/**
 * One product line of a purchase order.
 * <p>
 * {@code unitPrice} is a copy of the product price taken when the line was
 * written, not a live reference, so the document keeps the price it was agreed at.
 * The {@code order}/{@code real} pairs follow the same split as the parent: the
 * real values stay zero until approval.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "purchase_order_detail")
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderDetail extends Base {

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_order_id", referencedColumnName = "id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Column(name = "order_quantity")
    private Integer orderQuantity;

    @Column(name = "real_quantity")
    private Integer realQuantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "order_subtotal")
    private BigDecimal orderSubtotal;

    @Column(name = "real_subtotal")
    private BigDecimal realSubtotal;

    @Column(name = "note")
    private String note;
}
