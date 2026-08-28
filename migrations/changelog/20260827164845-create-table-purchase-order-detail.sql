--liquibase formatted sql
--
-- create table purchase order detail

--changeset denny.afrizal:20260827164845-create-table-purchase-order-detail

CREATE TABLE IF NOT EXISTS purchase_order_detail
(
    id                BIGSERIAL PRIMARY KEY,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by        BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    purchase_order_id BIGINT         NOT NULL REFERENCES purchase_order (id) ON DELETE CASCADE,
    product_id        BIGINT         NOT NULL REFERENCES product (id),
    order_quantity    INT            NOT NULL,
    real_quantity     INT            NOT NULL DEFAULT 0,
    unit_price        DECIMAL(10, 2) NOT NULL,
    subtotal          DECIMAL(12, 2) NOT NULL,
    note              TEXT,

    CONSTRAINT chk_purchase_order_detail_order_quantity CHECK (order_quantity > 0),
    CONSTRAINT chk_purchase_order_detail_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_purchase_order_detail_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_order_detail_purchase_order_id ON purchase_order_detail (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_purchase_order_detail_product_id ON purchase_order_detail (product_id);

--rollback DROP INDEX IF EXISTS idx_purchase_order_detail_purchase_order_id;
--rollback DROP INDEX IF EXISTS idx_purchase_order_detail_product_id;
--rollback DROP TABLE IF EXISTS purchase_order_detail
