package com.order.api.model.entity;

import com.order.api.enums.RefundReason;
import com.order.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money this service returns to a customer, always to the account of the payment it
 * came from. It is returned the moment it is recorded, so there is no status to
 * follow. A payment is refunded twice at most: its excess right away, and what it
 * applied to the order once the order is cancelled or expires.
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

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "payment_id", referencedColumnName = "id")
    private Payment payment;

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
