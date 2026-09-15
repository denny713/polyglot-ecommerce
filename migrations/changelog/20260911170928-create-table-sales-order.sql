-- liquibase formatted sql
--
-- create table sales order
-- changeset denny.afrizal:20260911170928-create-table-sales-order

CREATE TABLE IF NOT EXISTS sales_order
(
    id              BIGSERIAL PRIMARY KEY,
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by      UUID,
    updated_by      UUID,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    document_number VARCHAR(30)    NOT NULL UNIQUE,
    status          VARCHAR(20)    NOT NULL DEFAULT 'Pending',
    grand_total     DECIMAL(12, 2) NOT NULL,

    CONSTRAINT chk_sales_order_grand_total CHECK (grand_total >= 0),
    CONSTRAINT chk_sales_order_status CHECK (status IN ('Pending', 'Expired', 'Paid', 'Cancelled', 'Completed'))
)

--rollback DROP TABLE IF EXISTS sales_order;
