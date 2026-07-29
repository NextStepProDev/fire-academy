-- Library of exercise demonstration videos (unlisted YouTube links) the coach reuses across clients.
CREATE TABLE exercise_videos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    url VARCHAR(500) NOT NULL,
    -- The YouTube id extracted from the URL. Deduplication keys on THIS, not on the URL:
    -- watch?v=X, youtu.be/X and youtu.be/X?t=30 are the same video.
    video_key VARCHAR(64) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(80),
    -- lower(name + category) with Polish diacritics stripped, maintained by the entity. Lets
    -- "cwiczenie" find "Ćwiczenie…" without asking the coach to type accents.
    -- Deliberately a plain LIKE over a trigram index: a CREATE EXTENSION in a migration is a real
    -- operational cost, and at a few hundred rows a sequential scan is microseconds.
    -- Revisit past roughly 5000 videos: add pg_trgm + a GIN index on search_text.
    search_text TEXT NOT NULL,
    -- Retiring a video hides it from the picker without breaking the trainings that reference it.
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_exercise_videos_key UNIQUE (video_key)
);
CREATE INDEX idx_exercise_videos_browse ON exercise_videos (archived_at, name);

-- Reusable training skeletons ("Siła A", "Interwały 6x400").
CREATE TABLE training_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    -- Nullable: an untimed training has no duration, so forcing one would generate data nobody uses.
    default_duration_minutes INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_training_templates_duration
        CHECK (default_duration_minutes IS NULL OR default_duration_minutes BETWEEN 15 AND 720)
);

-- Material attached to a training or to a template — never both.
CREATE TABLE training_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID REFERENCES personal_trainings(id) ON DELETE CASCADE,
    template_id UUID REFERENCES training_templates(id) ON DELETE CASCADE,
    -- FILE joins this list when uploads land; extending the CHECK is then the whole migration.
    kind VARCHAR(10) NOT NULL,
    label VARCHAR(150),
    url VARCHAR(500),
    -- RESTRICT, not CASCADE: reference counting done by the database. A video in use cannot be
    -- deleted (the coach archives it instead), so no counter has to be kept in sync by hand.
    video_id UUID REFERENCES exercise_videos(id) ON DELETE RESTRICT,
    position SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ta_owner CHECK (num_nonnulls(training_id, template_id) = 1),
    CONSTRAINT chk_ta_position CHECK (position BETWEEN 0 AND 2),
    CONSTRAINT chk_ta_kind CHECK (
        (kind = 'LINK'  AND url IS NOT NULL AND video_id IS NULL) OR
        (kind = 'VIDEO' AND video_id IS NOT NULL AND url IS NULL))
);
-- Closes the "max 3 materials" rule in the database. Enforcing it only in the service leaves two
-- concurrent writes able to slip past it.
CREATE UNIQUE INDEX uq_ta_training_pos ON training_attachments (training_id, position)
    WHERE training_id IS NOT NULL;
CREATE UNIQUE INDEX uq_ta_template_pos ON training_attachments (template_id, position)
    WHERE template_id IS NOT NULL;
CREATE INDEX idx_ta_video ON training_attachments (video_id) WHERE video_id IS NOT NULL;
