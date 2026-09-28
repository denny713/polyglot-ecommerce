-- liquibase formatted sql
--
-- create table sales order detail
-- changeset denny.afrizal:20260911170937-create-table-sales-order-detail

CREATE TABLE IF NOT EXISTS sales_order_detail
(
    id             BIGSERIAL PRIMARY KEY,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted     BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    sales_order_id BIGINT         NOT NULL REFERENCES sales_order (id) ON DELETE CASCADE,
    product_id     BIGINT         NOT NULL REFERENCES product (id),
    quantity       INT            NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    subtotal       DECIMAL(12, 2) NOT NULL,

    CONSTRAINT chk_sales_order_detail_quantity CHECK (quantity > 0),
    CONSTRAINT chk_sales_order_detail_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_sales_order_detail_subtotal CHECK (subtotal >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sales_order_detail_sales_order_id ON sales_order_detail (sales_order_id);
CREATE INDEX IF NOT EXISTS idx_sales_order_detail_product_id ON sales_order_detail (product_id);

--rollback DROP INDEX IF EXISTS idx_sales_order_detail_sales_order_id;
--rollback DROP INDEX IF EXISTS idx_sales_order_detail_product_id;
--rollback DROP TABLE IF EXISTS sales_order_detail;