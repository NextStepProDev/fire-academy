-- Conversation attached to a single training. Two people ever, so no thread model is needed.
CREATE TABLE training_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID NOT NULL REFERENCES personal_trainings(id) ON DELETE CASCADE,
    -- The author may later delete their account; the comment stays as context for the other side.
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Role FROZEN at the time of writing. Deriving it from users.role later would relabel a client's
    -- old comments as coach comments the day they are made an admin, and flip their unread dots.
    author_is_admin BOOLEAN NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_training_comments_training ON training_comments (training_id, created_at);
-- Unread counters filter by author role and recency.
CREATE INDEX idx_training_comments_unread ON training_comments (training_id, author_is_admin, created_at DESC);

-- "Last time this viewer looked at this calendar".
-- The client is the row where user_id = athlete_id; a coach gets one row per client, so several
-- admins keep independent counters. A missing row means EPOCH — count everything.
CREATE TABLE training_calendar_reads (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seen_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, athlete_id)
);

-- A deleted future training leaves nothing behind to point at, so the alert needs its own snapshot.
-- Only FUTURE deletions are recorded: clearing out past entries is housekeeping, not news.
CREATE TABLE training_deletions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    training_date DATE NOT NULL,
    start_time TIME,
    title VARCHAR(150) NOT NULL,
    -- Who deleted it. Both sides may delete, and the alert has to travel the other way.
    deleted_by_admin BOOLEAN NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Dismissing the banner is a separate act from "I opened my calendar", so it gets its own
    -- column instead of riding on the seen marker.
    dismissed_at TIMESTAMPTZ
);
CREATE INDEX idx_training_deletions_athlete ON training_deletions (athlete_id, deleted_at DESC);
