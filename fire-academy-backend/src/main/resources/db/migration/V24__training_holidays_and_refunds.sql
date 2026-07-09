-- Global club closure days (days off) + a refund ledger for sessions cancelled in an already-paid month.
-- Applies to the TRAINING category only.

-- Whole-club closure on a given date (e.g. a public holiday). Reduces the billable session count
-- for every slot whose weekday matches the date. One closure per date (single club).
CREATE TABLE training_holidays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL,
    label VARCHAR(120),                        -- optional name shown to clients, e.g. 'Boże Ciało'
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_training_holidays_date UNIQUE (holiday_date)
);

CREATE INDEX idx_training_holidays_date ON training_holidays(holiday_date);

-- One refund obligation = one already-paid session that was later cancelled (a day off or a single-session
-- cancellation). Created when a session is cancelled in a month the subscriber has already paid for; removed
-- again if the cancellation is undone before it is settled. settled_at = the organizer resolved it, either by
-- handing the money back or by counting it toward this/next month (settlement_type). type: 'HOLIDAY' (day off)
-- or 'SESSION' (single cancelled session).
CREATE TABLE training_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES training_enrollments(id) ON DELETE CASCADE,
    session_date DATE NOT NULL,
    year_month VARCHAR(7) NOT NULL,            -- 'YYYY-MM' of the cancelled session
    amount DECIMAL(10,2) NOT NULL,             -- single-session price snapshot at cancellation time
    type VARCHAR(20) NOT NULL,                 -- HOLIDAY | SESSION
    label VARCHAR(120),                        -- holiday label, when applicable
    settled_at TIMESTAMPTZ,                    -- NULL = still owed
    settlement_type VARCHAR(20),               -- REFUNDED (money back) | CREDITED (counted toward a month)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_training_refunds UNIQUE (enrollment_id, session_date)
);

CREATE INDEX idx_training_refunds_enrollment ON training_refunds(enrollment_id);
CREATE INDEX idx_training_refunds_pending ON training_refunds(settled_at) WHERE settled_at IS NULL;
