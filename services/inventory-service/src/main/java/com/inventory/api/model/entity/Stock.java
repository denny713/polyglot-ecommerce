package com.inventory.api.model.entity;

import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

/** One stock movement — the append-only history behind {@link com.inventory.api.model.entity.StockPosition}. */
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

    @Column(name = "document_type")
    private DocType documentType;

    @Column(name = "activity")
    private StockActivity activity;

    @Column(name = "quantity")
    private Integer quantity;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "sales_order_id", referencedColumnName = "id")
    private SalesOrder salesOrder;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_order_id", referencedColumnName = "id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "purchase_return_id", referencedColumnName = "id")
    private PurchaseReturn purchaseReturn;
}
