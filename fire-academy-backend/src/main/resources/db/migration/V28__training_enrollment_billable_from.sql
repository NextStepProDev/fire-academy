-- Optional per-enrollment override of the first-month proration anchor.
-- NULL = fall back to created_at (the signup timestamp). Lets the organizer set the real start day at the
-- first payment ("count from day X") and recompute the bill cleanly, without deleting and re-adding.
ALTER TABLE training_enrollments ADD COLUMN billable_from DATE;
