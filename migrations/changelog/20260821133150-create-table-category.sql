--liquibase formatted sql
--
-- create table category
--changeset denny.afrizal:20260821133150-create-table-category

CREATE TABLE IF NOT EXISTS category
(
    id          BIGSERIAL PRIMARY KEY,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by  UUID,
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    name        VARCHAR(50) NOT NULL,
    description TEXT
);

--rollback DROP TABLE IF EXISTS category;