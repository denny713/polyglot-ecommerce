--liquibase formatted sql
--
-- create table stock

--changeset denny.afrizal:20260821133251-create-table-stock

CREATE TABLE IF NOT EXISTS stock
(
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    document_number VARCHAR(30) NOT NULL,
    document_type   VARCHAR(10) NOT NULL,
    activity        VARCHAR(5)  NOT NULL,
    quantity        INT         NOT NULL DEFAULT 0,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

--rollback DROP TABLE IF EXISTS stock;
