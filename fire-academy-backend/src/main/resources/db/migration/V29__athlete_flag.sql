-- Marks a user as a 1-on-1 coaching client ("podopieczny") with a personal training calendar.
-- Set manually by an admin; clearing it hides the calendar but never deletes the data behind it.
-- Deliberately NOT derived from training_enrollments: group subscriptions and 1-on-1 coaching are
-- separate commercial relationships — someone can have either one without the other.
ALTER TABLE users ADD COLUMN is_athlete BOOLEAN NOT NULL DEFAULT false;

-- Partial index: a handful of athletes among thousands of accounts. A plain index on a boolean
-- this skewed would never be used; the partial one makes "list all athletes" an index-only scan.
CREATE INDEX idx_users_athlete ON users (is_athlete) WHERE is_athlete = true;
