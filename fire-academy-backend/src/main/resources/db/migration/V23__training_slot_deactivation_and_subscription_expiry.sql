-- Zaplanowana dezaktywacja slotu (od konkretnej daty) + flaga powiadomienia o wygaśnięciu subskrypcji.

-- Slot przestaje się odbywać od tej daty (NULL = aktywny bezterminowo). Slot pozostaje
-- widoczny/zapisywalny do dnia poprzedzającego; od daty znika z katalogu publicznego.
ALTER TABLE training_slots ADD COLUMN deactivated_from DATE;

-- Czy wysłano już maila o zakończeniu subskrypcji (zapobiega podwójnym powiadomieniom
-- ze schedulera; ustawiane też przy rezygnacji, by nie dublować z mailem o rezygnacji).
ALTER TABLE training_enrollments ADD COLUMN expiry_notified BOOLEAN NOT NULL DEFAULT false;
