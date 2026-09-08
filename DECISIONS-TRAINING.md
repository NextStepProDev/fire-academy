# Decyzje projektowe — treningi (V24–V42) + niezmienniki kalendarza 1:1

Uzasadnienia migracji od V24 w górę: **dlaczego** schemat wygląda tak, a nie inaczej.
Wyprowadzone z `CLAUDE.md`, żeby nie obciążać kontekstu każdej sesji — `MIGRATIONS.md` ma
jednolinijkowe „co dodaje", tutaj leży pełne „dlaczego". Plik siedzi w roocie, a nie w `docs/`,
bo `docs/` jest w `.gitignore` — te uzasadnienia mają jechać razem z repo.

Czytaj przed zmianą w danym obszarze. Reguły, które łatwo złamać **każdą** zmianą w
kalendarzu 1:1, leżą w tym samym pliku — sekcje „Kalendarz treningów 1:1 — pułapki"
i „Prywatne notatki właściciela — niezmienniki" na końcu. `CLAUDE.md` trzyma z nich
jednolinijkowy indeks.

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

---

## Kalendarz treningów 1:1 — pułapki (zasady, które łatwo złamać po cichu)

> Przeniesione z `CLAUDE.md` (2026-09-08).

Zasady, które łatwo po cichu złamać przy kolejnej zmianie. Każda ma test, który to wyłapie.

**Nakładka cykliczna NIGDY nie jest materializowana.** Sesje grupowe na kalendarzu 1:1 liczy `RecurringSessionOverlayService` przy każdym żądaniu, z tego samego kodu co rachunek (`TrainingBillingService`). Zapisanie ich jako wierszy w `personal_trainings` wygląda prościej przez jedno popołudnie i kosztuje na zawsze: dzień wolny, odwołane zajęcia, rezygnacja w połowie miesiąca, dezaktywacja slotu od 15. — każde z nich musiałoby polować na wygenerowane wiersze, a każde pudło to kalendarz niezgodny z rachunkiem. `RecurringOverlayIntegrationTest` sprawdza, że po pobraniu strony `SELECT count(*) FROM personal_trainings` = 0.

**Koszt nakładki to 3 zapytania niezależnie od zakresu.** API zakresowe w `TrainingBillingService` (`closedDatesInRange` wsadowe, czyste `sessionDatesInRange`, `addDeactivationDates`) istnieje właśnie po to. Test porównuje liczbę zapytań dla tygodnia i dla 6-tygodniowej siatki — regresja do pobierania per miesiąc wywala build.

**Ekrany rozliczeniowe pobierają dni zamknięte RAZ na stronę, nie raz na osobę.** `sessions`/`amount`/`firstSessionDate`/`isPaymentOverdue`/`partialStartDate` mają po dwa warianty: bez `closed` (wygodny dla jednej subskrypcji, kosztuje 2 zapytania) i z `closed` (dla listy). Roster slotu i miesięczny przegląd płatności liczą `closedBySlotForMonth(...)` raz i przekazują wynik dalej — dni wolne i odwołania zależą od slotu i miesiąca, **nigdy od osoby**, więc wersja per-wiersz mnożyła te same dwa zapytania przez liczbę uczestników. Tak samo `creditService.availableBalance` liczy się raz na wiersz i wchodzi do `liveAppliedFor(te, month, balance)`, a `netFor` dostaje gotowy wiersz płatności zamiast go dociągać. Zmierzone na rosterze: **9 → 2 zapytania na każdego kolejnego uczestnika** (strona dla 3 osób: 31 → 12). Zostające 2 to saldo nadpłaty, które faktycznie jest per osoba.

**Ekran „Moje treningi" (`/moje-konto/treningi`) liczy tak samo.** `getMyEnrollments` pobiera dni zamknięte **raz na stronę** dla zbioru potrzebnych miesięcy (miesiąc rozliczeniowy każdego wiersza + jego podgląd następnego), a saldo nadpłaty i wiersz płatności czyta raz i przekazuje dalej zamiast pozwalać, by `creditService` dociągnął je sobie ponownie. Wcześniej jedna linijka rachunku kosztowała dziewięć zapytań.

**Ta sama reguła dotyczy dwóch archiwów**, które rosną w nieskończoność i zwracają wszystko naraz: przegląd odwołanych zajęć (`getCancelledOverview`, 5 → 1 zapytanie na wiersz) i archiwum usuniętych slotów (`getDeletedSlots`, 1 → 0). Uczestnicy, ich płatności i nierozliczone zwroty zależą od `(slot, miesiąc)` albo od niczego — pobiera się je raz na stronę. Zostające 1 w przeglądzie to `isSessionRestorable`: decyzja wymaga wierszy zwrotów tej konkretnej sesji, a wciągnięcie tego do wsadu oznaczałoby przebudowę silnika zwrotów — świadomie zostawione.

**Rejestracja zwrotów też pyta raz na grupę, nie raz na osobę.** `registerForSlotSession` przechodzi uczestników slotu dla **jednej daty i jednego miesiąca**, więc wszystkie trzy pytania per osoba — czy zwrot za tę datę już istnieje, ile zebrała płatność za ten miesiąc, ile już zwrócono — zależą od `(subskrypcja, miesiąc)` albo `(subskrypcja, data)` i pobiera się je **raz dla całej grupy**. Wcześniej kosztowały 3 odczyty na opłaconego uczestnika, a to mnoży się dalej: `registerForHoliday` woła tę metodę **raz na slot** danego dnia tygodnia, a `cancelInstructorDay` tak samo. Zmierzone: **4 → 1 zapytanie na dodatkowego opłaconego uczestnika**, przy czym to jedno to sam `INSERT` wiersza zwrotu. Pilnuje `RefundRegistrationQueryCountIntegrationTest` z budżetem **1 i zerowym zapasem** — przy 2 test przechodził z jednym z trzech odczytów przywróconym. ⚠️ Kluczem paid/unpaid jest teraz obecność wiersza w `findPaidForMonth` (te same predykaty co `findPaidEnrollmentIds`, ale zwraca encje), więc jedno zapytanie odpowiada na „czy opłacone" **i** daje kwotę do sufitu zwrotów. **Nie dotyczy `registerForEnrollmentRemoval`** — tam pętla to miesiące × sesje **jednej osoby**, czyli rzadka akcja admina o ograniczonym rozmiarze; zostawione świadomie, bo zmiana w silniku finansowym bez pomiaru, który ją uzasadnia, to zły interes.

`TrainingBillingQueryCountIntegrationTest` liczy **przyrost zapytań na wiersz** i ma **osobny budżet dla każdego z czterech ekranów** (3/3/1/0), każdy równy pomiarowi plus najwyżej jedno zapytanie zapasu. Jeden wspólny próg nie działał: ustawiony pod najdroższy ekran przepuszczał regresję na najtańszym — sprawdzone przez cofnięcie poprawki. Zmieniając te liczby, zweryfikuj w obie strony: że test przechodzi z poprawką **i pada bez niej**.

**Zadanie i trening to dwa wiersze, nigdy jeden.** Kuszące jest dopięcie „limitu kcal" jako pola do treningu — jedno okno, jedno odhaczenie. Wtedy dzień, w którym trening wyszedł, a dieta nie, nie ma jak się zapisać: cokolwiek pokaże checkbox, będzie kłamstwem o połowie dnia. Stąd `kind` na wierszu, dwa kafelki w dniu i dwa niezależne odhaczenia. `kind` jest ustawiany przy tworzeniu i nie ma go w `UpdateTrainingRequest` — przełączenie odhaczonego treningu na zadanie musiałoby po cichu skasować RPE, żeby wiersz przeszedł CHECK-i. Zadanie odhacza się bez RPE (walidacja zależy od wiersza, więc siedzi w serwisie, nie w adnotacji), a statystyki treningowe zadań nie widzą — mają własny blok `tasks`.

**Plan trenera jest dla podopiecznego tylko do odczytu — usuwanie też.** Recepta jest istotą prowadzenia: plan, który podopieczny może po cichu przepisać, przestaje nim być — trener czyta wtedy własne polecenia, zmienione, bez żadnego sygnału. Usuwanie siedzi w tej samej blokadzie, bo „skasuj i dodaj po swojemu" omija zakaz edycji. Zostaje odhaczanie (RPE + notatka) i komentarze — to akty, dla których plan istnieje. Blokada wisi na `created_by_admin`, ustawianym raz przy tworzeniu; `last_modified_by_admin` przeskakuje przy każdym odhaczeniu i nie nadaje się na właściciela wpisu. W paście kolejność jest istotna: **najpierw rozstrzygnięcie adresata (404 za cudzy kalendarz), dopiero potem 409** — inaczej odpowiedź zdradziłaby, że źródło istnieje. Front chowa przyciski (`canReshapeTraining` w `adapter.ts`, decyzja per kafelek, nie per rola) i pisze dlaczego — sam ubytek przycisków wygląda jak zepsuta karta.

**Wklejenie trafia tam, gdzie patrzy trener — nigdy „do właściciela źródła".** Schowek celowo przeżywa zmianę podopiecznego, więc `sourceId` sam w sobie nie mówi, gdzie ma wylądować wpis; bez `targetAthleteId` serwer zgadywał podopiecznego źródła i każde wklejenie po przełączeniu osoby cicho lądowało u poprzedniej (błąd zgłoszony 2026-08-04). Trener wysyła id kalendarza z ekranu, podopieczny może wskazać tylko siebie (inaczej 404, jak wszędzie w tym module). **Przeniesienie (MOVE) między osobami to kopia + usunięcie oryginału**, nie przepięcie wiersza: odhaczenie, RPE i wątek komentarzy to dane zdrowotne jednej osoby i nie mogą wypłynąć pod cudzym nazwiskiem — a strona źródłowa dostaje zwykłe powiadomienie o usunięciu.

**Kopia treningu zabiera materiały.** `duplicate` i `paste`-COPY przepisują załączniki (`AttachmentService.copyBetweenTrainings`); VIDEO wskazuje ten sam wiersz biblioteki, LINK jest duplikowany. Wcześniej kopia przychodziła jako sam tytuł — a filmy są treścią planu.

**Kontrakt `attachments`: `null` = nie ruszaj · `[]` = wyczyść · lista = zamień.** Przesunięcie treningu wysyła cały obiekt; potraktowanie braku listy jako „wyczyść" po cichu gubi materiały, których edycja nie dotykała.

**Materiały ustawia wyłącznie trener — zapis podopiecznego pole `attachments` IGNORUJE, nie odrzuca.** Biblioteka jest zasobem trenera, a podopieczny nie ma dla niej żadnego ekranu (ani wyboru filmu, ani pola na link), więc reguła siedziała **tylko w formularzu** — żądanie zbudowane ręcznie przechodziło, bo `AttachmentService` szuka filmu po `id` i nie pyta, kto woła. Nic tą drogą nie wycieka (podopieczny może wskazać wyłącznie film, który trener już mu pokazał — nie ma po swojej stronie ani listy, ani wyszukiwarki), ale **film raz przez niego podpięty przestaje dać się usunąć z biblioteki**: kasowanie jest odmawiane dla czegokolwiek w użyciu, a trener nie widzi, gdzie to użycie siedzi. **Ignorowanie zamiast błędu** jest tu istotne: formularz zawsze wysyła pełną listę, także z ukrytą sekcją, więc podopieczny przesuwający trening, do którego trener dopiął film, odsyła jego `id` z powrotem — błąd wywaliłby edycję niemającą z materiałami nic wspólnego. `null` znaczy „nie ruszaj" i pasuje do obu kształtów: nowy wpis podopiecznego i tak nie ma materiałów, a istniejący zachowuje to, co dał trener. Pusta lista od podopiecznego też niczego nie czyści.

**Zdjęcie wisi na komentarzu — kolumna, nie tabela.** To nie oszczędność, tylko sposób na jedyny cichy tryb awarii w tym module: `TrainingUnreadService` liczy z 7 źródeł, a zapomniane źródło nie sypie błędem, tylko przestaje kogokolwiek powiadamiać. Zdjęcie na wierszu komentarza dziedziczy kropki, badge rostera i licznik per kafelek **bez ani jednej linijki** w tym serwisie; osobna tabela byłaby ósmym źródłem do dopisania w trzech metodach. Do `training_attachments` też nie pasuje — to materiały **trenera**, kopiowane przez `duplicate`/`paste`, a zrzut zawodnika nie ma podróżować z planem.

**Pliki przeżywają wiersze, jeśli ktoś ich jawnie nie skasuje.** `PersonalTraining` nie ma kaskady JPA — komentarze znikają przez `ON DELETE CASCADE`, więc Hibernate nigdy ich nie ładuje i **żaden callback nie zadziała**. Stąd trzy jawne wywołania (`purgeForTraining` przed `repository.delete`, `purgeForUser` w obu ścieżkach usuwania konta) plus czwarta decyzja: **zdjęcie flagi `is_athlete` nic nie kasuje**, zgodnie z doktryną V29/V38. Siatką pod tym wszystkim jest nocne zamiatanie osieroconych plików — bo każdy jawny unlink siedzi w transakcji, która może się wycofać, a `LocalFileStorageService.delete` błędy tylko loguje. Bez zamiatarki jeden zgubiony `delete` to trwały wyciek danych zdrowotnych; z nią — plik żyje najwyżej do rana. Zamiatarka omija pliki młodsze niż godzina (zapis między odczytem wierszy a listingiem katalogu wyglądałby jak sierota).

**Metoda `@Scheduled` nigdy nie woła `@Transactional` z tej samej klasy.** Transakcję zakłada proxy Springa wokół beana; wywołanie w obrębie obiektu idzie na `this`, proxy nie dotyka, więc adnotacja **nic nie robi i nic o tym nie mówi**. Nie da się tego złapać ani testem, ani po zachowaniu: każde wywołanie repozytorium ma własną transakcję, robota się wykonuje, znika tylko atomowość partii. Nie wyłapie tego też test schedulera napisany normalnie — wstrzyknięty bean to **proxy**, czyli jedyna ścieżka, na której adnotacja działa; produkcja idzie inną. Stąd reguła konstrukcyjna: scheduler trzyma harmonogram, przebieg mieszka w osobnym beanie (`TrainingPhotoRetentionScheduler` → `TrainingPhotoRetentionService`), a wtedy ścieżka omijająca proxy po prostu nie istnieje. `@Transactional` **na samej metodzie `@Scheduled`** jest poprawne (woła ją infrastruktura, przez proxy) — tak mają `TokenCleanupScheduler` i `TrainingSubscriptionExpiryScheduler`. `TrainingPhotoTransactionIntegrationTest` asercjuje aktywną transakcję **w środku przebiegu**, osobno dla wejścia przez `sweep()` i przez bean — bo tylko to odróżnia obie ścieżki. Reguły pilnuje `architecture/SchedulerTransactionArchTest`: **czyta źródła** (nie zachowanie — zachowanie tego błędu nie zdradza) i wywala build, gdy klasa ze `@Scheduled` deklaruje metodę `@Transactional`, która **nie jest** samą metodą `@Scheduled`. Bramka jest celowo wąska: samowywołanie metody transakcyjnej jest **poprawne**, gdy wołający sam ma transakcję, i tak jest w 8 miejscach w `TrainingBillingService`/`TrainingCreditService`/`TrainingUnreadService`/`TrainingRefundService` — szersza reguła krzyczałaby na działający kod i skończyłaby wyciszona.

**Liczniki nieprzeczytanych: 7 źródeł, spisane z góry** (`TrainingUnreadService`). Tryb awarii jest cichy — zapomniane źródło po prostu nikogo nie powiadamia. Klucz to `updated_at` + flaga autorstwa, **nigdy `completed_at`**: cofnięcie wykonania zeruje tę kolumnę, a trener i tak musi się o tym dowiedzieć. `complete()`/`uncomplete()` **muszą** zerować `lastModifiedByAdmin`, inaczej podopieczny zapala sobie własną kropkę.

**Bycie na bieżąco ma DWIE krawędzie: „od kiedy" i „dokąd" (V41).** Kalendarz czyta się stroną po stronie, więc sama godzina nie wystarcza — otwarcie tego tygodnia nie mówi nic o przyszłym miesiącu, a wcześniej stemplowało jako przeczytany cały plan. Kapitan rozpisywał komuś miesiąc, plakietka pokazywała 12, podopieczny wchodził w plan, plakietka gasła i **żadna kropka nigdy się nie zapaliła** — też po przejściu strzałką na tamten miesiąc. Reguła: źródło jest nieprzeczytane, gdy druga strona ruszyła je po ostatniej wizycie **albo** gdy leży za dniem, do którego czytelnik doszedł. Konsekwencje, które łatwo złamać:
- **Predykat żyje w sześciu miejscach i muszą jechać razem** (`countTouchedSince`, `countSince`, `findTrainingIdsWithNewComments`, `countUnreadTrainings`, `countUnreadComments`, `PersonalTrainingService.isUnread`). Liczba obiecuje coś do znalezienia, kropki są tym, po czym się to znajduje — rozjazd między nimi jest dokładnie tym błędem, który V41 naprawia.
- **Usunięcia są wyjątkiem, świadomie.** Docierają różowym paskiem, który nie jest ograniczony stroną (`findPending` nie ma zakresu) i ma osobne odhaczenie. Dołożenie im `seen_through` liczyłoby to samo dwa razy.
- **Zamiast `:param IS NULL` w zapytaniu jest wartownik daty** (`SeenMarker.reach()` → `0001-01-01`). Postgres nie ma z czego wywnioskować typu takiego bindu i zapytanie **wywala się w całości**; wartownik załatwia to bez gałęzi, bo każda prawdziwa data treningu jest po nim.
- **`to` w `mark-seen` jest WYMAGANE.** Bez okna wołanie nie ma treści — „patrzyłem" to właśnie ta obietnica, która gasiła nieobejrzany miesiąc. Starszy bundle dostaje 400, więc kropki zostają na tę wizytę (bezpieczny kierunek, sam się naprawia przy przeładowaniu); przyjęcie wołania i ciche zaliczenie wszystkiego traci powiadomienie na zawsze.
- **Front raportuje raz na OKNO i na OSOBĘ**, nie raz na montowanie (`seenRanges` w `TrainingCalendar`). Pojedyncza flaga zgłosiłaby pierwszą stronę i zamilkła, a Kapitan przełącza podopiecznych bez odmontowania komponentu — klucz bez `athleteId` uznałby stronę drugiej osoby za już zgłoszoną.
- **Nowy cel ma własne oznaczenie** (`GoalResponse.unread`), bo cel był jedynym z siedmiu źródeł bez czegokolwiek na ekranie. Liczone z tego samego znacznika co kalendarz — tablica celów stoi na tym samym ekranie, więc drugie pojęcie „widziane" musiałoby mieć własny sposób gaszenia. Pole **nie istnieje** w JSON Kapitana (nie jest `false`), jak `overtraining`.

**Mark-seen dopiero gdy strona naprawdę dotarła** (`isSuccess && !isFetching`). Przy powrocie z cache React Query zgłasza sukces w tym samym ticku, a oznaczenie „widziane" przed policzeniem kropek przez serwer gasi je, zanim ktokolwiek je zobaczy. Po oznaczeniu invalidacja z `refetchType: 'none'` — kropki zostają na tę wizytę.

**Zapytania o liczniki nadpisują globalny cache — ale nie do zera.** Domyślny `staleTime` to 5 minut, na badge za dużo; badge ma więc `SHORT_STALE_MS` (30 s, `utils/queryFreshness.ts`) + `refetchOnWindowFocus: true`, a treść kalendarza dodatkowo `refetchOnMount: 'always'` + `placeholderData: undefined` (zmiana podopiecznego to inna encja, nie świeższe dane tej samej). **`staleTime: 0` jest tu błędem**, choć wygląda na najbezpieczniejszy wybór: przy domyślnym `refetchOnWindowFocus` każdy powrót na kartę odpalał wszystkie zamontowane zapytania naraz, a dwa ekrany admina montują **jedno zapytanie na wiersz** (`AdminEvents`, `AdminArchive` — lista 15 terminów to 15 żądań na focus, przy kubełku `admin` 60/min). Zera nie potrzeba, bo **`staleTime` nie blokuje `invalidateQueries`** — własna mutacja i tak odświeża swoją listę natychmiast; `refetchOnMount: 'always'` zostaje, bo montowanie to świadoma nawigacja, a focus nie. Jedyne zostawione `staleTime: 0` to `VideoPickerModal` (klucz per fraza, a trafienie w cache znaczy brak świeżo dodanego filmu = zła odpowiedź, nie stara).

**Formularze czekają na potwierdzenie zapisu** (`await`, nie fire-and-forget) i pokazują błąd **inline przy przycisku**. Zwinięcie formularza przed odpowiedzią serwera mówi użytkownikowi, że zapisał coś, co się nie zapisało.

**`fetchApi` ponawia TYLKO `GET`/`HEAD`.** Ponowienie zapisu jest bezpieczne dopiero wtedy, gdy serwer umie wykryć powtórkę — a kalendarz 1:1 celowo nie umie, bo dwa identyczne treningi jednego dnia to legalny plan. Żądanie może dojść, zostać wykonane i zgubić odpowiedź (timeout 30 s, restart backendu w trakcie deployu); ponowione tworzy drugi wpis. Odczyty ponawiamy dalej — to właśnie one zamieniają redeploy w chwilowy komunikat „aktualizacja serwisu" zamiast ekranu błędu. Pilnuje `src/api/client.test.ts`.

**Modale dzielą jeden stos** (`modalStack` w `Modal.tsx`). Zagnieżdżenie jest normą (potwierdzenie usunięcia nad szczegółami treningu, wybór filmu nad formularzem), a każdy modal ma własny nasłuch `keydown` na `document`. Bez stosu zamknięcie wewnętrznego oddawało stronę scrollowaniu spod wciąż otwartego zewnętrznego, a jeden Escape zamykał oba naraz. Reguła: `overflow` wraca dopiero przy pustym stosie, a na klawiaturę reaguje wyłącznie modal na wierzchu (również pułapka focusa — `ConfirmDialog` jest **rodzeństwem**, nie dzieckiem modala, który go otworzył). Pilnuje `Modal.test.tsx`.

**Liczby „łącznie" i „pierwsza aktywność" idą z bazy, nie z okna 12 miesięcy.** Reszta panelu statystyk (mapa cieplna, serie, średnie, frekwencja) liczy się z `findRange(athleteId, yearAgo, today)`. Wyprowadzone z tej samej listy liczby dożywotnie psują się po cichu: po roku „łącznie" przestaje rosnąć (stare treningi wypadają tak samo szybko, jak dochodzą nowe), a data pierwszego treningu pełznie do przodu. Stąd `countCompleted` / `findFirstCompletedDate` w repozytorium. **`byType` zostaje okienkowe** — jest zestawione z rocznym licznikiem zajęć grupowych, więc wartość dożywotnia porównywałaby dwa różne okresy. `bestStreakWeeks` też widzi tylko rok i tak jest opisane w Javadocu.

**Brak siatki godzinowej — świadomie.** Kalendarz to kolumny dni z kafelkami; godzina jest opcjonalna i jej brak to przypadek domyślny, więc trening bez godziny **nie renderuje żadnej etykiety czasu** (ani myślnika, ani „cały dzień"). Test asercjuje, że w drzewie nie ma osi godzinowej.

**Enum zapisany jako tekst nie sortuje się po kolejności deklaracji.** `ORDER BY horizon` w SQL dało LONG, MEDIUM, SHORT zamiast SHORT, MEDIUM, LONG — sortowanie celów siedzi w serwisie, po `Comparator.comparing(AthleteGoal::getHorizon)`.

**Sygnał przetrenowania widzi tylko trener.** Pole `overtraining` **nie istnieje** w JSON podopiecznego (nie jest `false`). Strona mówiąca komuś „przetrenowujesz się" zamienia zaczątek rozmowy w wyrok.

**Filmy „prywatne" na YouTube się nie osadzają** — osadzają się tylko „niepubliczne" (unlisted). Formularz dodawania pokazuje podgląd od razu po wklejeniu linku właśnie po to, żeby Kapitan zobaczył pusty odtwarzacz od razu, a nie przez podopiecznego tydzień później.

**Testy nakładki używają NASTĘPNEGO miesiąca.** Subskrypcja utworzona dziś jest prorowana od dzisiejszego dnia miesiąca, więc sesje wcześniejsze w bieżącym miesiącu poprawnie wypadają — pierwsze podejście wyglądało jak „nakładka nie działa", a była to działająca proracja.

---

---

---

## Prywatne notatki właściciela — niezmienniki

Notatnik trenera: notatkę widzi **wyłącznie jej autor** — nie kursant, nie podopieczny, nie drugi
admin. Cztery cele: trening 1:1, pojedyncze zajęcia cykliczne w kalendarzu konkretnej osoby, slot
tygodniowy jako całość, termin obozu/szkolenia.

**Notatka NIGDY nie jedzie w istniejącym DTO — i to jest cała ochrona, nie `@PreAuthorize`.**
Ryzykiem nie jest brakująca bramka roli, tylko uczynne pole. `CalendarRangeResponse` (z
`PersonalTrainingResponse` **i** `RecurringSession`) to **jeden rekord serwowany obu rolom** —
trenerowi z `/api/admin/...` i podopiecznemu z `/api/user/my-training/calendar` — a listingi
publiczne są cache'owane na brzegu. Pole dodane tam skompiluje się, będzie wyglądać na wygodę
i opublikuje notatnik ludziom, o których jest pisany. Dlatego notatki serwuje własny endpoint per
cel, a **typ notatki jest nieosiągalny poza `domain/adminnote` i `api/admin/note`** — serwis, który
nie umie notatki przeczytać, nie umie jej wypuścić. Pilnują tego **dwa testy patrzące z przeciwnych
stron**: `architecture/AdminNoteIsolationArchTest` (zasięg typu) i `AdminNoteLeakIntegrationTest`
(prawdziwy kalendarz obu ról, asercja na **zserializowanym JSON-ie**, nie na komponentach rekordu —
pole dopisane później pojedzie do przeglądarki niezależnie od tego, czy ktoś pamiętał o teście).
Ten drugi **najpierw asercjuje, że notatka w bazie w ogóle jest**; bez tego przechodzi też wtedy,
gdy fixture cicho nic nie zapisał, czyli jest nieodróżnialny od testu, który nic nie sprawdza.

> ⚠️ Bramka izolacji dopasowuje po **granicach słów i po nazwie pakietu**, nigdy przez
> `contains(typ + " ")`. Po nazwie typu stoi kropka (`AdminPrivateNote.MAX_BODY_LENGTH`) albo nawias
> ostry (`List<AdminPrivateNote>`), a `import ...domain.adminnote.*;` nie zawiera nazwy typu w ogóle
> — a wildcard własnego pakietu domenowego jest **konwencją tego repo** (66 plików z importami
> gwiazdkowymi, dziewięć serwisów robi to ze swoją domeną). W bliźniaczej apce bramka miała dziurę
> dokładnie tam. Test ma też własny dowód „na czerwono" na pięciu kształtach obejścia.

**Kasowanie NIE przechodzi przez bramkę podopiecznego — odczyt i zapis tak.** To poprawka po
audycie, nie przeoczenie. Symetria wygląda bezpiecznie i uwięziła cudze dane: po odebraniu komuś
flagi `is_athlete` notatki o jego treningach stawały się **niewidoczne i nieusuwalne naraz** (bez
flagi kalendarz trenera jest niedostępny, a bramka odmawiała jedynej operacji, która mogła je
sprzątnąć) — dane osobowe bez ścieżki usunięcia, czyli odwrotność tego, po co ta bramka stoi.
Usunięcie własnego tekstu nie może niczego wypuścić, a zapytanie zawężone do (autor, cel) trafi
wyłącznie w wiersz, który wołający sam napisał. **Przy każdej bramce opartej na fladze pytaj
osobno, co dzieje się z danymi utworzonymi, kiedy flaga jeszcze była.**

**Cztery prawdziwe FK z kaskadą, nie para `(target_type, target_id)`.** Dyskryminator dałby jeden
upsert zamiast czterech i zostawiałby po skasowanym slocie/terminie/treningu wiersz z cudzym
tekstem, którego nic nie sprząta i którego nikt nie zobaczy, żeby usunąć. Notatka umiera razem
z tym, czego dotyczy, i razem z kontem autora. Cztery bliźniacze upserty to świadoma cena za brak
sierot. `slot_id` obsługuje **dwa** cele, rozróżniane przez `session_date` — zajęcia cykliczne nie
mają wiersza nigdzie (`RecurringSessionOverlayService` liczy je przy każdym odczycie), więc
adresuje je klucz `(autor, podopieczny, slot, data)`. **Indeksy unikatowe muszą być partial**:
w wierszu dwie z trzech kolumn celu są NULL, a NULL-e nie kolidują w zwykłym UNIQUE — bez predykatu
indeks przepuści dowolnie wiele notatek „bez slotu", a `ON CONFLICT (...) WHERE ...` nie ma czego
wskazać.

**Bez html-escape.** Escape przy zapisie zamienia cudzysłowy i apostrofy autora w encje, a wtedy
każde miejsce renderujące musi to odkodowywać. Ta treść nie trafia do maila ani do `innerHTML`,
a jedyny autor i jedyny czytelnik to ta sama osoba. Pusta notatka to notatka usunięta — od usuwania
jest kosz, `PUT` z pustym ciałem to 400.

**Zero wpisu w activity logu** — w tym repo activity logu nie ma, więc niezmiennik jest spełniony
z definicji; gdyby powstał, notatki mają w nim nie występować (log audytuje działania dotykające
ludzi, a zakładka aktywności ogłaszałaby samo istnienie notatek).

**Jeden notatnik na admina — łącznie z licznikami.** Każdy odczyt, zapis, kasowanie i zapytanie o znaczniki jest zawężone do `(author_id, cel)`; notatka nigdy nie jest adresowana własnym `id`, więc nie ma gałęzi bez porównania autora. Jedyny wyjątek to `deleteAllAboutAthlete` przy wymazywaniu planu — kasuje notatki **wszystkich** autorów, bo wymazanie danych osoby nie może być wybiórcze. ⚠️ Ale **liczba w odpowiedzi musi opisywać wyłącznie notatki wołającego**: raport „skasowano 3", gdy sam napisałeś jedną, jest sposobem na odkrycie, że drugi admin prowadzi tu notatki i ile ich ma. Dlatego `purgeForAthlete` kasuje najpierw własne (i tę liczbę zwraca), a dopiero potem resztę. Pilnuje tego `AdminNotePerAuthorIsolationIntegrationTest`, sprawdzający izolację na **wszystkich** powierzchniach naraz, nie tylko na odczycie.

**Kopia treningu NIGDY nie zabiera notatki — i pilnują tego testy, nie sam gate.** Załączniki (filmy) jadą z kopią, bo są częścią recepty; notatka to zapis tego, co się wydarzyło jednego dnia jednej osobie, więc nie jedzie. Trzy kształty zachowują się różnie: **COPY** tworzy nowy wiersz (notatka zostaje przy oryginale), **MOVE u tej samej osoby** przestawia datę tego samego wiersza (notatka jedzie z nim, poprawnie), **MOVE między osobami** to kopia + skasowanie oryginału (notatka ginie kaskadą, nigdy nie ląduje u drugiej osoby). ⚠️ Dopisanie kopiowania notatek do `duplicate`/`paste` „dla spójności z załącznikami" to najbardziej prawdopodobna przyszła zmiana i **przechodziła przez bramkę izolacji**, bo idzie przez `AdminPrivateNoteService`, a nie przez encję. Dlatego gate obejmuje dziś także ten seam (allowlista `SERVICE_CALLERS`), a `AdminNoteCopyIsolationIntegrationTest` pokrywa wszystkie trzy kształty.

**Notatka o człowieku ginie z jego planem, notatka o biznesie zostaje.** `DELETE /api/admin/users/{id}/training-plan` kasuje notatki o treningach i zajęciach tej osoby; notatki o **slocie** i o **terminie** przeżywają, bo są obserwacją o klubie („grupa środowa za duża"), nie o kimś. Kasowanie notatek celowo omija bramkę podopiecznego (patrz wyżej) — ale samo API nie wystarczyło: bez flagi kalendarz trenera jest niedostępny, więc **w panelu nie było gdzie kliknąć**. Ten endpoint jest drugą połową tej poprawki i dlatego siedzi w zakładce Użytkownicy, jedynej powierzchni niezależnej od flagi.

**Znaczniki oddają SAME IDENTYFIKATORY, nigdy treść** — inaczej odpowiedź, która istnieje po to,
żeby narysować ikonki, wsadza notatnik do przeglądarki i cofa powód, dla którego notatka ma osobny
endpoint per cel. Dotyczy to **także zwykłego booleana `hasNote`** na współdzielonym DTO: sam fakt
istnienia notatek jest prywatny.

> ⚠️ **`staleTime` notatek to `SHORT_STALE_MS`, nie `0`.** „Zero cache" znaczy „autor widzi swój
> zapis natychmiast" i zapewnia to inwalidacja **całego prefiksu** `['admin','notes']` po mutacji
> (sam klucz jednej notatki nie zapaliłby znacznika). Zero w `staleTime` przy domyślnym
> `refetchOnWindowFocus` odpala wszystkie zamontowane zapytania na każdy powrót na kartę, a trzy
> wpięcia montują **jedno zapytanie na wiersz** — to udokumentowany tu incydent z limiterem.

> ⚠️ **Kontrolka „pokaż całość" wynika z POMIARU (`scrollHeight` vs `clientHeight`), nie z długości
> tekstu.** `line-clamp` liczy **linie**, warunek na `body.length` liczy **znaki**: notatka
> wypunktowana na dziesięć krótkich linii przy 200 znakach była ucięta i nie dało się jej rozwinąć.
> Jedno zjawisko, jedna jednostka.

**Sekcji nie wolno bramkować na „termin się jeszcze nie odbył"** — zniknęłaby dokładnie z terminów,
o które w tej funkcji chodzi. Dlatego notatka do terminu wisi w **dwóch** miejscach: w zakładkach
Obozy/Szkolenia i w **Archiwum** (`AdminEvents` filtruje `(endDate ?? startDate) >= today`, więc
minione terminy są widoczne wyłącznie tam).
