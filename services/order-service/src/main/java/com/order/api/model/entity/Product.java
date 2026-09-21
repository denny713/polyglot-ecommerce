package com.order.api.model.entity;

import com.order.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;

/**
 * What is ordered, returned and counted. Owned by another service in the stack;
 * inventory reads it and never writes it.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "product")
@AllArgsConstructor
@NoArgsConstructor
public class Product extends Base {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "buy_price")
    private BigDecimal buyPrice;

    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "image_url")
    private String imageUrl;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "supplier_id", referencedColumnName = "id")
    private Supplier supplier;
}
