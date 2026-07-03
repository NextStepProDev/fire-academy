-- Surplus ("nadwyżka") from a refund settled as CREDITED reduces the subscriber's next unpaid month(s).
-- When a paid month is covered (fully or partly) by that carried-over surplus, we record how much of the
-- month's bill the credit covered here. This makes the credit "consumed" once, so it cannot be applied twice
-- across months. Presence of a training_payments row = the month is paid; credit_applied = the part of that
-- paid month that was covered by surplus rather than fresh cash. Legacy paid months predate credits → 0.
ALTER TABLE training_payments ADD COLUMN credit_applied NUMERIC(10,2) NOT NULL DEFAULT 0;
