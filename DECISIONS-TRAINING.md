# Decyzje projektowe — treningi (V24–V39)

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

### Najniższy potwierdzony trend (okno 90 dni)

Panel wagi pokazuje **najniższy POTWIERDZONY trend ostatnich 90 dni** — czyli minimum z tych dni,
w których okno trzymało co najmniej `MIN_READINGS_TO_CLOSE_GOAL` ważeń. Nie minimum z surowych
odczytów, z dwóch niezależnych powodów.

Po pierwsze: **to musi być ta sama liczba, która jest w stanie zamknąć cel wagowy.** Kafel wisi na
tym samym ekranie co cele, więc „rekord" niższy od celu, który się nie zamknął, to dwie wykluczające
się liczby obok siebie — i wtedy jedna z nich uczy, że drugiej nie warto wierzyć. Warunek
potwierdzenia mieszka odtąd w jednej metodzie (`WeightTrendCalculator.confirmedTrendOn`), a nie
w warunku przepisywanym u każdego wywołującego; `AthleteGoalService.evaluateWeightGoals` sprawdza
dalej to samo po swojemu (świadomie nietknięte przy tej zmianie — osobny commit).

Po drugie: **minimum z N próbek spada razem z N.** Ważący się codziennie „pobiłby rekord" niżej niż
ważący się dwa razy w tygodniu przy identycznej realnej wadze, bo miał więcej losowań z tego samego
rozkładu. Kafel mierzyłby wtedy sumienność prowadzenia dziennika, nie postęp — a to zachęta do
ważenia się częściej zamiast do trenowania.

**Okno 90 dni jest stałe i nie chodzi za przełącznikiem zakresu wykresu.** Etykieta mówi „3 mies."
i ma zostać prawdziwa, gdy ktoś przestawi wykres na rok — ta sama zasada, według której limit
uzupełniania wstecz jest polityką, a nie oglądanym zakresem. Konsekwencja siedzi w warstwie odczytu:
`AthleteWeightService.series` czyta co najmniej `LOWEST_TREND_WINDOW_DAYS + (TREND_WINDOW_DAYS - 1)`
= 96 dni wstecz **niezależnie od zakresu**, bo najstarszy dzień okna potrzebuje jeszcze własnego
ogona na średnią kroczącą. Dziś nie zmienia to niczego (najkrótszy zakres to 120 dni), ale krótszy
zakres dodany kiedyś zwęziłby po cichu okno, którym kafel się podpisuje, i nikt nie połączyłby tego
z tą zmianą. Rozszerzenie liczone jako `min(...)`, nie arytmetyką — `Range.ALL` to data-wartownik
`1900-01-01`.

**Remis rozstrzyga dzień najpóźniejszy.** Powrót do swojego minimum to informacja („jestem tam
znowu"); bycie tam kiedyś nią nie jest. Porównanie jest jawne, nie oparte na kolejności iteracji —
`index()` daje `TreeMap`, ale metoda przyjmuje dowolną mapę.

**Brak potwierdzonej wartości = cała linijka znika**, nie „—" ani „brak danych". Kreska przy
statystyce rekordowej czyta się jak zero albo jak awaria, a najlepsza z niepotwierdzonych wartości
podpisana słowem „trend" byłaby dokładnie tym kłamstwem, przed którym broni cały moduł wagi.
Serwer przysyła wartość już przefiltrowaną albo `null`; klient sprawdza wyłącznie `null` i nigdy
nie liczy potwierdzenia po swojej stronie. Liczba miesięcy w etykiecie idzie z DTO
(`lowestTrendWindowDays`), żeby nie mieszkać w tłumaczeniu.

---

## Biblioteka filmów (V36)

DROP `exercise_videos.category` — pole tekstowe bez podpowiedzi rozjeżdżało bibliotekę na
„nogi"/„Nogi"/„nogi/pośladki"; treść wtopiona w `name` (i przeliczony `search_text`), nazwa
niesie całe znaczenie.

Do tego nazwa filmu **uzupełnia się sama z tytułu YouTube** (publiczny oEmbed, bez klucza;
request budowany z **sparsowanego `video_key`**, nigdy z wklejonego stringa — inaczej to SSRF
z uprzejmą twarzą) i tylko do pustego pola.

---

## Zdjęcia w komentarzach (V39)

`training_comments` + `photo_filename/width/height/expires_at`, `body` nullable + CHECK
`body IS NOT NULL OR photo_filename IS NOT NULL`.

### Dlaczego limit dzienny wisi na podopiecznym, a nie na wrzucającym

Limit „3 na trening" wygląda jak sufit i nim nie jest — treningów można założyć dowolnie wiele, a
każdy otwiera trzy kolejne miejsca. Zdjęcia leżą na tym samym dysku co baza, więc folder bez sufitu
kończy się Postgresem odmawiającym zapisu, a nie komunikatem o braku miejsca na zdjęcia.

Pierwszy odruch — „X zdjęć dziennie na osobę" — jest złego kształtu i wywraca się na trenerze:
**trener wrzuca do wielu kalendarzy w jednym posiedzeniu**. Dwudziestu podopiecznych po trzy zdjęcia
to sześćdziesiąt, czyli limit na konto zatrzymałby go na siódmej osobie, nie dotknąwszy przy tym
żadnego podopiecznego. Licznik siedzi więc na **kalendarzu podopiecznego**: ta sama sesja zostawia
każdego na 3 z 25, a rosnące dane mają sufit tam, gdzie faktycznie rosną. Całość jest wtedy
ograniczona przez `liczba podopiecznych × 25 × 30 dni retencji`, a liczbę podopiecznych ustala trener.

Zdjęcia wrzucone przez trenera **liczą się do dnia podopiecznego**: plik zajmuje to samo miejsce
niezależnie od tego, kto go wysłał, a limit, który da się obejść drugim kontem, nie jest limitem.

Doba jest **kalendarzowa**, nie ruchoma. Obie tak samo ograniczają dysk w średniej, ale tylko jedną
da się wytłumaczyć osobie stojącej na sali. Liczone są wiersze **istniejące**, więc skasowanie
zdjęcia zwalnia miejsce w limicie — cykl „wrzuć i skasuj" zostawia dysk tam, gdzie był, więc nie ma
tu czego bronić ani drugiej tabeli do utrzymywania.

### Dlaczego kolumna na komentarzu, a nie tabela

Kuszące jest `training_photos` z własnym kluczem — wygląda porządniej i od razu daje wiele zdjęć
na komentarz. Kosztuje jednak dokładnie tam, gdzie ten moduł psuje się po cichu:
`TrainingUnreadService` czyta z **siedmiu** źródeł, a zapomniane źródło nie sypie błędem, tylko
przestaje kogokolwiek powiadamiać. Zdjęcie na wierszu komentarza dziedziczy kropki, badge rostera
i licznik per kafelek za darmo — osobna tabela byłaby ósmym źródłem do dopisania w trzech
metodach, i to takim, którego brak zauważyłby dopiero trener, do którego nie dotarło zdjęcie.

Do `training_attachments` też nie pasuje: to materiały **trenera** (filmy, linki), kopiowane
przez `duplicate`/`paste`. Zrzut zawodnika nie ma podróżować z przeklejonym planem, a
`chk_ta_owner` i tak zamyka właściciela na `training_id` XOR `template_id` — `comment_id`
oznaczałby przepisanie tego CHECK-a i narzucenie komentarzom limitu trzech pozycji, który
w tamtej tabeli znaczy co innego.

Limit **3 na trening** (nie na komentarz) wynika z tego, jak wygląda sesja z zegarka:
podsumowanie, strefy, splity.

### Dlaczego wyłącznie JPEG i zawsze przekodowanie

`StorePolicy.TRAINING_PHOTO` odrzuca PNG i WebP, choć reszta serwisu je przyjmuje. Powód jest
jeden: **JDK nie ma czytnika WebP**, więc WebP ląduje na dysku niezdekodowany i jedyną kontrolą
jest jego sygnatura. Dla zdjęcia z galerii to akceptowalny kompromis; dla danych zdrowotnych nie.
JPEG serwer dekoduje i koduje **sam**, co daje trzy rzeczy niezależne od tego, co przysłał
klient: prawdziwe wymiary (a nie deklarowane), zdjęty **cały EXIF wraz z GPS**, i pewność, że
serwowany `Content-Type` opisuje faktyczne bajty. `forceReencode` jest tu konieczne — bez niego
mały plik przeszedłby w oryginale, czyli razem ze swoimi metadanymi.

Przeglądarka i tak konwertuje wszystko przez canvas (1280 px, q0.75, ~110 KB), więc podopieczny
nadal wybiera z telefonu PNG, WebP czy HEIC. Ta sama konwersja zdejmuje EXIF **jeszcze przed
wysłaniem**, więc lokalizacja nie opuszcza urządzenia.

Cały łańcuch kontroli został **sparametryzowany, nie skopiowany**: `StorePolicy` to argument
`LocalFileStorageService.storeImage`, nie druga ścieżka. Druga implementacja tych samych pięciu
sprawdzeń byłaby drugim miejscem do utrzymania w zgodzie — a ta, która by odstała, byłaby tą,
w którą nikt nie patrzy.

### Limit pikseli przed dekodowaniem

Przy okazji domknięta dziura, która istniała już wcześniej na avatarach: `ImageIO.read` wołane
bez sprawdzenia wymiarów alokuje ~4 bajty na piksel, więc 1,5 MB JPEG opisujący 10000×10000
zjada ~400 MB w kontenerze z `mem_limit: 384m`. Wymiary czyta się teraz **z nagłówka**
(`ImageIO.getImageReaders`), przed dotknięciem pikseli, i odrzuca powyżej 40 MPx.

### Dlaczego `private, no-store`, a nie publiczny cache

`/api/files` oddaje pliki bez logowania, z `max-age=7d, public` — i przyjmował **dowolny** folder
pasujący do `^[a-z]+$`, więc każdy katalog pod `uploads/` był światowo czytelny, gdy tylko
wyciekła nazwa pliku. Stąd biała lista `PUBLIC_FOLDERS` (nowy folder jest odtąd domyślnie
prywatny) i osobne, uwierzytelnione endpointy dla zdjęć, przechodzące przez
`TrainingAccessService` — czyli z tą samą dyscypliną **404 zamiast 403** co reszta modułu.

`no-store`, bo dane zdrowotne nie mają prawa osiąść w dyskowym cache przeglądarki ani u
pośrednika. Użytkownik nic na tym nie traci: front trzyma `Blob` w pamięci React Query na czas
sesji, więc ponowne otwarcie modala nie schodzi po sieci. Konsekwencja dla frontu: zdjęcie **nie
może być zwykłym `<img src>`**, bo ten nie niesie nagłówka `Authorization` — stąd pobranie bajtów
i `URL.createObjectURL`. Cache trzyma **Bloba**, nigdy gotowego object URL-a: dwa dymki z tym
samym zdjęciem dzieliłyby jeden URL, a pierwszy odmontowany unieważniłby go drugiemu.

### Retencja 30 dni i zamiatarka osieroconych

Zrzut z zegarka odpowiada na pytanie „jak poszła ta sesja" i traci wartość, gdy odpowiedź została
przeczytana. Krótka retencja jest więc uczciwa merytorycznie, a przy okazji jest **jedyną**
odpowiedzią na to, że katalog danych zdrowotnych nie rośnie latami. `photo_expires_at` jest
**zapisane, nie liczone**: front pokazuje realną datę zamiast odtwarzać ją ze stałej, a zmiana
okna nie przepisuje losu istniejących wierszy. Kasowane jest samo zdjęcie — komentarz „nogi
ciężkie" jest wart trzymania i rok później; komentarz będący **wyłącznie** zdjęciem znika w
całości, bo nie zostaje w nim nic do przeczytania (i CHECK i tak by go nie przyjął).

Drugi przebieg schedulera — **usuwanie plików, do których nie ma wiersza** — jest tym, który
odpowiada audytowi. `PersonalTraining` nie ma kaskady JPA, więc komentarze znikają przez
`ON DELETE CASCADE`, Hibernate ich nie ładuje i **żaden callback nie ma jak sięgnąć po pliki**;
stąd trzy jawne `purge*`. Każdy z nich siedzi jednak w transakcji, która może się wycofać, a
`LocalFileStorageService.delete` błędy tylko loguje. Bez zamiatarki jeden zgubiony `delete` to
trwały wyciek; z nią — plik żyje najwyżej do rana. Pliki młodsze niż godzina są pomijane, bo
upload między odczytem wierszy a listingiem katalogu wyglądałby jak sierota, a skasowanie zdjęcia
tuż po wysłaniu jest znacznie gorsze niż zamiecenie go dobę później.

### Zdjęcie flagi `is_athlete` nic nie kasuje

Świadomie spójne z V29/V38: dane wracają po ponownym włączeniu, dostęp odcina
`TrainingAccessService`, a zdjęcia i tak wygasają w ≤30 dni. To decyzja, nie przeoczenie —
pilnuje jej test.

### Usuwanie zdjęcia: jedyny wyłom w „komentarze są tylko do dopisywania"

Komentarze nie mają i nie będą miały endpointu usuwania — wątek jest zapisem rozmowy. Zdjęcie
ma, bo zrzut ekranu potrafi pokazać więcej, niż autor zamierzał, a prawo do wycofania danych
zdrowotnych musi mieć techniczną drogę realizacji. Kasuje **autor swojego** albo **trener
dowolnego** w wątku swojego podopiecznego (to on odpowiada za to, co klub trzyma); podopieczny
nie rusza zdjęcia trenera. Tekst komentarza przeżywa usunięcie obrazu.

### Zgoda: poszerzenie zakresu to nowa zgoda, nie dopisek

V38 wymienia wagę, trend, cele wagowe, limity kalorii, RPE i komentarze — nie zdjęcia. Zgoda
udzielona pod tamtym tekstem nie obejmuje nowego zakresu art. 9, więc migracja **zeruje
`training_consent_at` wszystkim** i każdy podopieczny przechodzi ekran zgody raz jeszcze, już
z nową treścią. Kasowany jest wyłącznie dowód zgody — żadne dane. Dokładając cokolwiek do
`consent.items`, powtórz ten ruch.

### Osobny prefiks uploadu

`POST /api/user/my-training/photos` i `POST /api/admin/training-photos` leżą **poza** prefiksami
komentarzy nie z powodów estetycznych: `RateLimitFilter` rozstrzyga kubełek **wyłącznie po
prefiksie ścieżki**, więc upload pod prefiksem kalendarza dziedziczyłby limit 120/min. Własny
prefiks pozwala postawić regułę uploadu **na początku `RULES`**, przed kalendarzem i panelem
(pierwsza pasująca wygrywa), i dać uploadom 12/min. Kubełek racjonuje **bajty, nie żądania** — multipart jest
parsowany do pamięci, zanim handler zdąży cokolwiek odrzucić. **Odczyt zdjęć celowo w nim nie
siedzi**: otwarcie kilku treningów pod rząd to kilkanaście GET-ów i nie ma powodu ich reglamentować.
