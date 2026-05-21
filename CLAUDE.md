# Projekt 1

System webowy — Java/Spring Boot backend + React frontend. Dark mode, Tailwind 4.

**Wersja:** odczytywana z pliku `VERSION` via `@version@` placeholder w `application.yml`

---

## Stack

### Backend
- **Java 25** + **Spring Boot 4.0.2**
- Spring Security + JWT (jjwt 0.12.6), Spring Data JPA + **PostgreSQL 17**
- Spring Boot Starter Mail, **Actuator** (health check), Cache + Caffeine, Flyway, DevTools (dev)
- **JSpecify 1.0.0** (@NullMarked), **springdoc-openapi 2.8.4**
- **Testcontainers 1.20.4** + JUnit 5

### Frontend
- **React 19.2** + **TypeScript 5.9**, **Vite 7.2**
- **Tailwind CSS 4.1**, **TanStack React Query 5.90**, **React Router 7.13**
- **date-fns 4.1**, **lucide-react 0.563**, **clsx 2.1**, ESLint 9

### Struktura repozytorium
```
projekt-1-backend/
projekt-1-frontend/
projekt-1-hub/          # Docker Compose (dev/prod), .env
.github/workflows/      # CI/CD
VERSION
```

---

## Baza Danych — Flyway

**Obecny stan: V1. Kolejna migracja: V2.**

| Wersja | Co dodaje |
|--------|-----------|
| V1 | users, auth_tokens |

---

## API Endpoints

### Auth `/api/auth`
`POST /register` · `POST /login` · `POST /verify-email?token=` · `POST /resend-verification` · `POST /forgot-password` · `POST /reset-password` · `POST /refresh`

### User `/api/user` (auth required)
`GET /me` · `PUT /me` · `PUT /me/password` · `DELETE /me`

### Files `/api/files` (public, cached 7 dni)
`GET /files/{folder}/{filename}` — streaming

### Dev `/api/dev` (profil `dev` only)
`POST /login` · `POST /logout` · `GET /session` · `GET /users`

---

## Autentykacja

- Email/password: rejestracja → weryfikacja email → login → JWT
- JWT: access (15 min) + refresh (7 dni), algorithm HS256
- OAuth2 Google: wymaga `OAUTH2_GOOGLE_CLIENT_ID` + `OAUTH2_GOOGLE_CLIENT_SECRET` w `.env`
- **Auto-admin:** email z `ADMIN_EMAIL` (env var) automatycznie dostaje ADMIN przy rejestracji
- Account lockout: 5 failed attempts → 15 min lockout
- Rate limiting: per-IP per-endpoint

---

## Infrastruktura

### Dev Ports
- PostgreSQL: 5433
- Backend: 8081
- Frontend (Vite): 5174
- MailHog SMTP: 1026, Web UI: 8026

### CI/CD (GitHub Actions)
- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main
- `deploy.yml`: ręczny trigger → SSH → `docker compose pull && up -d`

### Zmienne środowiskowe (`.env`)
`POSTGRES_DB/USER/PASSWORD`, `MAIL_HOST/PORT/USERNAME/PASSWORD`, `JWT_SECRET`, `GHCR_OWNER`, `VERSION`, `OAUTH2_GOOGLE_CLIENT_ID/SECRET`, `ADMIN_EMAIL`

---

## Testy

```bash
./gradlew test                                    # Wszystkie testy
./gradlew test --tests "JwtServiceTest"           # Konkretna klasa
```

**Naming:** `shouldDoSomethingWhenCondition()`, struktura Given/When/Then
