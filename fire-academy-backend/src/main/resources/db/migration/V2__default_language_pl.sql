ALTER TABLE users ALTER COLUMN preferred_language SET DEFAULT 'pl';

UPDATE users SET preferred_language = 'pl' WHERE preferred_language IN ('en', 'es');
