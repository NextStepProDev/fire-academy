ALTER TABLE events ADD COLUMN end_time TIME;
ALTER TABLE events DROP COLUMN duration;
