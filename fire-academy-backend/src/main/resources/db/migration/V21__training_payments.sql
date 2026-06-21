-- Status płatności za trening: jeden rekord = opłacony miesiąc danej subskrypcji.
-- Obecność rekordu (enrollment_id, year_month) = opłacone; brak = nieopłacone.
-- Płatność realizowana offline (u organizatora), admin/trener oznacza ją ręcznie.

CREATE TABLE training_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES training_enrollments(id) ON DELETE CASCADE,
    year_month VARCHAR(7) NOT NULL,            -- 'YYYY-MM'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_training_payments UNIQUE (enrollment_id, year_month)
);

CREATE INDEX idx_training_payments_enrollment ON training_payments(enrollment_id);
