--liquibase formatted sql
--
-- create table supplier
--changeset denny.afrizal:20260821133232-create-table-supplier
CREATE TABLE IF NOT EXISTS supplier
(
    id             BIGSERIAL PRIMARY KEY,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by     UUID,
    updated_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name           VARCHAR(50) NOT NULL,
    phone          VARCHAR(50),
    email          VARCHAR(50),
    contact_person VARCHAR(50),
    address        TEXT,
    province       VARCHAR(100),
    city           VARCHAR(100),
    district       VARCHAR(100),
    subdistrict    VARCHAR(100),
    postal_code    VARCHAR(7),
    note           TEXT
);

--rollback DROP TABLE IF EXISTS supplier;