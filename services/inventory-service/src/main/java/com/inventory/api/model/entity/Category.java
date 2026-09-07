package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * Product grouping, owned by another service in the stack.
 * <p>
 * Inventory only reads it — it is mapped here so a product can be returned with
 * its category, not so this service can maintain categories.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "category")
@AllArgsConstructor
@NoArgsConstructor
public class Category extends Base {

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    private List<Product> products;
}
