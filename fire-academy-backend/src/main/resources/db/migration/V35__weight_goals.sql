-- Weight goals live alongside the general ones: same three horizons, same trophy case, but they
-- close themselves. A goal like "reach 73 kg" is objectively checkable, unlike "10 pull-ups" which
-- needs a human to confirm.
ALTER TABLE athlete_goals
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN target_weight_kg NUMERIC(5, 2),
    -- Snapshot of the trend when the goal was set. Two jobs: it tells us which DIRECTION counts as
    -- progress (down to 73 vs up to 80 — the goal cannot know without it), and it is the baseline
    -- the progress bar measures against.
    ADD COLUMN start_weight_kg NUMERIC(5, 2),
    -- A human's decision is final; a machine's is correctable. A typo inside the valid range
    -- (65 for 75) drags the trend down and would otherwise close a goal irreversibly.
    ADD COLUMN achieved_automatically BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_kind CHECK (kind IN ('GENERAL', 'WEIGHT')),
    ADD CONSTRAINT chk_athlete_goals_weight_fields CHECK (
        (kind = 'WEIGHT' AND target_weight_kg IS NOT NULL AND start_weight_kg IS NOT NULL)
        OR (kind = 'GENERAL' AND target_weight_kg IS NULL AND start_weight_kg IS NULL)),
    ADD CONSTRAINT chk_athlete_goals_target_range CHECK (
        target_weight_kg IS NULL OR target_weight_kg BETWEEN 20 AND 300);

-- The active-goal limit is now per KIND as well: a technique goal and a weight goal may share a
-- horizon, because they are different kinds of thing and should not compete for one slot.
DROP INDEX uq_athlete_goals_active;
CREATE UNIQUE INDEX uq_athlete_goals_active
    ON athlete_goals (athlete_id, kind, horizon) WHERE achieved_at IS NULL;
