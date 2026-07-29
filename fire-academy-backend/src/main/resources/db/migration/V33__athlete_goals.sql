-- Goals the coach sets for a client, on three horizons.
CREATE TABLE athlete_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    horizon VARCHAR(10) NOT NULL,
    content VARCHAR(500) NOT NULL,
    target_date DATE,
    -- DATE, not a timestamp: achievement is back-dated ("you actually hit this last Tuesday"), and
    -- a time of day would be invented precision.
    achieved_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_athlete_goals_horizon CHECK (horizon IN ('SHORT', 'MEDIUM', 'LONG'))
);

-- One ACTIVE goal per horizon, but any number of achieved ones — that pile is the trophy case.
-- A plain unique constraint would cap the client at three goals for life.
CREATE UNIQUE INDEX uq_athlete_goals_active
    ON athlete_goals (athlete_id, horizon) WHERE achieved_at IS NULL;
CREATE INDEX idx_athlete_goals_athlete ON athlete_goals (athlete_id, achieved_at);
