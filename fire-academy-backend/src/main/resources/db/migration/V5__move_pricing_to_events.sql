ALTER TABLE event_types
    DROP COLUMN price,
    DROP COLUMN max_participants,
    DROP COLUMN duration;

ALTER TABLE events
    ADD COLUMN duration VARCHAR(100);
