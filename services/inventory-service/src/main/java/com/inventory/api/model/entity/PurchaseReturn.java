package com.inventory.api.model.entity;

import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
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
    private String status;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "reason")
    private String reason;

    @Column(name = "note")
    private String note;

    @OneToMany(mappedBy = "purchaseReturn", fetch = FetchType.LAZY)
    private List<PurchaseReturnDetail> purchaseReturnDetails;
}
