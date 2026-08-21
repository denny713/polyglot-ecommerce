--liquibase formatted sql
--
-- create table product

--changeset denny.afrizal:20260821133208-create-table-product

CREATE TABLE IF NOT EXISTS product
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL,
    description TEXT,
    price       DECIMAL(10, 2) NOT NULL,
    image_url   TEXT,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted  BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by  BIGINT,
    updated_by  BIGINT,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_name ON product (name);

--rollback DROP INDEX IF EXISTS idx_product_name;
--rollback DROP TABLE IF EXISTS product;
