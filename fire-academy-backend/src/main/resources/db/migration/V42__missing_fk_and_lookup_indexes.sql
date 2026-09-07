-- Indexes for three columns that queries filter on and nothing indexed.
--
-- All three are debt rather than a live problem: at this club's size Postgres scans these tables
-- faster than it would read an index. They are added now because each one grows without a ceiling,
-- and the point at which a sequential scan stops being free is not a moment anybody gets told about.

-- training_refunds.session_date
-- Three queries filter on it: the refunds of one slot on one day, the pending ones for that pair,
-- and every refund on a date. The table already has a unique index on (enrollment_id, session_date),
-- but session_date is its SECOND column, so none of those three can use it. The table gains a row
-- per paid subscriber per cancelled session, forever.
CREATE INDEX idx_training_refunds_session_date ON training_refunds(session_date);

-- training_comments.author_id
-- A foreign key with no index of its own. findPhotosForUser filters on it when an account is
-- deleted, to unlink photos its owner left in somebody else's thread — so the one query that needs
-- it runs during an erasure, which is exactly when it should not be slow.
CREATE INDEX idx_training_comments_author ON training_comments(author_id);

-- training_calendar_reads.athlete_id
-- Second column of the composite primary key (user_id, athlete_id), which a lookup by athlete alone
-- cannot use. Erasing one person's plan deletes every reader's marker for them, and did so with a
-- full scan.
CREATE INDEX idx_training_calendar_reads_athlete ON training_calendar_reads(athlete_id);
