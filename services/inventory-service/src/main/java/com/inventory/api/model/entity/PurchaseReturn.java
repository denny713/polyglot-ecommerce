package com.inventory.api.model.entity;

import com.inventory.api.enums.DocStatus;
import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "purchase_return")
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseReturn extends Base {

    @Column(name = "document_number")
    private String documentNumber;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "supplier_id", referencedColumnName = "id")
    private Supplier supplier;

    @Column(name = "status")
    private DocStatus status;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "reason")
    private String reason;

    @Column(name = "note")
    private String note;

    @OneToMany(mappedBy = "purchaseReturn", fetch = FetchType.LAZY)
    @SQLRestriction("is_deleted = false")
    @BatchSize(size = 50)
    private List<PurchaseReturnDetail> purchaseReturnDetails;

    @Override
    protected List<? extends Base> cascadeChildren() {
        return (purchaseReturnDetails == null) ? List.of() : purchaseReturnDetails;
    }
}
