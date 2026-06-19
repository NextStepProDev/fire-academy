-- Zgoda marketingowa (newsletter) — osobna od polityki prywatności i od (nieużywanego już) toggla powiadomień.
-- marketing_consent_at: timestamp udzielenia zgody (NULL = brak zgody), ten sam audytowalny wzorzec co privacy_accepted_at.
ALTER TABLE users ADD COLUMN marketing_consent_at TIMESTAMPTZ;

-- Stabilny, długożyciowy token do linku rezygnacji w mailach marketingowych (działa bez logowania).
-- Każdy user ma własny token niezależnie od zgody; DEFAULT wypełnia istniejące wiersze.
ALTER TABLE users ADD COLUMN marketing_unsubscribe_token UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_users_marketing_unsubscribe_token ON users (marketing_unsubscribe_token);
