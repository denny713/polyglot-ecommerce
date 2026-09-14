-- liquibase formatted sql
--
-- create table stock position
-- changeset denny.afrizal:20260911171536-create-table-stock-position

CREATE TABLE IF NOT EXISTS stock_position
(
    id         BIGSERIAL PRIMARY KEY,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    product_id BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL DEFAULT 0
);

--rollback DROP TABLE IF EXISTS stock_position;
