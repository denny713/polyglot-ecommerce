package com.inventory.api.model.entity;

import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "stock")
@AllArgsConstructor
@NoArgsConstructor
public class Stock extends Base {

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "product_id", referencedColumnName = "id")
    private Product product;

    @Column(name = "document_number")
    private String documentNumber;

    // STRING, or JPA defaults to ORDINAL and writes 0/1 into a VARCHAR guarded
    // by CHECK (document_type IN ('PO', 'PR')) / CHECK (activity IN ('IN', 'OUT')).
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity")
    private StockActivity activity;

    @Column(name = "quantity")
    private Integer quantity;

    // ManyToOne: one document produces one movement per line, and neither
    // foreign key is unique in the schema.
    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_order_id", referencedColumnName = "id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_return_id", referencedColumnName = "id")
    private PurchaseReturn purchaseReturn;
}
