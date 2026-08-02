-- Explicit consent to processing 1-on-1 calendar health data (GDPR art. 9(2)(a)).
--
-- The calendar stores what nothing else in the service stores: weigh-ins with dates and the trend
-- built from them, weight goals, daily calorie targets, perceived-effort ratings and written notes
-- on how a session felt. In that context this is data concerning health, and art. 9 asks for
-- EXPLICIT consent — a statement the client makes, not one inferred from the fact that they typed
-- a number into a form. Until now the policy claimed the act of typing WAS the consent; it is not.
--
-- Same shape as privacy_accepted_at (V17) and marketing_consent_at (V18): NULL means not given,
-- and the timestamp itself is the proof GDPR requires. Deliberately not backfilled — clients who
-- have been using the calendar for months pass the consent screen once, on their next visit.
--
-- Dropping the is_athlete flag clears this too (User.setAthlete), so coming back to the calendar
-- after a break is a fresh decision rather than one inherited from a past arrangement.
ALTER TABLE users
    ADD COLUMN training_consent_at TIMESTAMPTZ;
