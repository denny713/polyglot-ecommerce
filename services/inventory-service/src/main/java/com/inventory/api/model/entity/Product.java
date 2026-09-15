package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * What is ordered, returned and counted. Owned by another service in the stack;
 * inventory reads it and never writes it.
 * <p>
 * {@code price} is read at submit time and copied onto the document line, so a
 * later price change does not rewrite documents that were already created.
 * <p>
 * The {@code stockPosition} association joins on this row's own id against
 * {@code product_id}, which is what makes it a one-to-one from the product side,
 * and {@code NotFoundAction.IGNORE} leaves it null for a product that has never
 * moved rather than failing the read.
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

    @OneToMany(mappedBy = "product")
    @SQLRestriction("is_deleted = false")
    private List<Stock> stocks;

    @OneToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "id", referencedColumnName = "product_id")
    private StockPosition stockPosition;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "category_id", referencedColumnName = "id")
    private Category category;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "supplier_id", referencedColumnName = "id")
    private Supplier supplier;
}
