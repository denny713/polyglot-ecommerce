package com.order.api.model.entity;

import com.order.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * Quantity on hand for one product. Owned by the inventory service, which deducts it
 * once an order is paid; checkout only reads it.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "stock_position")
@AllArgsConstructor
@NoArgsConstructor
public class StockPosition extends Base {

    @OneToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Column(name = "quantity")
    private Integer quantity;
}
