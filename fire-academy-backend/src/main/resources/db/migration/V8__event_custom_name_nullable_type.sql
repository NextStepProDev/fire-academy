ALTER TABLE events ADD COLUMN category VARCHAR(20);
UPDATE events SET category = (SELECT category FROM event_types WHERE event_types.id = events.event_type_id);
ALTER TABLE events ALTER COLUMN category SET NOT NULL;

ALTER TABLE events ADD COLUMN custom_name VARCHAR(255);

ALTER TABLE events ALTER COLUMN event_type_id DROP NOT NULL;

ALTER TABLE events ADD CONSTRAINT chk_event_name CHECK (event_type_id IS NOT NULL OR custom_name IS NOT NULL);

CREATE INDEX idx_events_category ON events(category);
