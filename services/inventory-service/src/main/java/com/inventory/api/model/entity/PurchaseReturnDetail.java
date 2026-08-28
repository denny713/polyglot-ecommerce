package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "purchase_return_detail")
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseReturnDetail extends Base {

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_return_id", referencedColumnName = "id")
    private PurchaseReturn purchaseReturn;

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

    @Column(name = "reason")
    private String reason;

    @Column(name = "note")
    private String note;
}
