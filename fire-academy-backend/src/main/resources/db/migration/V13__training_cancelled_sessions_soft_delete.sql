-- Odwoływanie pojedynczych zajęć (np. choroba trenera) + miękkie usuwanie slotów.
-- Dotyczy wyłącznie kategorii TRAINING.

-- Miękkie usunięcie slotu: po usunięciu admin nadal widzi zapisanych (dane kontaktowe),
-- żeby móc się z nimi skontaktować. NULL = slot aktywny w katalogu.
ALTER TABLE training_slots ADD COLUMN deleted_at TIMESTAMPTZ;

-- Odwołane pojedyncze zajęcia (konkretna data wystąpienia cyklicznego slotu).
CREATE TABLE training_cancelled_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID NOT NULL REFERENCES training_slots(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_training_cancelled_session UNIQUE (slot_id, session_date)
);

CREATE INDEX idx_training_cancelled_sessions_slot ON training_cancelled_sessions(slot_id, session_date);
