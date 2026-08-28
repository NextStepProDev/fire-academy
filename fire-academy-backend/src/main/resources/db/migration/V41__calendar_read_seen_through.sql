-- How far into the calendar this viewer has actually looked.
--
-- The read marker held only a timestamp, so opening any single page stamped "seen" over the whole
-- plan. A month the client never reached was cleared from their badge without a dot ever appearing
-- on it: the coach's work was announced and then silently swallowed.
--
-- Nullable and deliberately NOT backfilled. NULL means "no day has been reached yet", which is the
-- only honest statement about the existing rows — we do not know which windows those people saw.
-- The cost is one pass of dots on future entries after the deploy; claiming everything had been
-- seen would undo the very fix.
ALTER TABLE training_calendar_reads ADD COLUMN seen_through DATE;

COMMENT ON COLUMN training_calendar_reads.seen_through IS
    'Last calendar day this viewer has opened; moves forward only. NULL = nothing reached yet.';
