-- Snapshot of the NET amount (bill minus surplus credit) actually collected when a month was marked paid.
-- Without it the displayed amount was recomputed live (price × sessions), so it drifted during the month
-- and price edits rewrote the history of already-paid months. NULL = legacy payment rows from before this
-- column existed (displays fall back to the live reconstruction for those).
ALTER TABLE training_payments ADD COLUMN amount NUMERIC(10,2);
