-- The shared 1-on-1 plan: the coach schedules, the client ticks off. One row = one training.
CREATE TABLE personal_trainings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    training_date DATE NOT NULL,

    -- Both nullable, and BOTH NULL is the default case: most entries are "do this on Wednesday",
    -- not "meet at 17:00". The calendar renders day columns with stacked cards, so nothing depends
    -- on a training having a clock position.
    start_time TIME,
    end_time TIME,

    title VARCHAR(150) NOT NULL,
    description VARCHAR(2000),

    -- Who authored / last touched the row. Drives the unread dots in both directions: the client
    -- must only be alerted about the coach's changes and vice versa.
    created_by_admin BOOLEAN NOT NULL,
    last_modified_by_admin BOOLEAN NOT NULL,

    completed_at TIMESTAMPTZ,
    feedback VARCHAR(2000),
    rpe SMALLINT,

    -- Optimistic locking from day one. The plan is genuinely shared — coach and client can be editing
    -- the same row at the same time — and retrofitting @Version onto a live table means a backfill plus
    -- a window where concurrent writes silently overwrite each other.
    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_personal_trainings_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    -- An end without a start is meaningless. Previously the hour grid enforced this by geometry;
    -- without a grid the database has to.
    CONSTRAINT chk_personal_trainings_times
        CHECK (end_time IS NULL OR (start_time IS NOT NULL AND end_time > start_time)),
    -- RPE only exists as part of ticking a training off, so uncompleting must clear it.
    CONSTRAINT chk_personal_trainings_rpe_needs_completion
        CHECK (completed_at IS NOT NULL OR rpe IS NULL)
);

-- Every calendar read is "this athlete, this date range".
CREATE INDEX idx_personal_trainings_athlete_date ON personal_trainings (athlete_id, training_date);

-- Unread counters scan by athlete + recency; this runs on every window focus.
CREATE INDEX idx_personal_trainings_unread ON personal_trainings (athlete_id, updated_at DESC);

-- MISSED is never stored: it is training_date < today AND completed_at IS NULL, computed on read.
-- No status column, no nightly job to drift out of sync.
