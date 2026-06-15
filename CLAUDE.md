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

**Obecny stan: V11. Kolejna migracja: V12.**

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

---

## API Endpoints

### Auth `/api/auth`
`POST /register` · `POST /login` · `POST /logout` · `POST /verify-email?token=` · `POST /resend-verification` · `POST /forgot-password` · `POST /reset-password` · `POST /refresh`

### User `/api/user` (auth required)
`GET /me` · `PUT /me` · `PUT /me/password` · `DELETE /me` · `PUT /me/notifications` · `PUT /me/language` · `POST /me/avatar` (multipart, kadrowanie po stronie frontu) · `DELETE /me/avatar`

### Files `/api/files` (public, cached 7 dni)
`GET /files/{folder}/{filename}` — streaming

### Public `/api/public` (brak auth)
`GET /instructors?category=` · `GET /instructors/{id}` · `GET /event-types?category=` · `GET /event-types/{id}` · `GET /events?category=` · `GET /events/{id}` · `POST /events/{id}/enroll`

### OG `/og` (brak auth, HTML z Open Graph meta tagami dla crawlerów social media)
`GET /` · `GET /{categorySlug}/rodzaj/{id}` · `GET /{categorySlug}/termin/{id}` · `GET /kadra/{id}`
Nginx wykrywa crawlery (Facebook, WhatsApp, Twitter itp.) i proxy detail pages do tych endpointów. Zwraca HTML z `og:title`, `og:description`, `og:image` + meta refresh redirect do SPA.

> ⚠️ **GOTCHA — bot-list w `nginx.conf` NIGDY nie zawiera wyszukiwarek.** Reguły `if ($http_user_agent ~* ...)` na `/kadra/{id}` i `/(treningi|obozy|szkolenia)/(rodzaj|termin)/{id}` przepisują bota na `/og/*`, a `OgController` zwraca stub z `<meta http-equiv="refresh">` na ten sam URL. Crawler renderujący JS (Googlebot/bingbot/Baidu/Yandex) podąża za refreshem → znowu reguła bota → **pętla = GSC „Redirect error"** + pusta strona (zła dla SEO). Trzymaj tam wyłącznie scrapery social (FB/WhatsApp/Twitter/LinkedIn/Slack/Telegram/Discord); wyszukiwarki muszą trafiać do SPA. (Naprawione 2026-06; ten sam błąd był w climbing.)

### Admin `/api/admin` (ROLE_ADMIN)
`/instructors` — CRUD + categories (CAMP/COURSE/TRAINING) + photo upload + reorder + toggle active
`/event-types` — CRUD + `?category=` + thumbnail + gallery photos + reorder
`/events` — CRUD + `?category=` + toggle active + customName (bez auto-create EventType)
`/enrollments` — lista + admin-add + delete

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
| `/admin/login` | LoginPage | Logowanie admina (ukryte, brak linku na stronie) |
| `/admin/register` | RegisterPage | Rejestracja admina |
| `/admin/*` | AdminPage | Panel admina (5 zakładek: kadra, treningi, obozy, szkolenia, zapisy) |
| `/verify-email` | VerifyEmailPage | Weryfikacja email (link z maila) |
| `/reset-password` | ResetPasswordPage | Reset hasła (link z maila) |
| `/forgot-password` | ForgotPasswordPage | Formularz zapomniałem hasła |

Nawigacja (Navbar): Strona główna · Treningi · Obozy · Szkolenia · (Panel admina — widoczny tylko dla zalogowanego admina)

Stopka (Footer): Opis Fire Academy · Quick links · Dane kontaktowe · Polityka prywatności · Regulamin · ShareButton

### Udostępnianie (ShareButton)
Rozwijany przycisk (Facebook / WhatsApp / Kopiuj link) na: kartach rodzajów, wierszach terminów, kartach kadry, stronach szczegółów, stopce (strona główna). Slug kategorii: `treningi`↔TRAINING, `obozy`↔CAMP, `szkolenia`↔COURSE (`src/utils/categorySlug.ts`). OG meta tagi w `index.html` (statyczny fallback) + `react-helmet-async` (dynamiczny `<title>`) + backend `OgController` (dla crawlerów). Placeholder `public/og-default.png` — wymaga zastąpienia właściwym obrazem 1200×630px.

---

## Język

Aplikacja wspiera wyłącznie **język polski**. Backend: `messages.properties` (pl), frontend: `locales/pl/`. Default `preferredLanguage` w bazie i kodzie: `"pl"`.

---

## Autentykacja

- Email/password: rejestracja → weryfikacja email → login → JWT
- JWT: access (15 min) + refresh (7 dni), algorithm HS256
- OAuth2 Google (opcjonalny): aktywacja przez profil `oauth2` (`SPRING_PROFILES_ACTIVE=dev,oauth2`), wymaga `OAUTH2_GOOGLE_CLIENT_ID` + `OAUTH2_GOOGLE_CLIENT_SECRET` w `.env`
- **Auto-admin:** email z `ADMIN_EMAIL` (env var) automatycznie dostaje ADMIN przy rejestracji
- Account lockout: 5 failed attempts → 15 min lockout
- Rate limiting: per-IP per-endpoint
- **Logowanie ukryte** — brak przycisku logowania na stronie publicznej. Admin loguje się wchodząc na `/admin` (przekierowanie → `/admin/login`). Rejestracja: `/admin/register`
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

### CI/CD (GitHub Actions)
- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main
- `deploy.yml`: ręczny trigger → SSH → `docker compose pull && up -d`

### Zmienne środowiskowe (`.env`)
`POSTGRES_DB/USER/PASSWORD`, `MAIL_HOST/PORT/USERNAME/PASSWORD`, `JWT_SECRET`, `GHCR_OWNER`, `VERSION`, `ADMIN_EMAIL`
Opcjonalne (profil `oauth2`): `OAUTH2_GOOGLE_CLIENT_ID/SECRET`

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
