-- Photos attached to 1-on-1 training comments — a screenshot from a sports watch, so the coach can
-- read heart rate, zones and pace instead of having them retyped.
--
-- The photo is a COLUMN on the comment, not a table of its own and not a row in
-- training_attachments. Two reasons, both load-bearing:
--
--   * TrainingUnreadService reads from seven sources and its failure mode is silence — a source
--     nobody remembers to add simply never notifies anyone. Hanging the photo off the comment row
--     means the unread dots, the roster badge and the per-card marker all keep working with no
--     change to that service at all.
--   * training_attachments carries the COACH's materials (videos, links) and is copied by
--     duplicate/paste. An athlete's screenshot must not travel with a copied plan. Its chk_ta_owner
--     also fixes the owner as training_id XOR template_id, so a comment_id would mean rewriting it.
--
-- One photo per comment (that is what a column buys), at most three per training — enforced in
-- TrainingPhotoService, because the limit is about the training and the rows hang off comments.
ALTER TABLE training_comments
    ADD COLUMN photo_filename   VARCHAR(64),
    ADD COLUMN photo_width      SMALLINT,
    ADD COLUMN photo_height     SMALLINT,
    ADD COLUMN photo_expires_at TIMESTAMPTZ;

-- A photo with no words is a whole message ("here is how it went"), so body stops being required.
-- The CHECK keeps the row meaningful: text, a photo, or both — never an empty bubble.
ALTER TABLE training_comments
    ALTER COLUMN body DROP NOT NULL;

ALTER TABLE training_comments
    ADD CONSTRAINT chk_tc_content CHECK (body IS NOT NULL OR photo_filename IS NOT NULL);

-- Photos are deleted 30 days after upload. The expiry is STORED rather than derived from created_at
-- so the client can be shown the real date instead of recomputing it from a constant, and so
-- changing the retention window later does not silently rewrite the fate of existing rows.
CREATE INDEX idx_tc_photo_expiry ON training_comments (photo_expires_at)
    WHERE photo_filename IS NOT NULL;

-- Widening the art. 9 consent: V38's wording lists weigh-ins, the trend, weight goals, calorie
-- targets, effort ratings and comments. It does not mention images, and a screenshot from a watch
-- carries more than its author meant to show. Consent given against the old text cannot cover the
-- new scope, so it is withdrawn here and every client ticks once more against the new wording.
-- Nothing is deleted — only the proof of consent, which is exactly what has to be re-obtained.
UPDATE users SET training_consent_at = NULL WHERE training_consent_at IS NOT NULL;
