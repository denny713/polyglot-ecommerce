package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;

/**
 * One product line of a sales order.
 * <p>
 * {@code unitPrice} is a copy of the product selling price taken when the line was
 * written, not a live reference, so the document keeps the price the customer
 * actually paid rather than today's.
 * <p>
 * There is only one quantity, where a purchase document carries two: nothing about
 * a sold line is reconciled later, so the quantity on the line is the quantity
 * deducted from stock.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "sales_order_detail")
@AllArgsConstructor
@NoArgsConstructor
public class SalesOrderDetail extends Base {

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "sales_order_id", referencedColumnName = "id")
    private SalesOrder salesOrder;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "subtotal")
    private BigDecimal subtotal;
}
