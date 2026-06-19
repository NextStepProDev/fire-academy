-- Kolumna nigdy nie była egzekwowana — żaden mail service nie sprawdzał flagi.
-- Toggle „powiadomienia email" usunięty z UI razem z marketingiem (V18); odrębny opt-in
-- marketingu (marketing_consent_at) zastąpił ten mechanizm w całości.
ALTER TABLE users DROP COLUMN email_notifications_enabled;
