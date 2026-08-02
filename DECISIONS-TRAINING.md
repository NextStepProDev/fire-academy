# Decyzje projektowe — treningi (V24–V37)

Uzasadnienia migracji od V24 w górę: **dlaczego** schemat wygląda tak, a nie inaczej.
Wyprowadzone z `CLAUDE.md`, żeby nie obciążać kontekstu każdej sesji — CLAUDE.md ma
jednolinijkowe „co dodaje", tutaj leży pełne „dlaczego". Plik siedzi w roocie, a nie w `docs/`,
bo `docs/` jest w `.gitignore` — te uzasadnienia mają jechać razem z repo.

Czytaj przed zmianą w danym obszarze. Reguły, które łatwo złamać **każdą** zmianą w
kalendarzu 1:1, zostały w CLAUDE.md (sekcja „Kalendarz treningów 1:1 — pułapki").

---

## Rozliczenia treningów grupowych (V24–V28)

### V24 — dni wolne i zwroty

`training_holidays` (globalne dni wolne klubu — obniżają liczbę zajęć/cenę wszystkich slotów
tego dnia tygodnia) + `training_refunds` (rejestr zwrotów: opłacone zajęcia, które się nie odbyły
= należność; typ HOLIDAY/SESSION; rozliczenie `settled_at` + `settlement_type` REFUNDED (zwrot
gotówki) / CREDITED (zaliczone na poczet miesiąca)).

Billing scentralizowany w `TrainingBillingService` (odejmuje dni wolne + odwołane zajęcia);
zwroty w `TrainingRefundService` (rejestracja przy odwołaniu opłaconego miesiąca, cofnięcie przy
przywróceniu/odznaczeniu płatności). Odwołania zajęć: pojedyncze (per slot+data), **odwołanie
wszystkich zajęć trenera w danym dniu** (`POST /admin/training-slots/cancel-instructor-day`),
oraz dzień wolny = cały klub. Zakładki admina „Dni wolne" i „Zwroty" w sekcji Treningi.

### V25 — nadwyżka (credit) realnie obniża rachunek

`training_payments.credit_applied` — nadwyżka ze zwrotu rozliczonego jako CREDITED realnie
obniża rachunek. `TrainingCreditService`: saldo nadwyżki = suma zwrotów CREDITED − nadwyżka
skonsumowana (zamrożona na opłaconych miesiącach w `credit_applied`); obniża najbliższy
nieopłacony miesiąc **nie wcześniejszy niż miesiąc źródłowy nadwyżki** (nadpłata za sierpień
idzie na wrzesień, nie na lipiec), overflow roluje na kolejne; docinana do żywego rachunku
(`cena × zajęcia`). Konsumpcja przy oznaczeniu „opłacone", zwrot przy odznaczeniu.

Bezpiecznik: nie można cofnąć rozliczenia CREDITED, którego nadwyżka już skonsumowana
(`trainingrefund.credit.consumed`). User widzi NET + „uwzględniono nadwyżkę −X zł"; roster
pokazuje saldo nadwyżki.

**Płatności: okno + chronologia** (`setPayment`): miesiąc otwiera się do płatności dopiero na
7 dni przed startem (`too.early`, to samo okno co estymata) — nie da się opłacić sierpnia
w środku lipca; dodatkowo chronologia (nie opłacisz miesiąca, gdy wcześniejszy nieopłacony; nie
cofniesz, gdy późniejszy opłacony) → opłacone miesiące = ciągły prefiks, stan „sierpień opłacony,
lipiec nie" nie powstaje.

Testy: `TrainingCreditServiceTest` / `AdminTrainingRefundServiceTest` /
`AdminTrainingEnrollmentServiceTest` (jednostkowe, niezależne od zegara).

### V26 — snapshot kwoty + audyt przedprodukcyjny (2026-07-03)

`training_payments.amount` — **snapshot kwoty NET zamrożony przy oznaczeniu „opłacone"**
(NULL = stare wpisy → fallback na przeliczenie na żywo). Razem z nim pakiet poprawek audytu:

1. **Proracja od daty zapisu, nie „od dziś"** — `TrainingBillingService.sessions(te, month)`:
   stały bywalec płaci pełny miesiąc niezależnie od dnia zapłaty, proracja tylko w miesiącu,
   w którym powstał zapis (od dnia zapisu); wariant `sessions(slot, month)` „od dziś" zostaje
   wyłącznie do podglądu nowego zapisu (katalog/modal).
2. **Blokada cofnięcia płatności z rozliczonym zwrotem**
   (`trainingpayment.unpay.settled.refund`) — najpierw cofnij rozliczenie zwrotu.
3. **Zwroty świadome przyczyny zamknięcia daty** (`ClosureCause`
   SINGLE_SESSION/HOLIDAY/DEACTIVATION): rejestracja pomija datę już zamkniętą innym
   mechanizmem (bez zwrotu za zajęcia, których nie było w opłaconym rachunku),
   revoke/bezpieczniki dotykają tylko zwrotów, które realnie ożywają (usunięcie dnia wolnego nie
   kasuje zwrotu z osobnego odwołania itd.); odwołanie sesji/dnia trenera na dacie dnia
   wolnego → 409.
4. **Blokada rezygnacji usera i usunięcia przez admina przy opłaconym bieżącym/przyszłym
   miesiącu** (`trainingenrollment.cancel.paid.future` / `remove.paid`) — hard delete nie zje
   wpłat.
5. **Usunięcie konta z aktywną subskrypcją**: mail do organizatora + jawne skasowanie
   subskrypcji (`closeSubscriptionsBeforeAccountDeletion`).
6. **Strefa czasowa** `-Duser.timezone=Europe/Warsaw` w Dockerfile + `TZ` w compose (JVM w UTC
   psuła okno płatności i granice miesiąca o północy).
7. **Drobne**: zapis/katalog ukrywa miesiące po pełnej dezaktywacji slotu, admin-add na usunięty
   slot → 404, scheduler wygaśnięć pomija usunięte sloty, kontrola duplikatu = prawdziwe
   nakładanie przedziałów (`existsOverlapping`).

Testy: rozszerzone `TrainingFlowIntegrationTest` (krzyżówki zamknięć, blokady),
`TrainingBillingServiceTest` (proracja), frontend `trainingSchedule.test.ts`.

### V27 — płatność przypięta

`training_payments.pinned` — płatność oznaczona pojedynczo na rosterze (per slot) jest
„przypięta": zbiorcze cofnięcie całego miesiąca jej nie rusza, kasuje ją tylko ten sam
przełącznik per slot.

### V28 — „licz od dnia X" + sygnał zaległości

`training_enrollments.billable_from` — opcjonalna korekta „licz od dnia X" pierwszego miesiąca
(NULL = fallback na `created_at` = dzień zapisu). Organizator ustawia realną datę startu przy
pierwszej płatności, rachunek przelicza się sam (`TrainingBillingService.billableFromDay` bierze
`billableFrom` gdy ustawione). Endpoint `PUT /admin/training-enrollments/{id}/start`
(`SetStartRequest`): data musi być w miesiącu startu (400) i miesiąc nie może być opłacony
(`trainingenrollment.start.paid`, 409).

Do tego **sygnał „zaległość po terminie"** (bez automatu kasującego): roster + Płatności
pokazują `overdue` = nieopłacone i po dacie pierwszych zajęć miesiąca + 1 dzień grace
(`TrainingBillingService.isPaymentOverdue`); usunięcie nieopłaconego zapisu zostaje ręczne.

Testy: `TrainingBillingServiceTest` (override + overdue),
`TrainingFlowIntegrationTest.shouldSetBillingStartDateAndBlockChangeOncePaid`.

---

## Kalendarz 1:1 (V29–V33, V37)

### V29 — flaga podopiecznego

`users.is_athlete` — flaga podopiecznego 1:1 (indeks częściowy `WHERE is_athlete = true`).
Ustawiana ręcznie przez admina w profilu użytkownika; **zdjęcie flagi niczego nie kasuje** —
plan, komentarze i cele zostają i wracają po ponownym włączeniu. Celowo NIE wyprowadzana
z subskrypcji grupowych: trening 1:1 to inna relacja handlowa. Nadanie flagi nie jest
przywilejem super-admina (nie daje żadnych uprawnień).

### V30 — wspólny plan trener↔podopieczny

`personal_trainings`. **`start_time`/`end_time` nullable od startu, a oba NULL to przypadek
DOMYŚLNY** (trening bez godziny = „zrób to w środę"); `CHECK` odrzuca koniec bez początku.
`@Version` **od pierwszej migracji**, nie doklejone później. Status `MISSED` **liczony, nigdy
zapisywany** (data w przeszłości + brak `completed_at`) — brak kolumny, brak nocnego zadania.
RPE 1–10 z `CHECK` wiążącym je z ukończeniem, więc cofnięcie wykonania musi je wyczyścić.

### V31 — komentarze, znaczniki przeczytania, migawki usunięć

- `training_comments` (czat przy treningu; **`author_is_admin` = rola ZAMROŻONA w chwili
  wpisu** — wyliczanie jej z `users.role` przemianowałoby stare komentarze podopiecznego w dniu,
  w którym zostanie adminem).
- `training_calendar_reads` (PK `(user_id, athlete_id)`; podopieczny = wiersz
  `user_id = athlete_id`, każdy admin ma niezależne liczniki; brak wiersza = EPOCH = licz
  wszystko).
- `training_deletions` (migawka usuniętych **przyszłych** treningów — oryginał znika, więc alert
  niesie własną kopię; `deleted_by_admin` bo kasować mogą obie strony; `dismissed_at` osobno od
  znacznika „widziane").

### V32 — biblioteka filmów, szablony, załączniki

- `exercise_videos` (biblioteka filmów YouTube; **dedup po `video_key`, nie po URL** —
  `watch?v=X`, `youtu.be/X` i `youtu.be/X?t=30` to jeden film; `search_text` bez polskich znaków,
  świadomie `LIKE` zamiast `pg_trgm` — próg wyjścia ~5000 filmów w komentarzu migracji).
- `training_templates` (użycie **kopiuje** treść, więc edycja szablonu nie przepisuje rozdanych
  treningów).
- `training_attachments` (`kind` LINK/VIDEO; **`video_id` z `ON DELETE RESTRICT`** = zliczanie
  referencji po stronie bazy, film w użyciu można tylko zarchiwizować; limit 3 domknięty
  `UNIQUE (właściciel, position)` + `CHECK position ≤ 2`, nie tylko w serwisie).

### V33 — cele na trzech horyzontach

`athlete_goals` — cele na 3 horyzontach (SHORT/MEDIUM/LONG), ustawiane przez trenera, read-only
u podopiecznego. **Partial `UNIQUE (athlete_id, horizon) WHERE achieved_at IS NULL`** — ogranicza
tylko AKTYWNE cele; zwykły unikat ograniczyłby podopiecznego do trzech celów na całe życie.
Osiągnięty cel jest **niezmienny** (brak edycji, usunięcia i ponownego osiągnięcia → 409) i trafia
do skrzyni trofeów; `achieved_at` to DATE, bo datowanie jest wsteczne.

### V37 — zadanie jako osobny wiersz

`personal_trainings` + `kind` TRAINING/TASK, `target_calories`.

**Zadanie to OSOBNY WIERSZ, nie pole przy treningu** — podopieczny może dowieźć trening
i przewalić kalorie tego samego dnia, a jeden checkbox nie umie tego powiedzieć; dwa wpisy = dwa
odhaczenia = dwie prawdy. `kind` **ustawiany przy tworzeniu i niezmienny**
(`UpdateTrainingRequest` w ogóle nie ma tego pola): przerobienie odhaczonego treningu na zadanie
musiałoby skasować jego RPE, żeby przejść CHECK-i.

Zadanie odhacza się **bez RPE** (`CHECK rpe IS NULL OR kind='TRAINING'`) — „jak ciężko było
zmieścić się w 2200 kcal, 1–10" to pytanie o nic, a odpowiedź trafiłaby do tych samych średnich,
z których trener czyta obciążenie. Limit kalorii jest **liczbą, nie tekstem w tytule**
(CHECK 500–10000, jak przy wadze łapie zgubione zero) — inaczej nie da się tego policzyć ani
zestawić z wagą.

Statystyki treningowe (seria, frekwencja, mapa, miesiące, `byType.personal`, „najbliższy
trening") **nie widzą zadań**; zadania mają własny blok `tasks`, w którym **każda liczba niesie
swój mianownik i swoje okno** (`thisMonthDone/thisMonthDue`, `windowDone/windowDue` = 90 dni,
`completionPercent`). Gołe liczniki z różnych okien obok siebie czytają się jak zestaw do
porównania, a porównać ich nie można; „3 z 4" broni się samo. Do mianownika wchodzi tylko to, co
**już zapadło** — zadanie jeszcze przed podopiecznym nie jest porażką, a `—` zamiast `0`
odróżnia „przewalone" od „nie było żadnych".

---

## Waga i cele wagowe (V34–V35)

### V34 — poranna waga

`athlete_weights` — unikat na parę osoba+dzień: **ponowne ważenie tego samego dnia to korekta,
nie drugi pomiar**; `CHECK` 20–300 kg łapie zgubiony przecinek.

**Świadomie BEZ kalorii spalonych** — tych nie da się zmierzyć (±20–30% nawet z zegarka),
a bilans oparty na zgadywance daje liczbę precyzyjnie wyglądającą i nieprawdziwą. Waga jest
pomiarem; przy dołożeniu spożycia realne zapotrzebowanie **wyliczy się z danych osoby**, nie ze
wzoru.

Trend = **średnia krocząca 7 dni**, liczona serwerowo per punkt (front nie ma własnej definicji
trendu); zmiana tygodniowa porównuje **dwa trendy** tydzień od siebie, nie dwa pojedyncze
ważenia. Ostrzeżenie o spadku >1%/tydz. **tylko dla trenera** (pole nieobecne w JSON
podopiecznego). **Brak endpointu zapisu po stronie admina** — waga wpisana przez trenera byłaby
drugim źródłem prawdy.

### V35 — cel wagowy zamykający się sam

`athlete_goals` + `kind` GENERAL/WEIGHT, `target_weight_kg`, `start_weight_kg`,
`achieved_automatically`.

**Cel wagowy zamyka się SAM** — ale wyłącznie na **trendzie 7-dniowym**, nigdy na pojedynczym
pomiarze: surowa liczba potrafi dotknąć celu przez odwodnienie i odbić nazajutrz, a świętowanie
tego przeczyłoby całemu modułowi wagi. `start_weight_kg` to zdjęcie trendu przy zakładaniu — daje
**kierunek** (w dół czy w górę; cel inaczej nie ma jak wiedzieć) i punkt odniesienia dla paska
postępu; bez żadnego pomiaru nie da się założyć celu (409).

**Cofnąć można TYLKO osiągnięcie automatyczne** (`POST /goals/{id}/reopen`) — literówka
w granicach zakresu ciągnie trend przez cel; decyzja człowieka pozostaje ostateczna. Partial
unique rozszerzony na `(athlete, kind, horizon)`, więc cel techniczny i wagowy nie konkurują
o ten sam horyzont. Ocena odpala się przy zapisie wagi (`MyTrainingController`), nie schedulerem.

---

## Biblioteka filmów (V36)

DROP `exercise_videos.category` — pole tekstowe bez podpowiedzi rozjeżdżało bibliotekę na
„nogi"/„Nogi"/„nogi/pośladki"; treść wtopiona w `name` (i przeliczony `search_text`), nazwa
niesie całe znaczenie.

Do tego nazwa filmu **uzupełnia się sama z tytułu YouTube** (publiczny oEmbed, bez klucza;
request budowany z **sparsowanego `video_key`**, nigdy z wklejonego stringa — inaczej to SSRF
z uprzejmą twarzą) i tylko do pustego pola.
