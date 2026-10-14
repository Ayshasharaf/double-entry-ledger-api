ALTER TABLE journal_entries DROP CONSTRAINT journal_entries_status_check;

ALTER TABLE journal_entries
    ADD CONSTRAINT journal_entries_status_check
        CHECK (status IN ('pending', 'posted', 'voided', 'reversed'));

ALTER TABLE journal_entries ALTER COLUMN posted_at DROP NOT NULL;

CREATE OR REPLACE FUNCTION prevent_journal_entry_update_except_status()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.id IS DISTINCT FROM NEW.id
        OR OLD.idempotency_key IS DISTINCT FROM NEW.idempotency_key
        OR OLD.description IS DISTINCT FROM NEW.description
        OR OLD.reverses_journal_entry_id IS DISTINCT FROM NEW.reverses_journal_entry_id
        OR OLD.metadata IS DISTINCT FROM NEW.metadata
        OR OLD.created_at IS DISTINCT FROM NEW.created_at
    THEN
        RAISE EXCEPTION 'journal_entries are immutable except for status updates';
    END IF;
    IF OLD.status IS DISTINCT FROM NEW.status
        AND NOT (
            (OLD.status = 'pending' AND NEW.status IN ('posted', 'voided'))
            OR (OLD.status = 'posted' AND NEW.status = 'reversed')
        )
    THEN
        RAISE EXCEPTION 'illegal journal status transition: % -> %', OLD.status, NEW.status;
    END IF;
    IF OLD.posted_at IS DISTINCT FROM NEW.posted_at
        AND NOT (OLD.status = 'pending' AND NEW.status = 'posted' AND OLD.posted_at IS NULL)
    THEN
        RAISE EXCEPTION 'posted_at can be set only when a pending journal is posted';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

