# Fire Academy

Wizytówka klubu treningowego — treningi indywidualne i małe grupy (4–6 osób). Java/Spring Boot backend + React frontend. Dark mode (antracyt/czarny/pomarańczowy), Tailwind 4. Wyłącznie język polski.

**Domena:** `fireworkout.pl` (Cloudflare DNS, SSL)
**Firma:** FIZJO4LIFE Sp. z o.o., KRS 0001024771, NIP 6282290548
**Mail:** fireacademy.biz@gmail.com (SMTP Gmail App Password)
**Wersja:** odczytywana z pliku `VERSION` via `@version@` placeholder w `application.yml`

---
## Mapa dokumentacji

Ten plik trzyma to, co potrzebne **zanim** zajrzysz w kod. Reszta leży obok — otwieraj, gdy zmiana
dotyka danego obszaru:

| Plik | Kiedy otworzyć |
|------|----------------|
| [`API.md`](API.md) | dokładasz lub zmieniasz endpoint, upload pliku, maila, OG/SEO |
| [`MIGRATIONS.md`](MIGRATIONS.md) | piszesz migrację albo szukasz, skąd wzięła się kolumna |
| [`DECISIONS-SECURITY.md`](DECISIONS-SECURITY.md) | auth, JWT, OAuth, rate limiting, obsługa 401/429 na froncie |
| [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md) | rozliczenia, kalendarz 1:1, zgody RODO art. 9, prywatne notatki |
| [`INFRA.md`](INFRA.md) | deploy, Docker, JVM, kopie zapasowe, CI/CD, zmienne środowiskowe |
| `docs/manual.md` | manual aplikacji (lokalny, `docs/` jest w `.gitignore`) |

---

## Niezmienniki — przeczytaj, zanim ruszysz kod

Reguły, które da się złamać **bez błędu i bez ostrzeżenia**. Każda ma za sobą test albo incydent.
Jeśli zmiana dotyka któregoś punktu — otwórz wskazany plik **przed** pisaniem kodu.

**Upload, pliki, obrazy → [`API.md`](API.md)**
- Nowy folder pod `uploads/` jest **domyślnie prywatny** — publiczny dopiero po wpisie na białą listę `PUBLIC_FOLDERS` w `FileController`. `trainingphotos/` (dane zdrowotne) celowo tam nie wchodzi.
- Nowy format obrazu to **trzy miejsca**: `StorePolicy`, sygnatura w `ImageFormat.sniff`, wzorzec w `StoragePaths.FILENAME`. Pominięcie trzeciego → plik da się zapisać, ale nie odczytać ani skasować.
- Sufit 24 mln pikseli i `CONCURRENT_DECODES = 1` są **policzone** pod `mem_limit: 384m`. Zmieniasz jedno — przelicz drugie.

**Rate limiting i sesje → [`DECISIONS-SECURITY.md`](DECISIONS-SECURITY.md)**
- Każdy endpoint z `MultipartFile` musi trafić do kubełka `upload` w `RateLimitFilter.RULES` — `RateLimitCoverageTest` wywala build, gdy któryś ucieknie.
- Nowy kontroler = własna reguła w `RULES` albo świadomy wpis w allowliście `INTENTIONALLY_GENERIC`.
- **Sesję kończy wyłącznie 401/403 od serwera.** 429, timeout ani błąd sieci — nigdy. Nierozpoznany błąd znaczy „nie wiem", a nie „wyloguj".
- Hasło sprawdzane **przed** zgłoszeniem blokady konta (anty-enumeracja). Nie odwracać kolejności.
- Google OAuth jest na prodzie **wyłączone** flagą `GOOGLE_LOGIN_ENABLED`. Kolejność włączania: klucze → profil `oauth2` → flaga. Dopóki flaga stoi na `false`, markery „w przygotowaniu" w polityce prywatności **muszą** zostać.

**Maile → [`API.md`](API.md)**
- Wysyłka do wielu odbiorców to **jedno zadanie** na `mailCampaignExecutor`, nigdy jedno zadanie na osobę.
- Szkielet HTML maila istnieje **tylko** w `BrandedMailSender` — nie dopisuj lokalnego szablonu „bo ten jeden mail jest inny".
- Nagłówki `List-Unsubscribe` lecą **wyłącznie** na wysyłce masowej, nigdy na transakcyjnej.

**SEO i nginx → [`API.md`](API.md)**
- Bot-list w `fire-academy-frontend/nginx.conf` **nigdy** nie zawiera wyszukiwarek — pętla przekierowań = „Redirect error" w Search Console.
- Dokładasz zewnętrzny zasób → dopisz host do CSP w `nginx.conf` **i** do sekcji 4 w `seo-smoke.sh`. Dev server Vite nie wysyła CSP, więc lokalnie nic nie pęknie.

**Kalendarz 1:1 i rozliczenia → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- Nakładka zajęć cyklicznych **nigdy** nie jest materializowana do `personal_trainings`.
- Ekrany listowe pobierają dni zamknięte **raz na stronę**, nie raz na wiersz — cztery osobne budżety w `TrainingBillingQueryCountIntegrationTest`.
- Zadanie i trening to **dwa wiersze** (`kind`), nigdy jedno pole na wspólnym wierszu.
- Wpis trenera jest dla podopiecznego **tylko do odczytu**; klucz to `created_by_admin`, nigdy `last_modified_by_admin`.
- Wklejenie trafia do `targetAthleteId` — kalendarza otwartego na ekranie, nie do właściciela źródła.
- `attachments`: `null` = nie ruszaj · `[]` = wyczyść · lista = zamień. Zapis podopiecznego to pole **ignoruje**, nie odrzuca.
- Liczniki nieprzeczytanych mają **7 źródeł** i dwie krawędzie (`updated_at` + `seen_through`); predykat żyje w sześciu miejscach i zmienia się we wszystkich naraz.
- Klasa ze `@Scheduled` **nie może** deklarować `@Transactional` na innej metodzie — samowywołanie omija proxy i adnotacja milczy. Pilnuje `SchedulerTransactionArchTest`.
- Poszerzenie zakresu danych zdrowotnych = **nowa zgoda** (wyzerowanie `training_consent_at`), nie dopisek do listy.

**Prywatne notatki właściciela → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- Notatka **nigdy** nie jedzie w istniejącym DTO — także jako sam `hasNote`. Typ jest nieosiągalny poza `domain/adminnote` i `api/admin/note`; pilnują tego dwa testy z przeciwnych stron.
- Kasowanie notatki **omija** bramkę `is_athlete` (odczyt i zapis nie omijają). Zdjęcie flagi nie może uwięzić cudzych danych bez ścieżki usunięcia.
- Kopia treningu (`duplicate`, `paste`-COPY) **nie zabiera** notatki — inaczej niż załączniki.

**Front → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md)**
- `fetchApi` ponawia **tylko** `GET`/`HEAD`. Ponowiony zapis tworzy drugi wpis.
- `staleTime: 0` jest błędem (lawina żądań przy powrocie na kartę) — jest `SHORT_STALE_MS` z `utils/queryFreshness.ts`.
- Surowy `<input type="date">` poza `components/ui/DateInput` wywala build.

**Proces**
- **Migracje SQL są niezmienne** — nawet poprawka komentarza w zaaplikowanym pliku zmienia checksum Flyway i wywala deploy.
- Produkt po polsku, kod (komentarze, logi, commity, PR) po angielsku.
- Commit funkcjonalny zawsze z bumpem `VERSION` — bez tego CI frontu się nie odpala (filtr `paths`), a tag obrazu w GHCR jest nadpisywany.
- Kopie zapasowe: `rclone copy`, **nigdy** `sync` → [`INFRA.md`](INFRA.md).

---

## Stack

### Backend
- **Java 25** + **Spring Boot 4.1.1**
- Spring Security 7.1 + JWT (jjwt 0.13.0), Spring Data JPA + **PostgreSQL 18** (prod od 2026-09-06; dev compose też 18)
- Spring Boot Starter Mail, **Actuator** (health check), Cache + Caffeine, **Flyway 12**, DevTools (dev)
- **JSpecify 1.0.1** (@NullMarked), **springdoc-openapi 3.1.0**
- **Testcontainers 1.21.4** + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 6.0**, **Vite 8.0**
- **Tailwind CSS 4.1**, **TanStack React Query 5.101**, **React Router 7.13**
- **date-fns 4.1**, **lucide-react 1.27**, **clsx 2.1**, ESLint 10
- **i18next 26** + **react-i18next 17**, Vitest 4 + Playwright (E2E)

### Struktura repozytorium
```
fire-academy-backend/
fire-academy-frontend/     # nginx.conf (bot-list dla /og) + scripts/prerender.mjs
fire-academy-hub/          # Docker Compose (dev/prod), .env, setup-swap.sh, seo-smoke.sh
docs/                      # ⚠️ w .gitignore — lokalne: manual.md, workflow gita
.github/workflows/         # CI/CD
CLAUDE.md · API.md · MIGRATIONS.md · DECISIONS-SECURITY.md · DECISIONS-TRAINING.md · INFRA.md · VERSION
```

---

## Baza danych — Flyway

**Obecny stan: V42 (wszystko na `main`). Kolejna migracja: V43.**

Pełna historia V1–V42 z opisem każdej zmiany → [`MIGRATIONS.md`](MIGRATIONS.md).
Uzasadnienia decyzji treningowych (V24–V39) → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md).

---

## API — mapa prefiksów

Pełna lista endpointów wraz z pułapkami → [`API.md`](API.md).

| Prefiks | Dostęp | Co tam jest |
|---------|--------|-------------|
| `/api/auth` | brak | rejestracja, logowanie, weryfikacja maila, reset hasła, refresh |
| `/api/user` | zalogowany | profil, zgody, avatar, zapisy na wydarzenia, subskrypcje slotów |
| `/api/user/my-training` | podopieczny (`is_athlete` + zgoda art. 9) | kalendarz 1:1, waga, cele, komentarze, zdjęcia |
| `/api/admin` | ROLE_ADMIN | kadra, rodzaje, terminy, zapisy, sloty, płatności, zwroty, użytkownicy, notatki, plan 1:1, biblioteka filmów |
| `/api/public` | brak | katalog kadry/rodzajów/terminów/slotów, wypis z marketingu |
| `/api/files` | brak | streaming plików z białej listy folderów |
| `/og` · `/sitemap.xml` | brak | Open Graph dla scraperów social, mapa strony |
| `/api/dev` | profil `dev` | logowanie na skróty, lista userów |

---

## Frontend Routes

| Ścieżka | Komponent | Opis |
|---------|-----------|------|
| `/` | HomePage | Hero z 3 sekcjami (diagonal clip-path): Treningi / Obozy / Szkolenia |
| `/treningi` | TrainingsPage | Terminy + Rodzaje (popup modal) + Kadra |
| `/obozy` | CampsPage | Terminy + Rodzaje (popup modal) + Kadra |
| `/szkolenia` | CoursesPage | Terminy + Rodzaje (popup modal) + Kadra |
| `/:category/rodzaj/:id` | EventTypeDetailPage | Strona szczegółów rodzaju (galeria, opis, powiązane terminy, share) |
| `/:category/termin/:id` | EventDetailPage | Strona szczegółów terminu (data, lokalizacja, cena, zapis, share) |
| `/kadra/:id` | InstructorDetailPage | Strona szczegółów instruktora (zdjęcie, bio, share) |
| `/logowanie` | LoginPage | Logowanie (link „Zaloguj się" w Navbarze dla gościa na każdej zakładce; `/admin/login` i `/login` → redirect tutaj). Po zalogowaniu wraca na zapamiętaną ścieżkę (returnTo) |
| `/rejestracja` | RegisterPage | Rejestracja konta (telefon + wymagana akceptacja polityki prywatności → `acceptedPrivacy` + opcjonalna zgoda marketingowa → `acceptedMarketing`; `/admin/register`, `/register` → redirect tutaj) |
| `/uzupelnij-profil` | ProfileCompletionPage | Domknięcie konta po Google (ProtectedRoute): brakujące pola profilu + (gdy `privacyAccepted=false`) obowiązkowa polityka prywatności i opcjonalny marketing → `POST /api/user/me/consents`. Pokazywana gdy `needsProfileCompletion(user)` |
| `/wypisz-sie` | MarketingUnsubscribePage | Rezygnacja z marketingu z linku w mailu (public, `?token=`, przycisk → `POST /api/public/marketing/unsubscribe`) |
| `/moje-konto` | MyAccountPage | Konto usera (ProtectedRoute): profil + kafelki do sekcji poniżej |
| `/moje-konto/rezerwacje` | MyReservationsPage | Moje rezerwacje na wydarzenia (bieżące/archiwum z `GET /api/user/enrollments`, anulowanie własnego zapisu) |
| `/moje-konto/treningi` | MyTrainingsPage | Moje treningi cykliczne (subskrypcje slotów, rachunek, rezygnacja — `GET /api/user/training-enrollments`) |
| `/moje-konto/plan-treningowy` | MyTrainingCalendarPage | Kalendarz treningów 1:1 podopiecznego (ProtectedRoute + `isAthlete`): cele (read-only), kalendarz, waga, statystyki. Kafelek na `/moje-konto` widoczny tylko przy `isAthlete` |
| `/settings` | SettingsPage | Ustawienia konta (ProtectedRoute): avatar, dane (w tym telefon), hasło, zgoda marketingowa (toggle), usunięcie konta |
| `/polityka-prywatnosci` | PrivacyPolicyPage | Polityka prywatności (link ze stopki i z formularzy zgód) |
| `/admin/*` | AdminPage | Panel admina (zakładki: kadra, treningi, obozy, szkolenia, **podopieczni**, użytkownicy, archiwum). Zakładka „Podopieczni": lista → klik = kalendarz 1:1 tej osoby (przejmuje całą zakładkę) + cele + statystyki; pod listą biblioteka filmów i szablony. Zakładka „Użytkownicy": lista (paginacja+sort+wyszukiwanie) → klik w osobę = profil (`AdminUserDetail`: dane podgląd, zapisy bieżące/archiwum, dopisanie do wydarzenia, usuwanie zapisu/wpisu z archiwum). **Zakładka RODO usunięta** — prawo do bycia zapomnianym = usunięcie konta (anonimizuje całą historię, patrz niżej) |
| `/verify-email` | VerifyEmailPage | Weryfikacja email (link z maila) |
| `/reset-password` | ResetPasswordPage | Reset hasła (link z maila) |
| `/forgot-password` | ForgotPasswordPage | Formularz zapomniałem hasła |
| `/resend-verification` | ResendVerificationPage | Ponowne wysłanie linku weryfikacyjnego |
| `/oauth-callback` | OAuthCallbackPage | Powrót z Google (odbiera tokeny, kieruje dalej — m.in. na `/uzupelnij-profil`) |

Nawigacja (Navbar): Strona główna · Treningi · Obozy · Szkolenia · (Moje konto — zalogowany user) · (Panel admina — zalogowany admin) · (Zaloguj się — gość, na każdej zakładce). Zapis na wydarzenie wymaga konta: hook `useEnrollGuard` przekierowuje gościa na `/logowanie` z returnTo.

Stopka (Footer): Opis Fire Academy · Quick links · Dane kontaktowe · Polityka prywatności · ShareButton. **Regulaminu nie ma** — klucz `footer.terms` siedzi w `locales/pl/common.json`, ale nic go nie renderuje i nie ma trasy; przy dodawaniu linku trzeba najpierw dopisać stronę

### Udostępnianie (ShareButton)
Rozwijany przycisk (Facebook / WhatsApp / Kopiuj link) na: kartach rodzajów, wierszach terminów, kartach kadry, stronach szczegółów, stopce (strona główna). Slug kategorii: `treningi`↔TRAINING, `obozy`↔CAMP, `szkolenia`↔COURSE (`src/utils/categorySlug.ts`). OG meta tagi w `index.html` (statyczny fallback) + `react-helmet-async` (dynamiczny `<title>`) + backend `OgController` (dla crawlerów). Placeholder `public/og-default.png` — wymaga zastąpienia właściwym obrazem 1200×630px.

---

## Język

**Rozróżnienie: język produktu vs język kodu.**

- **Produkt (UI, treści dla użytkownika) — tylko polski.** Backend: `messages.properties` (pl), frontend: `locales/pl/`. Default `preferredLanguage` w bazie i kodzie: `"pl"`. Stringi widoczne dla usera, maile, komunikaty błędów, OG/SEO — po polsku.
- **Kod (komentarze, logi, commity) — angielski.** Komentarze (`//`, `/* */`, Javadoc/JSDoc) i stringi logów (`log.info/warn/error`, `console.*`) piszemy po angielsku (konwencja przyjęta 2026-06-22). Commity/PR też po angielsku.
  - **NIE tłumaczymy na angielski:** `messages.properties`, `locales/*.json`, stringi UI, komunikaty wyjątków pokazywane userowi, treści OG/SEO, dane seedowane (`DevDataSeeder`), wartości asercji w testach sprawdzające polski tekst.
  - **Migracje `db/migration/*.sql` są niezmienne** (zaaplikowane na prodzie → zmiana komentarza = inny checksum Flyway = błąd deployu). Komentarze w istniejących migracjach zostają jak były; nowe piszemy po angielsku.

---

## Local Dev Workflow

**Porty dev:** PostgreSQL `5433` · backend `8081` · frontend (Vite) `5174` · MailHog SMTP `1026`, web UI `8026`

```bash
# 1. Baza danych + MailHog
cd fire-academy-hub && docker compose -f docker-compose.dev.yml up -d

# 2. Backend (IntelliJ: Run FireAcademyApplication z profilem dev)
#    lub z terminala:
cd fire-academy-backend && ./gradlew bootRun

# 3. Frontend
cd fire-academy-frontend && npm run dev
```

Backend wymaga działającego PostgreSQL (port 5433). MailHog (web UI: localhost:8026) przechwytuje emaile wysyłane przez auth flow (weryfikacja konta, reset hasła).

---

## Testy

```bash
./gradlew test                                    # Wszystkie testy
./gradlew test --tests "JwtServiceTest"           # Konkretna klasa
```

**Naming:** `shouldDoSomethingWhenCondition()`, struktura Given/When/Then

**Bramka frontu — `frontend/src/__architecture__/dateInput.test.ts`.** Surowy `<input type="date">`
poza `components/ui/DateInput` wywala build. Safari na macOS **nie zamyka** swojego popovera po
kliknięciu dnia: wartość jest już zapisana, więc klik czyta się jako „nic się nie stało", a data
„pojawia się" dopiero po kliknięciu obok. `DateInput` robi `blur` i **zaraz oddaje fokus** temu
samemu polu — bez tego powrotu reszta przeglądarek (które popover zamykają same) płaciłaby za fix
fokusem na `body`: Enter przestaje zapisywać, a Tab startuje od góry strony. Powrót fokusu popovera
**nie** otwiera, bo natywny picker otwiera się na klik, nie na fokus. Jedno i drugie **nie** dzieje
się przy pisaniu z klawiatury (zdarzenie leci po każdym znaku, więc fokus uciekałby po dniu, przed
miesiącem) ani na dotyku (kółko na iOS wysyła zdarzenie przy każdym przekręceniu i zamyka się
własnym „Done"). Bramka czyta źródła przez `import.meta.glob`, nie `node:fs`, bo ten pakiet nie ma
`@types/node`; osobny test pilnuje, że glob **cokolwiek** widzi — pusty glob przechodziłby zawsze.
