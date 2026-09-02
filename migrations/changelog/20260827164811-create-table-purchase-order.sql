--liquibase formatted sql
--
-- create table purchase order
--changeset denny.afrizal:20260827164811-create-table-purchase-order
CREATE TABLE IF NOT EXISTS purchase_order
(
    id                BIGSERIAL PRIMARY KEY,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by        BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    document_number   VARCHAR(30)    NOT NULL UNIQUE,
    supplier_id       BIGINT         NOT NULL REFERENCES supplier (id),
    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    order_grand_total DECIMAL(12, 2) NOT NULL,
    real_grand_total  DECIMAL(12, 2) NOT NULL,
    note              TEXT,
    CONSTRAINT chk_purchase_order_grand_total CHECK (order_grand_total >= 0),
    CONSTRAINT chk_purchase_order_status CHECK (status IN ('DRAFT', 'APPROVED', 'CANCELLED'))
);

--rollback DROP TABLE IF EXISTS purchase_order;