-- liquibase formatted sql
--
-- create table stock
-- changeset denny.afrizal:20260911171527-create-table-stock

CREATE TABLE IF NOT EXISTS stock
(
    id                 BIGSERIAL PRIMARY KEY,
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by         UUID,
    updated_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    product_id         BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    document_number    VARCHAR(30) NOT NULL,
    document_type      VARCHAR(20) NOT NULL,
    activity           VARCHAR(10) NOT NULL,
    quantity           INT         NOT NULL DEFAULT 0,
    sales_order_id     BIGINT REFERENCES sales_order (id) ON DELETE CASCADE,
    purchase_order_id  BIGINT REFERENCES purchase_order (id) ON DELETE CASCADE,
    purchase_return_id BIGINT REFERENCES purchase_return (id) ON DELETE CASCADE,

    CONSTRAINT chk_stock_activity CHECK (activity IN ('Stock In', 'Stock Out')),
    CONSTRAINT chk_stock_document_type CHECK (document_type IN ('Sales Order', 'Purchase Order', 'Purchase Return')),
    CONSTRAINT chk_stock_quantity CHECK (quantity > 0)
);

--rollback DROP TABLE IF EXISTS stock;
