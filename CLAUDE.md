# Fire Academy

Wizytówka klubu treningowego — treningi indywidualne i małe grupy (4–6 osób). Java/Spring Boot backend + React frontend. Dark mode (antracyt/czarny/pomarańczowy), Tailwind 4. Wyłącznie język polski.

**Domena:** `fireworkout.pl` (Cloudflare DNS, SSL)
**Firma:** FIZJO4LIFE Sp. z o.o., KRS 0001024771, NIP 6282290548
**Mail:** fireacademy.biz@gmail.com (SMTP Gmail App Password)
**Wersja:** odczytywana z pliku `VERSION` via `@version@` placeholder w `application.yml`

---

## Stack

### Backend
- **Java 25** + **Spring Boot 4.1.0**
- Spring Security 7.1 + JWT (jjwt 0.13.0), Spring Data JPA + **PostgreSQL 17**
- Spring Boot Starter Mail, **Actuator** (health check), Cache + Caffeine, **Flyway 12**, DevTools (dev)
- **JSpecify 1.0.0** (@NullMarked), **springdoc-openapi 3.0.3**
- **Testcontainers 1.21.4** + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 6.0**, **Vite 8.0**
- **Tailwind CSS 4.1**, **TanStack React Query 5.90**, **React Router 7.13**
- **date-fns 4.1**, **lucide-react 1.17**, **clsx 2.1**, ESLint 10
- **i18next 26** + **react-i18next 17**, Vitest 4 + Playwright (E2E)

### Struktura repozytorium
```
fire-academy-backend/
fire-academy-frontend/
fire-academy-hub/          # Docker Compose (dev/prod), .env
.github/workflows/      # CI/CD
VERSION
```

---

## Baza Danych — Flyway

**Obecny stan: V19. Kolejna migracja: V20.**
> ⚠️ V12–V15 są **zarezerwowane przez niezmergowaną gałąź treningową** (`feat/trainings-types-scheduling`) i bywają już zastosowane w bazach dev. Dlatego zmiany na innych gałęziach numerujemy **od V16 w górę** (Flyway dopuszcza lukę po V11), żeby nie kolidować z `training_*`.

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
| V12–V15 | *(zarezerwowane przez gałąź treningową — `training_slots`, `training_payments`, odwołania/dezaktywacja; nie na tej gałęzi)* |
| V16 | enrollments.phone → nullable (admin może dopisać zalogowanego usera bez numeru; RODO — minimalizacja) |
| V17 | enrollments.user_id → FK do users (ON DELETE SET NULL) + indeks + unikat (user_id,event_id); users.privacy_accepted_at (zgoda RODO). **Zapis wymaga konta** (PII = źródło prawdy w users — roster admina i maile czytają aktualne dane przez `Enrollment.display*()`; kolumny snapshotu firstName/lastName/email/phone w `enrollments` to **tylko fallback** dla czytelności archiwum po usunięciu konta, nie odświeżane przy edycji profilu) |
| V18 | users.marketing_consent_at (zgoda marketingowa opt-in, NULL=brak; wzorzec jak privacy_accepted_at) + users.marketing_unsubscribe_token (UUID, NOT NULL DEFAULT gen_random_uuid(), unikat — stabilny token linku rezygnacji bez logowania). **Marketing odrębny od maili serwisowych**: serwisowe (zapisy/odwołania, weryfikacja, reset) zawsze idą; marketing tylko za zgodą i z linkiem rezygnacji |
| V19 | DROP users.email_notifications_enabled — kolumna nigdy nie była egzekwowana (żaden mail service nie sprawdzał flagi); zastąpiona całkowicie przez marketing_consent_at. Usunięty endpoint `PUT /me/notifications` + DTO + frontowy `authApi.updateNotifications` |

---

## API Endpoints

### Auth `/api/auth`
`POST /register` · `POST /login` · `POST /logout` · `POST /verify-email?token=` · `POST /resend-verification` · `POST /forgot-password` · `POST /reset-password` · `POST /refresh`

### User `/api/user` (auth required)
`GET /me` (zwraca m.in. `privacyAccepted`, `marketingConsent`) · `PUT /me` · `PUT /me/password` · `DELETE /me` (RODO: anonimizuje całą historię — przyszłe zapisy kasuje (zwalnia miejsce), przeszłe anonimizuje + zeruje `user_id`; ta sama logika co admin → `EnrollmentErasureService.eraseForUser`, wołana PRZED usunięciem konta) · `PUT /me/marketing` (toggle zgody marketingowej `{enabled}`) · `POST /me/consents` (domknięcie zgód po Google: `{acceptedPrivacy, acceptedMarketing}` — polityka obowiązkowa gdy jeszcze nieudzielona) · `PUT /me/language` · `POST /me/avatar` (multipart, kadrowanie po stronie frontu) · `DELETE /me/avatar`
`POST /enrollments` (zapis na wydarzenie z konta — body `{eventId, note}`, dane z profilu; brak telefonu w profilu → `enrollment.phone.required`, front kieruje do `/settings`) · `GET /enrollments` (moje rezerwacje: `{current, past}`, każdy z `canCancel`) · `DELETE /enrollments/{id}` (anulowanie własnego zapisu; blokada <24h; powiadamiany tylko organizator)

### Files `/api/files` (public, cached 7 dni)
`GET /files/{folder}/{filename}` — streaming

### Public `/api/public` (brak auth)
`GET /instructors?category=` · `GET /instructors/{id}` · `GET /event-types?category=` · `GET /event-types/{id}` · `GET /events?category=` · `GET /events/{id}`
`POST /marketing/unsubscribe` (`{token}` — rezygnacja z marketingu z linku w mailu, bez logowania; idempotentne, zawsze 204 — anti-enumeracja; front: strona `/wypisz-sie?token=`, świadomie POST nie GET, by skanery maili nie wypisywały userów prefetchem)
> ⚠️ Zapis gościa (`POST /events/{id}/enroll`) **USUNIĘTY** (V17). Zapis tylko przez konto → `POST /api/user/enrollments`. Niezalogowany klik „Zapisz się" w SPA → redirect na `/logowanie` (returnTo).

### OG `/og` (brak auth, HTML z Open Graph meta tagami dla crawlerów social media)
`GET /` · `GET /{categorySlug}/rodzaj/{id}` · `GET /{categorySlug}/termin/{id}` · `GET /kadra/{id}`
Nginx wykrywa crawlery (Facebook, WhatsApp, Twitter itp.) i proxy detail pages do tych endpointów. Zwraca HTML z `og:title`, `og:description`, `og:image` + meta refresh redirect do SPA.

> ⚠️ **GOTCHA — bot-list w `nginx.conf` NIGDY nie zawiera wyszukiwarek.** Reguły `if ($http_user_agent ~* ...)` na `/kadra/{id}` i `/(treningi|obozy|szkolenia)/(rodzaj|termin)/{id}` przepisują bota na `/og/*`, a `OgController` zwraca stub z `<meta http-equiv="refresh">` na ten sam URL. Crawler renderujący JS (Googlebot/bingbot/Baidu/Yandex) podąża za refreshem → znowu reguła bota → **pętla = GSC „Redirect error"** + pusta strona (zła dla SEO). Trzymaj tam wyłącznie scrapery social (FB/WhatsApp/Twitter/LinkedIn/Slack/Telegram/Discord); wyszukiwarki muszą trafiać do SPA. (Naprawione 2026-06; ten sam błąd był w climbing.)

### Admin `/api/admin` (ROLE_ADMIN)
`/instructors` — CRUD + categories (CAMP/COURSE/TRAINING) + photo upload + reorder + toggle active
`/event-types` — CRUD + `?category=` + thumbnail + gallery photos + reorder
`/events` — CRUD + `?category=` + toggle active + customName (bez auto-create EventType)
`/enrollments` — lista + admin-add + delete (`DELETE /{id}?notify=` — `notify=false` = ciche usunięcie z archiwum, bez maila o odwołaniu; admin-add ma guard duplikatu `enrollment.already.exists`. **Admin-add tylko dla istniejącego konta** — `AdminEnrollRequest{eventId, userId, note}`, dane uczestnika z konta (front: wyszukiwarka usera przez `GET /users?search=`); duplikat per `user_id+event`)
`/users` — `GET /{id}` (profil: dane + avatar + ustawienia + `currentEnrollments`/`pastEnrollments` — bieżące vs archiwalne po `COALESCE(endDate,startDate)`) · `GET ?search=&page=&size=&sort=&direction=` (lista/wyszukiwanie po imieniu/nazwisku/mailu, **stronicowane** — domyślnie 50/stronę, max 100; zwraca `{content, page, size, totalElements, totalPages}`. Sortowanie: `sort` ∈ {`name`, `email`, `role`, `marketing`, `created`} (whitelist, telefon niesortowalny; `marketing` = po `marketing_consent_at`), `direction` ∈ {`asc`,`desc`}, domyślnie `created`/`desc`. Lista zwraca też `marketingConsent` per user (ikona zgody w UI). **Konta z `ADMIN_HIDDEN_EMAILS` ukryte** — filtr w SQL, by liczniki/paginacja były spójne; te same konta pominięte w wyszukiwarce admin-add i jako adresaci maila zbiorczego) · `POST /email` (`{subject, message, audience, userIds}` — `audience` ∈ {`MARKETING` (tylko zgody marketingowe + auto link rezygnacji w stopce), `ALL` (komunikat serwisowy do wszystkich, bez linku), `SELECTED` (wybrane `userIds`)}; branding+podpis auto, ukryte konta pomijane) · `DELETE /{id}` (bezpieczne usunięcie: przyszłe zapisy usuwane = zwolnienie miejsca, archiwalne anonimizowane, kasowane tokeny+avatar) · `POST /{id}/promote` (**tylko super-admin z `ADMIN_EMAIL`**) · `POST /{id}/demote` (**tylko super-admin z `ADMIN_EMAIL`**; nie da się zdegradować super-admina ani siebie)

> **Super-admin** = e-mail z `ADMIN_EMAIL` (`AdminEmailConfig.isAdminEmail`). `GET /api/user/me` zwraca flagę `superAdmin` (front pokazuje przyciski nadania i odebrania uprawnień admina tylko jemu). Maile admin→user: `AdminUserMailService` (logo Fire Academy, podpis „Pozdrawiam, Fire Academy", temat bez HTML-escape).

### Dev `/api/dev` (profil `dev` only)
`POST /login` · `GET /users`

---

## Frontend Routes

| Ścieżka | Komponent | Opis |
|---------|-----------|------|
| `/` | HomePage | Hero z 3 sekcjami (diagonal clip-path): Treningi / Obozy / Szkolenia |
| `/treningi` | TrainingsPage | Terminy + Rodzaje (popup modal) + Kadra |
| `/obozy` | EventsPage(CAMP) | Terminy + Rodzaje (popup modal) + Kadra |
| `/szkolenia` | EventsPage(COURSE) | Terminy + Rodzaje (popup modal) + Kadra |
| `/:category/rodzaj/:id` | EventTypeDetailPage | Strona szczegółów rodzaju (galeria, opis, powiązane terminy, share) |
| `/:category/termin/:id` | EventDetailPage | Strona szczegółów terminu (data, lokalizacja, cena, zapis, share) |
| `/kadra/:id` | InstructorDetailPage | Strona szczegółów instruktora (zdjęcie, bio, share) |
| `/logowanie` | LoginPage | Logowanie (link „Zaloguj się" w Navbarze dla gościa na każdej zakładce; `/admin/login` i `/login` → redirect tutaj). Po zalogowaniu wraca na zapamiętaną ścieżkę (returnTo) |
| `/rejestracja` | RegisterPage | Rejestracja konta (telefon + wymagana akceptacja polityki prywatności → `acceptedPrivacy` + opcjonalna zgoda marketingowa → `acceptedMarketing`; `/admin/register`, `/register` → redirect tutaj) |
| `/uzupelnij-profil` | ProfileCompletionPage | Domknięcie konta po Google (ProtectedRoute): brakujące pola profilu + (gdy `privacyAccepted=false`) obowiązkowa polityka prywatności i opcjonalny marketing → `POST /api/user/me/consents`. Pokazywana gdy `needsProfileCompletion(user)` |
| `/wypisz-sie` | MarketingUnsubscribePage | Rezygnacja z marketingu z linku w mailu (public, `?token=`, przycisk → `POST /api/public/marketing/unsubscribe`) |
| `/moje-konto` | MojeKontoPage | Konto usera (ProtectedRoute): profil + „Moje rezerwacje" (bieżące/archiwum z `GET /api/user/enrollments`, anulowanie własnego zapisu) |
| `/settings` | SettingsPage | Ustawienia konta (ProtectedRoute): avatar, dane (w tym telefon), hasło, zgoda marketingowa (toggle), usunięcie konta |
| `/admin/*` | AdminPage | Panel admina (zakładki: kadra, treningi, obozy, szkolenia, użytkownicy, archiwum). Zakładka „Użytkownicy": lista (paginacja+sort+wyszukiwanie) → klik w osobę = profil (`AdminUserDetail`: dane podgląd, zapisy bieżące/archiwum, dopisanie do wydarzenia, usuwanie zapisu/wpisu z archiwum). **Zakładka RODO usunięta** — prawo do bycia zapomnianym = usunięcie konta (anonimizuje całą historię, patrz niżej) |
| `/verify-email` | VerifyEmailPage | Weryfikacja email (link z maila) |
| `/reset-password` | ResetPasswordPage | Reset hasła (link z maila) |
| `/forgot-password` | ForgotPasswordPage | Formularz zapomniałem hasła |

Nawigacja (Navbar): Strona główna · Treningi · Obozy · Szkolenia · (Moje konto — zalogowany user) · (Panel admina — zalogowany admin) · (Zaloguj się — gość, na każdej zakładce). Zapis na wydarzenie wymaga konta: hook `useEnrollGuard` przekierowuje gościa na `/logowanie` z returnTo.

Stopka (Footer): Opis Fire Academy · Quick links · Dane kontaktowe · Polityka prywatności · Regulamin · ShareButton

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

## Autentykacja

- Email/password: rejestracja → weryfikacja email → login → JWT
- JWT: access (15 min) + refresh (7 dni), algorithm HS256
- OAuth2 Google (opcjonalny): aktywacja przez profil `oauth2` (`SPRING_PROFILES_ACTIVE=dev,oauth2`), wymaga `OAUTH2_GOOGLE_CLIENT_ID` + `OAUTH2_GOOGLE_CLIENT_SECRET` w `.env`
- **Auto-admin:** email z `ADMIN_EMAIL` (env var) automatycznie dostaje ADMIN przy rejestracji
- Account lockout: 5 failed attempts → 15 min lockout
- Rate limiting: per-IP per-endpoint
- **Konta publiczne** — logowanie/rejestracja dostępne dla każdego (`/logowanie`, `/rejestracja`); Navbar pokazuje „Zaloguj się" gościowi na wszystkich zakładkach. Zapis na wydarzenia wymaga konta. Admin trafia do panelu przez `/admin` (gdy zalogowany jako ADMIN). Rejestracja zapisuje zgodę RODO (`users.privacy_accepted_at`) + opcjonalną zgodę marketingową (`users.marketing_consent_at`). **Google OAuth**: zgody domykane na `/uzupelnij-profil` (polityka obowiązkowa, marketing opcjonalny) — `OAuth2UserService.createNewUser` celowo nie ustawia zgód
- Strony utility (verify-email, reset-password, forgot-password, resend-verification) pozostają na root level (linki z maili)

---

## Infrastruktura

### Dev Ports
- PostgreSQL: 5433
- Backend: 8081
- Frontend (Vite): 5174
- MailHog SMTP: 1026, Web UI: 8026

### Serwer produkcyjny
- **Swap 2 GB — krytyczny przy ograniczonej pamięci (bez tego OOM).** Przy pierwszym deploy uruchomić raz: `sudo bash setup-swap.sh` (skrypt w `fire-academy-hub/`, idempotentny: 2 GB `/swapfile`, swappiness 10, utrwalone w fstab + sysctl)
- **JVM tuning backendu (mem_limit 384m).** ENTRYPOINT w `fire-academy-backend/Dockerfile`: `-XX:MaxRAMPercentage=55.0` (~211 MB heap; + non-heap ~120 MB mieści się w 384 MB z zapasem — przy 75% było ~288 MB heap → ~408 MB > limit = ryzyko OOM-kill i wypychania bezczynnego heapu do swapu), `-XX:MaxMetaspaceSize=128m`, `-XX:+ExitOnOutOfMemoryError`. **Bez wymuszonego `-XX:+UseG1GC`** — poniżej 2 GB RAM JVM ergonomicznie wybiera lekszy SerialGC (mniej pamięci natywnej niż G1 na ciasnym boxie). `$JAVA_OPTS` zachowany jako passthrough; w `docker-compose.prod.yml` `JAVA_OPTS=""` (po upgradzie RAM można tam wstawić `-XX:+UseG1GC`).
- **Mail health poza liveness probe.** `management.health.mail.enabled=false` w `application.yml` (domyślnie, niezależnie od env). Wolny SMTP (~10 s) przekraczał 5 s timeout docker healthchecka `/actuator/health` → fałszywe „unhealthy" → zbędny restart. Maile nie są liveness-critical. (W compose env `MANAGEMENT_HEALTH_MAIL_ENABLED=false` zostaje jako redundantny, jawny override.)

### CI/CD (GitHub Actions)
- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main
- `deploy.yml`: ręczny trigger → SSH → `docker compose pull && up -d`

### Zmienne środowiskowe (`.env`)
`POSTGRES_DB/USER/PASSWORD`, `MAIL_HOST/PORT/USERNAME/PASSWORD`, `JWT_SECRET`, `GHCR_OWNER`, `VERSION`, `ADMIN_EMAIL`
Opcjonalne: `ADMIN_HIDDEN_EMAILS` (CSV — konta techniczne/deweloperskie z adminem do testów, **ukryte na liście użytkowników** w panelu, filtr w SQL `AdminEmailConfig.isHiddenEmail`); profil `oauth2`: `OAUTH2_GOOGLE_CLIENT_ID/SECRET`

---

## Local Dev Workflow

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
