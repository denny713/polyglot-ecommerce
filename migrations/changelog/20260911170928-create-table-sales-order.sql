-- liquibase formatted sql
--
-- create table sales order
-- changeset denny.afrizal:20260911170928-create-table-sales-order

CREATE TABLE IF NOT EXISTS table_name
(
    id         BIGSERIAL PRIMARY KEY,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Default audit and soft-delete fields.
    -- Remove the unused fields when they are not required by this table.

    -- TODO: Describe the purpose of this table and the changes introduced by this changeset.
)

--rollback DROP TABLE IF EXISTS table_name
