-- Zapis na wydarzenie wymaga teraz konta użytkownika (RODO: dane osobowe = źródło prawdy w users).
-- Powiązanie zapisu z kontem przez klucz obcy. Kolumny PII w enrollments zostają jako snapshot
-- z chwili zapisu (czytelny roster nawet po usunięciu/anonimizacji konta).
--
-- user_id jest nullable WYŁĄCZNIE po to, by usunięcie konta (ON DELETE SET NULL) lub anonimizacja
-- mogły je wyzerować — w normalnym obrocie każdy zapis ma konto.
ALTER TABLE enrollments
    ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_enrollments_user ON enrollments(user_id);

-- Jedno konto = maksymalnie jeden zapis na dane wydarzenie. Indeks częściowy nie obejmuje
-- wierszy anonimizowanych/odłączonych (user_id IS NULL).
CREATE UNIQUE INDEX uq_enrollments_user_event
    ON enrollments(user_id, event_id) WHERE user_id IS NOT NULL;

-- Audytowalna zgoda na politykę prywatności (RODO) — moment akceptacji przy rejestracji.
ALTER TABLE users ADD COLUMN privacy_accepted_at TIMESTAMPTZ;
