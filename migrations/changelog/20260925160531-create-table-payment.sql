-- liquibase formatted sql
--
-- create table payment
-- changeset denny.afrizal:20260925160531-create-table-payment

CREATE TABLE IF NOT EXISTS payment
(
    id                BIGSERIAL PRIMARY KEY,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by        UUID,
    updated_by        UUID,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    document_number   VARCHAR(30)    NOT NULL UNIQUE,
    sales_order_id    BIGINT         NOT NULL REFERENCES sales_order (id),
    reference         VARCHAR(100)   NOT NULL UNIQUE,
    method            VARCHAR(30)    NOT NULL,
    bank_name         VARCHAR(50)    NOT NULL,
    account_number    VARCHAR(50)    NOT NULL,
    account_name      VARCHAR(100)   NOT NULL,
    amount            DECIMAL(12, 2) NOT NULL,
    applied_amount    DECIMAL(12, 2) NOT NULL,
    excess_amount     DECIMAL(12, 2) NOT NULL,
    paid_at           TIMESTAMPTZ    NOT NULL,

    CONSTRAINT chk_payment_method CHECK (method IN ('Transfer', 'Virtual Account', 'E-Wallet')),
    CONSTRAINT chk_payment_amount CHECK (amount > 0 AND applied_amount >= 0 AND excess_amount >= 0),
    CONSTRAINT chk_payment_split CHECK (applied_amount + excess_amount = amount)
);

CREATE INDEX IF NOT EXISTS idx_payment_sales_order_id ON payment (sales_order_id);

--rollback DROP TABLE IF EXISTS payment;
