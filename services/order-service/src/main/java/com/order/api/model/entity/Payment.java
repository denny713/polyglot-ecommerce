package com.order.api.model.entity;

import com.order.api.enums.PaymentMethod;
import com.order.api.model.entity.base.Base;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Money a customer sent towards a sales order. An order paid in instalments has one
 * row per instalment. Of the {@code amount} sent, {@code appliedAmount} went towards
 * the order and {@code excessAmount} is what was paid over its outstanding, which is
 * refunded.
 */
@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@Entity
@Table(name = "payment")
@AllArgsConstructor
@NoArgsConstructor
public class Payment extends Base {

    @Column(name = "document_number")
    private String documentNumber;

    @ManyToOne
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "sales_order_id", referencedColumnName = "id")
    private SalesOrder salesOrder;

    @Column(name = "reference")
    private String reference;

    @Column(name = "method")
    private PaymentMethod method;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "applied_amount")
    private BigDecimal appliedAmount;

    @Column(name = "excess_amount")
    private BigDecimal excessAmount;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
