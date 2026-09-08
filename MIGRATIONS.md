# Fire Academy — migracje bazy (Flyway)

> Wydzielone z `CLAUDE.md`. Pełna historia V1–V42. Uzasadnienia decyzji treningowych → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md).

## Baza Danych — Flyway

**Obecny stan: V42 (wszystko na `main`). Kolejna migracja: V43.**
> ℹ️ Migracje treningowe zostały przenumerowane z V12–V15 na **V20–V23** po rebasie na main (2026-06-21). Luka V12–V15 nie jest już zarezerwowana.
> ℹ️ V29–V38 (kalendarz 1:1 + waga + cele wagowe + zadania + zgoda RODO) zostały scalone do `main` 2026-08-02 wraz z resztą gałęzi treningowej.

| Wersja | Co dodaje |
|--------|-----------|
| V1 | users, auth_tokens |
| V2 | default language → pl, migracja istniejących en/es → pl |
| V3 | instructors, event_types, event_type_photos, events, enrollments |
| V4 | instructor_categories (kadra per kategoria: CAMP/COURSE/TRAINING) |
| V5 | przeniesienie price/max_participants/duration z event_types do events |
| V6 | end_time w events, usunięcie duration |
| V7 | description w events (opis terminu) |
| V8 | category + custom_name w events, nullable event_type_id |
| V9 | note w enrollments (informacja dla organizatora) |
| V10 | indeksy wydajnościowe: enrollments(event_id, email), events(category, active, start_date) |
| V11 | avatar_filename w users (zdjęcie profilowe użytkownika, folder `avatars/`) |
| V12–V15 | *(puste — historycznie zarezerwowane przez gałąź treningową, przeniesione na V20–V23 po rebasie)* |
| V16 | enrollments.phone → nullable (admin może dopisać zalogowanego usera bez numeru; RODO — minimalizacja) |
| V17 | enrollments.user_id → FK do users (ON DELETE SET NULL) + indeks + unikat (user_id,event_id); users.privacy_accepted_at (zgoda RODO). **Zapis wymaga konta** (PII = źródło prawdy w users — roster admina i maile czytają aktualne dane przez `Enrollment.display*()`; kolumny snapshotu firstName/lastName/email/phone w `enrollments` to **tylko fallback** dla czytelności archiwum po usunięciu konta, nie odświeżane przy edycji profilu) |
| V18 | users.marketing_consent_at (zgoda marketingowa opt-in, NULL=brak; wzorzec jak privacy_accepted_at) + users.marketing_unsubscribe_token (UUID, NOT NULL DEFAULT gen_random_uuid(), unikat — stabilny token linku rezygnacji bez logowania). **Marketing odrębny od maili serwisowych**: serwisowe (zapisy/odwołania, weryfikacja, reset) zawsze idą; marketing tylko za zgodą i z linkiem rezygnacji |
| V19 | DROP users.email_notifications_enabled — kolumna nigdy nie była egzekwowana (żaden mail service nie sprawdzał flagi); zastąpiona całkowicie przez marketing_consent_at. Usunięty endpoint `PUT /me/notifications` + DTO + frontowy `authApi.updateNotifications` |
| V20 | training_slots — cykliczne sloty treningowe (dzień tygodnia + godziny, rodzaj/trener, max uczestników, cena, aktywność) + training_enrollment (miesięczne subskrypcje user→slot, FK users ON DELETE CASCADE) |
| V21 | training_payment — rejestr płatności miesięcznych per subskrypcja (oznaczanie opłacone/nieopłacone w rosterze) |
| V22 | training_cancelled_session — odwołania pojedynczych zajęć (soft-delete) + archiwum |
| V23 | dezaktywacja slotu od konkretnej daty + wygaśnięcie subskrypcji terminowej (scheduler) |
| V24 | training_holidays (dni wolne klubu) + training_refunds (zwroty za opłacone zajęcia, które się nie odbyły; REFUNDED / CREDITED). Billing w `TrainingBillingService`, zwroty w `TrainingRefundService` |
| V25 | training_payments.credit_applied — nadwyżka CREDITED obniża najbliższy nieopłacony miesiąc (`TrainingCreditService`). Płatności: okno 7 dni przed startem + chronologia (opłacone = ciągły prefiks) |
| V26 | training_payments.amount — snapshot kwoty NET przy „opłacone" + pakiet audytu przedprodukcyjnego (proracja od daty zapisu, `ClosureCause`, blokady cofnięcia/rezygnacji, strefa czasowa) |
| V27 | training_payments.pinned — płatność oznaczona per slot jest „przypięta"; zbiorcze cofnięcie miesiąca jej nie rusza |
| V28 | training_enrollments.billable_from — korekta „licz od dnia X" pierwszego miesiąca (`PUT /admin/training-enrollments/{id}/start`) + sygnał `overdue` |
| V29 | users.is_athlete — flaga podopiecznego 1:1 (indeks częściowy). **Zdjęcie flagi niczego nie kasuje**; nie wyprowadzana z subskrypcji grupowych |
| V30 | personal_trainings — wspólny plan trener↔podopieczny. Godziny nullable (brak = przypadek domyślny), `@Version`, RPE 1–10 związane CHECK-iem z ukończeniem, `MISSED` liczony a nie zapisywany |
| V31 | training_comments (`author_is_admin` = rola zamrożona w chwili wpisu) + training_calendar_reads (liczniki per para) + training_deletions (migawka usuniętych przyszłych treningów) |
| V32 | exercise_videos (dedup po `video_key`) + training_templates (użycie **kopiuje** treść) + training_attachments (`video_id` ON DELETE RESTRICT, limit 3 domknięty w bazie) |
| V33 | athlete_goals — cele na 3 horyzontach (SHORT/MEDIUM/LONG), partial UNIQUE tylko na aktywnych; osiągnięty cel niezmienny |
| V34 | athlete_weights — poranna waga (unikat osoba+dzień = korekta, nie drugi pomiar). Trend = średnia krocząca 7 dni liczona serwerowo. Ostrzeżenie o szybkim spadku **tylko dla trenera**; brak zapisu po stronie admina |
| V35 | athlete_goals + `kind` GENERAL/WEIGHT, `target_weight_kg`, `start_weight_kg`. **Cel wagowy zamyka się sam, ale wyłącznie na trendzie 7-dniowym**; cofnąć można tylko osiągnięcie automatyczne |
| V36 | DROP exercise_videos.category — treść wtopiona w `name`; nazwa filmu uzupełnia się z tytułu YouTube (oEmbed, request z **sparsowanego** `video_key`) |
| V37 | personal_trainings + `kind` TRAINING/TASK, `target_calories`. **Zadanie to osobny wiersz**, odhaczane bez RPE; statystyki treningowe zadań nie widzą — mają własny blok `tasks` |
| V38 | users.training_consent_at — wyraźna zgoda RODO art. 9 na dane zdrowotne planu 1:1 (waga, trend, cele wagowe, limity kalorii, RPE, komentarze). NULL = brak; **celowo bez backfillu**, więc każdy obecny podopieczny raz przechodzi ekran zgody. Zdjęcie `is_athlete` **kasuje zgodę** (`User.setAthlete`) — dane wracają po ponownym włączeniu, zgoda nie |
| V39 | training_comments + `photo_filename/width/height/expires_at`; `body` staje się nullable + CHECK `body IS NOT NULL OR photo_filename IS NOT NULL`. **Zdjęcie to kolumna na komentarzu**, nie tabela — dziedziczy liczniki nieprzeczytanych i kaskadę bez zmian w `TrainingUnreadService`. Maks. **3 na trening** i **25 dziennie na kalendarz podopiecznego**, retencja **30 dni** (`photo_expires_at` zapisane, nie liczone). Folder `trainingphotos/` **poza** białą listą `FileController`. Migracja **zeruje `training_consent_at` wszystkim** — zakres art. 9 poszerzony o zdjęcia, więc stara zgoda go nie obejmuje |
| V40 | admin_private_notes — prywatne notatki właściciela (trening 1:1 · zajęcia cykliczne w kalendarzu osoby · slot tygodniowy · termin). **Trzy kolumny celu, cztery cele**: `slot_id` obsługuje dwa, rozróżniane przez `session_date` (zajęcia cykliczne nie mają wiersza nigdzie). Cztery prawdziwe FK z kaskadą zamiast dyskryminatora + cztery **partial** unique. Szczegóły → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md), sekcja „Prywatne notatki właściciela — niezmienniki" |
| V41 | training_calendar_reads.seen_through — jak daleko w kalendarz ten czytelnik faktycznie zajrzał. Znacznik miał samą godzinę, więc otwarcie **jednej strony** stemplowało jako przeczytany **cały plan**: miesiąc, do którego nikt nie doszedł, znikał z plakietki, nie zapaliwszy ani jednej kropki. Kolumna **przesuwa się tylko do przodu** (`GREATEST`, a `GREATEST` pomija NULL-e, i to przenosi stare wiersze). **Bez backfillu, świadomie** — NULL znaczy „nie doszedł jeszcze nigdzie", co jest jedynym prawdziwym zdaniem o istniejących wierszach. Szczegóły → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md), sekcja „Liczniki nieprzeczytanych" |
| V42 | indeksy pod zapytania, które ich nie miały: `training_refunds.session_date` (trzy zapytania filtrują po niej, a unikat `(enrollment_id, session_date)` ma ją jako **drugą** kolumnę, więc żadne z nich go nie użyje), `training_comments.author_id` (FK bez indeksu, czytany przy kasowaniu konta, żeby odpiąć zdjęcia zostawione w cudzym wątku) i `training_calendar_reads.athlete_id` (drugi człon PK — wymazanie planu osoby robiło pełny skan). **Dług, nie awaria**: przy dzisiejszej skali skan jest szybszy niż indeks. Dodane, bo każda z tych tabel rośnie bez sufitu, a moment, w którym skan przestaje być darmowy, nikomu się nie zgłasza |

> 📖 **Pełne uzasadnienia V24–V39 → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md).** Tam leży „dlaczego" (bezpieczniki rozliczeń, kolejność płatności, kontrakty pól, testy pilnujące każdej reguły). Czytaj przed zmianą w danym obszarze.

