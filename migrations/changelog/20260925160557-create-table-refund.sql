-- liquibase formatted sql
--
-- create table refund
-- changeset denny.afrizal:20260925160557-create-table-refund

CREATE TABLE IF NOT EXISTS refund
(
    id               BIGSERIAL PRIMARY KEY,
    is_active        BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by       UUID,
    updated_by       UUID,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    document_number  VARCHAR(30)    NOT NULL UNIQUE,
    sales_order_id   BIGINT         NOT NULL REFERENCES sales_order (id),
    payment_id       BIGINT         NOT NULL REFERENCES payment (id),
    reason           VARCHAR(30)    NOT NULL,
    amount           DECIMAL(12, 2) NOT NULL,
    bank_name        VARCHAR(50)    NOT NULL,
    account_number   VARCHAR(50)    NOT NULL,
    account_name     VARCHAR(100)   NOT NULL,
    refunded_at      TIMESTAMPTZ    NOT NULL,

    CONSTRAINT chk_refund_amount CHECK (amount > 0),
    CONSTRAINT chk_refund_reason CHECK (reason IN ('Overpayment', 'Cancellation', 'Expired'))
);

CREATE INDEX IF NOT EXISTS idx_refund_sales_order_id ON refund (sales_order_id);
-- With no status to tell a refund already made, these keep a payment from being refunded twice:
-- its excess once, and what it applied to the order once, whether cancelled or expired.
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_payment_excess ON refund (payment_id)
    WHERE reason = 'Overpayment' AND is_deleted = false;
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_payment_applied ON refund (payment_id)
    WHERE reason <> 'Overpayment' AND is_deleted = false;

--rollback DROP TABLE IF EXISTS refund;
