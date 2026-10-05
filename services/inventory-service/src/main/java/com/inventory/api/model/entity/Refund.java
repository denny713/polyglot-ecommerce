package com.inventory.api.model.entity;

import com.inventory.api.enums.RefundReason;
import com.inventory.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money the order service returned to a customer for a sales order. This service
 * only reads it, to record the stock a cancelled order brings back under the refund
 * that took it back.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "refund")
@AllArgsConstructor
@NoArgsConstructor
public class Refund extends Base {

    @Column(name = "document_number")
    private String documentNumber;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "sales_order_id", referencedColumnName = "id")
    private SalesOrder salesOrder;

    @Column(name = "reason")
    private RefundReason reason;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
