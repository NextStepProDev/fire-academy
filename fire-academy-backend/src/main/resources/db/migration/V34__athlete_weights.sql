-- Morning body weight. In a club with weight classes this is competitive infrastructure, not a
-- vanity metric: it drives cut planning and it is the only honest ground truth about energy balance.
CREATE TABLE athlete_weights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    measured_on DATE NOT NULL,
    weight_kg NUMERIC(5, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- One reading per day: weighing twice is a correction, not a second data point. The upsert in
    -- the service relies on this.
    CONSTRAINT uq_athlete_weights_day UNIQUE (athlete_id, measured_on),
    -- Wide enough for anyone, narrow enough to catch a slipped decimal point (7.42 / 742).
    CONSTRAINT chk_athlete_weights_range CHECK (weight_kg BETWEEN 20 AND 300)
);

CREATE INDEX idx_athlete_weights_athlete_date ON athlete_weights (athlete_id, measured_on DESC);

-- No calories here on purpose. Calories burned can only ever be estimated (±20-30% even from a
-- watch), and a bilans built on a guess produces a precise-looking number that is simply wrong.
-- Weight is measured. Given intake logged alongside it, real maintenance can be DERIVED from the
-- person's own data later — which is the only version of this worth building.
