-- Treningi cykliczne: sloty tygodniowe (bezterminowe) + miesięczne subskrypcje zalogowanych użytkowników.
-- Dotyczy wyłącznie kategorii TRAINING; Obozy/Szkolenia (events/enrollments) bez zmian.

CREATE TABLE training_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type_id UUID NOT NULL REFERENCES event_types(id) ON DELETE RESTRICT,
    instructor_id UUID REFERENCES instructors(id) ON DELETE SET NULL,
    day_of_week SMALLINT NOT NULL,            -- ISO: 1=poniedziałek ... 7=niedziela
    start_time TIME NOT NULL,
    end_time TIME,
    price DECIMAL(10,2),                       -- cena za pojedyncze zajęcia
    max_participants INT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_training_slots_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_training_slots_max CHECK (max_participants > 0)
);

-- Subskrypcja: zapis zalogowanego użytkownika na slot, interwał miesięcy.
-- start_month <= miesiąc; end_month NULL = na czas nieokreślony (stały bywalec).
CREATE TABLE training_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID NOT NULL REFERENCES training_slots(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_month VARCHAR(7) NOT NULL,           -- 'YYYY-MM'
    end_month VARCHAR(7),                       -- 'YYYY-MM' lub NULL (bezterminowo)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_training_enrollments_period CHECK (end_month IS NULL OR end_month >= start_month)
);

CREATE INDEX idx_training_slots_active ON training_slots(active, day_of_week, start_time);
CREATE INDEX idx_training_slots_event_type ON training_slots(event_type_id);
CREATE INDEX idx_training_slots_instructor ON training_slots(instructor_id);
CREATE INDEX idx_training_enrollments_slot ON training_enrollments(slot_id, start_month, end_month);
CREATE INDEX idx_training_enrollments_user ON training_enrollments(user_id);
