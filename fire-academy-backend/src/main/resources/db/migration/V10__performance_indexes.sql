CREATE INDEX idx_enrollments_event_email ON enrollments(event_id, email);

CREATE INDEX idx_events_category_active_date ON events(category, active, start_date);
