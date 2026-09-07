package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/**
 * Current quantity on hand for one product — the running total of that product's
 * {@link com.inventory.api.model.entity.Stock} rows.
 * <p>
 * It is stored rather than derived so reading a level costs one row instead of an
 * aggregate over the whole ledger, which means it is only correct as long as every
 * movement updates it. A product that has never moved has no row at all; the
 * services create one starting at zero on first use.
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
