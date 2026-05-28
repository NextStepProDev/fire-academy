CREATE TABLE instructor_categories (
    instructor_id UUID NOT NULL REFERENCES instructors(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    PRIMARY KEY (instructor_id, category),
    CONSTRAINT chk_instructor_category CHECK (category IN ('CAMP', 'COURSE', 'TRAINING'))
);

CREATE INDEX idx_instructor_categories_category ON instructor_categories(category);

-- Existing instructors get all current categories
INSERT INTO instructor_categories (instructor_id, category)
SELECT id, 'CAMP' FROM instructors
UNION ALL
SELECT id, 'COURSE' FROM instructors;

-- Allow TRAINING in event_types for future use
ALTER TABLE event_types DROP CONSTRAINT chk_event_types_category;
ALTER TABLE event_types ADD CONSTRAINT chk_event_types_category CHECK (category IN ('CAMP', 'COURSE', 'TRAINING'));
