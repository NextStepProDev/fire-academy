-- A second kind of entry on the 1-on-1 plan: a TASK ("stay under 2200 kcal today") next to a
-- TRAINING.
--
-- Deliberately a separate ROW rather than a calorie field bolted onto a training. A client can nail
-- the session and blow the diet on the same day, and one tick box cannot say that: whatever it
-- reported would be a lie about half the day. Two entries, two ticks, two truths — and the numbers
-- stay countable on their own.
ALTER TABLE personal_trainings
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'TRAINING',
    -- Only tasks carry it, and it is a NUMBER rather than words inside the title: "max 2200 kcal"
    -- typed into a heading cannot be counted, charted, or read next to the weight trend later.
    ADD COLUMN target_calories INTEGER;

ALTER TABLE personal_trainings
    ADD CONSTRAINT chk_personal_trainings_kind
        CHECK (kind IN ('TRAINING', 'TASK')),
    -- The bounds catch a slipped digit the same way the weight range does; 500 is below any sane
    -- daily limit and 10000 above any sane one.
    ADD CONSTRAINT chk_personal_trainings_calories
        CHECK (target_calories IS NULL
               OR (kind = 'TASK' AND target_calories BETWEEN 500 AND 10000)),
    -- Perceived effort belongs to a training. "How hard was staying under 2200 kcal, 1-10" is a
    -- question about nothing, and an answer to it would quietly poison the RPE averages and the
    -- overtraining signal, which read every rated entry there is.
    ADD CONSTRAINT chk_personal_trainings_rpe_is_training
        CHECK (rpe IS NULL OR kind = 'TRAINING');
