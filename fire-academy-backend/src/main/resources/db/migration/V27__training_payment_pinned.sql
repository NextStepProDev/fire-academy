-- Distinguishes how a monthly payment row was created:
--   pinned = true  → marked paid individually via the per-slot roster toggle (one specific training).
--                    Such a payment survives a whole-month "revert" — it is only cleared when the admin
--                    un-marks that very training on the roster.
--   pinned = false → added by the whole-month "pay for the person" action (or legacy rows). A whole-month
--                    revert removes these.
-- Existing rows default to false (revertible), which matches how they were created before this column.

ALTER TABLE training_payments ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false;
