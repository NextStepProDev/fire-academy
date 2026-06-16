-- Telefon w zapisach staje się opcjonalny: admin może dopisać zalogowanego użytkownika,
-- który świadomie nie podał numeru (RODO — minimalizacja danych). Zapis publiczny nadal
-- wymaga telefonu na poziomie walidacji DTO (anonimowy uczestnik nie ma innego kontaktu).
ALTER TABLE enrollments ALTER COLUMN phone DROP NOT NULL;
