--liquibase formatted sql
--
-- create table purchase return detail

--changeset denny.afrizal:20260827164912-create-table-purchase-return-detail

CREATE TABLE IF NOT EXISTS purchase_return_detail
(
    id                 BIGSERIAL PRIMARY KEY,
    is_active          BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by         BIGINT,
    updated_by         BIGINT,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    purchase_return_id BIGINT         NOT NULL REFERENCES purchase_return (id) ON DELETE CASCADE,
    product_id         BIGINT         NOT NULL REFERENCES product (id),
    quantity           INT            NOT NULL,
    unit_price         DECIMAL(10, 2) NOT NULL,
    subtotal           DECIMAL(12, 2) NOT NULL,
    reason             VARCHAR(255),
    note               TEXT,

    CONSTRAINT chk_purchase_return_detail_quantity CHECK (quantity > 0),
    CONSTRAINT chk_purchase_return_detail_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_purchase_return_detail_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_purchase_return_detail_purchase_return_id ON purchase_return_detail (purchase_return_id);
CREATE INDEX IF NOT EXISTS idx_purchase_return_detail_product_id ON purchase_return_detail (product_id);

--rollback DROP INDEX IF EXISTS idx_purchase_return_detail_purchase_return_id;
--rollback DROP INDEX IF EXISTS idx_purchase_return_detail_product_id;
--rollback DROP TABLE IF EXISTS purchase_return_detail
