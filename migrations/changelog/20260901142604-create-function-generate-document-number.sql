--liquibase formatted sql
--
-- create function generate document number

--changeset denny.afrizal:20260901142604-create-function-generate-document-number
CREATE OR REPLACE FUNCTION generate_document_number(p_type TEXT, p_date DATE)
    RETURNS VARCHAR
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_table       TEXT;
    v_prefix      TEXT;
    v_next_prefix TEXT;
    v_sequence    BIGINT;
BEGIN
    v_table := CASE p_type
                   WHEN 'PO' THEN 'purchase_order'
                   WHEN 'PR' THEN 'purchase_return'
                   END;

    IF v_table IS NULL THEN
        RAISE EXCEPTION 'Unknown document type: %', p_type;
    END IF;

    v_prefix := p_type || TO_CHAR(p_date, 'YYYYMMDD');
    v_next_prefix := p_type || TO_CHAR(p_date + 1, 'YYYYMMDD');

    PERFORM pg_advisory_xact_lock(HASHTEXT(v_prefix));

    EXECUTE FORMAT($q$
        SELECT COALESCE(MAX(SUBSTRING(document_number FROM $1)::BIGINT), 0)
          FROM %I
         WHERE document_number >= $2
           AND document_number < $3
           AND document_number ~ $4
    $q$, v_table)
        INTO v_sequence
        USING LENGTH(v_prefix) + 1, v_prefix, v_next_prefix, '^' || v_prefix || '[0-9]+$';

    v_sequence := v_sequence + 1;

    RETURN v_prefix || LPAD(v_sequence::TEXT, GREATEST(3, LENGTH(v_sequence::TEXT)), '0');
END;
$$;

--rollback DROP FUNCTION IF EXISTS generate_document_number(TEXT, DATE);
