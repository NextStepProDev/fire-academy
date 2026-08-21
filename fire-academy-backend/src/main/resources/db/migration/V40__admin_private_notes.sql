-- The owner's private notebook: observations written while looking at a session, readable by
-- nobody but their author. Not a message to anyone -- the client, the athlete and a second admin
-- must never see a word of it.

CREATE TABLE admin_private_notes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id    UUID NOT NULL REFERENCES users(id)              ON DELETE CASCADE,

    -- Exactly one anchor. Three real foreign keys rather than a (target_type, target_id) pair:
    -- the pair would buy one upsert instead of four and pay for it with rows nothing cleans up.
    -- A note has to die with the thing it is about, and with its author's account.
    training_id  UUID REFERENCES personal_trainings(id)          ON DELETE CASCADE,
    event_id     UUID REFERENCES events(id)                      ON DELETE CASCADE,
    slot_id      UUID REFERENCES training_slots(id)              ON DELETE CASCADE,

    -- Narrows a slot to ONE dated occurrence inside ONE person's calendar. A group session has no
    -- row of its own -- it is computed on every read -- so it is addressed by this composite key
    -- instead of by an id. Both columns or neither.
    athlete_id   UUID REFERENCES users(id)                       ON DELETE CASCADE,
    session_date DATE,

    body         VARCHAR(4000) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_apn_single_target CHECK (
        (training_id IS NOT NULL)::int + (event_id IS NOT NULL)::int + (slot_id IS NOT NULL)::int = 1
    ),
    CONSTRAINT chk_apn_session_shape CHECK (
        (session_date IS NULL AND athlete_id IS NULL)
        OR (session_date IS NOT NULL AND athlete_id IS NOT NULL AND slot_id IS NOT NULL)
    ),
    -- An empty note is a deleted note, not an empty one.
    CONSTRAINT chk_apn_body_not_blank CHECK (btrim(body) <> '')
);

-- The predicates are not decoration. Two of the three target columns are NULL in every row, and
-- NULLs do not collide in a plain UNIQUE -- without a predicate the index would wave through any
-- number of notes "with no slot". The predicate is also what lets ON CONFLICT (...) WHERE ...
-- name this exact index.
CREATE UNIQUE INDEX uq_apn_training ON admin_private_notes (author_id, training_id)
    WHERE training_id IS NOT NULL;
CREATE UNIQUE INDEX uq_apn_event    ON admin_private_notes (author_id, event_id)
    WHERE event_id IS NOT NULL;
CREATE UNIQUE INDEX uq_apn_slot     ON admin_private_notes (author_id, slot_id)
    WHERE slot_id IS NOT NULL AND session_date IS NULL;
CREATE UNIQUE INDEX uq_apn_session  ON admin_private_notes (author_id, athlete_id, slot_id, session_date)
    WHERE session_date IS NOT NULL;

-- Markers: "which of these has a note", one query per calendar page. The slot, event and training
-- markers are already served by the unique indexes above; only the per-athlete session lookup needs
-- its own, because those rows are found by (author, athlete, date) rather than by (author, slot).
CREATE INDEX idx_apn_author_athlete ON admin_private_notes (author_id, athlete_id, session_date) WHERE session_date IS NOT NULL;

-- Foreign keys need indexes of their OWN, and Postgres does not create them. Every index above leads
-- with author_id, which answers "this owner's notes" but is useless to a cascade: deleting a training
-- asks `WHERE training_id = ?`, and with the column in second position that is a sequential scan of
-- the whole table. The coach deletes trainings routinely, so this would be paid on every one of them
-- -- invisibly at first, since the table starts empty.
CREATE INDEX idx_apn_training ON admin_private_notes (training_id) WHERE training_id IS NOT NULL;
CREATE INDEX idx_apn_event    ON admin_private_notes (event_id)    WHERE event_id IS NOT NULL;
CREATE INDEX idx_apn_slot     ON admin_private_notes (slot_id)     WHERE slot_id IS NOT NULL;
CREATE INDEX idx_apn_athlete  ON admin_private_notes (athlete_id)  WHERE athlete_id IS NOT NULL;
