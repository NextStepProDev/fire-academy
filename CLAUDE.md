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
CLAUDE.md · DECISIONS-TRAINING.md · VERSION
```

---

## Baza Danych — Flyway

**Obecny stan: V40 (wszystko na `main`). Kolejna migracja: V41.**
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
| V40 | admin_private_notes — prywatne notatki właściciela (trening 1:1 · zajęcia cykliczne w kalendarzu osoby · slot tygodniowy · termin). **Trzy kolumny celu, cztery cele**: `slot_id` obsługuje dwa, rozróżniane przez `session_date` (zajęcia cykliczne nie mają wiersza nigdzie). Cztery prawdziwe FK z kaskadą zamiast dyskryminatora + cztery **partial** unique. Szczegóły → sekcja „Prywatne notatki właściciela — niezmienniki" |

> 📖 **Pełne uzasadnienia V24–V39 → [`DECISIONS-TRAINING.md`](DECISIONS-TRAINING.md).** Tam leży „dlaczego" (bezpieczniki rozliczeń, kolejność płatności, kontrakty pól, testy pilnujące każdej reguły). Czytaj przed zmianą w danym obszarze.

---

## API Endpoints

### Auth `/api/auth`
`POST /register` · `POST /login` · `POST /logout` · `POST /verify-email?token=` · `POST /resend-verification` · `POST /forgot-password` · `POST /reset-password` · `POST /refresh`

### User `/api/user` (auth required)
`GET /me` (zwraca m.in. `privacyAccepted`, `marketingConsent`) · `PUT /me` · `PUT /me/password` · `DELETE /me` (RODO: anonimizuje całą historię — przyszłe zapisy kasuje (zwalnia miejsce), przeszłe anonimizuje + zeruje `user_id`; ta sama logika co admin → `EnrollmentErasureService.eraseForUser`, wołana PRZED usunięciem konta) · `PUT /me/marketing` (toggle zgody marketingowej `{enabled}`) · `POST /me/consents` (domknięcie zgód po Google: `{acceptedPrivacy, acceptedMarketing}` — polityka obowiązkowa gdy jeszcze nieudzielona) · `PUT /me/language` · `POST /me/avatar` (multipart, kadrowanie po stronie frontu) · `DELETE /me/avatar` · `POST /me/logout-all` (wylogowanie ze wszystkich urządzeń: kasuje własne refresh tokeny + czyści cache filtra JWT; **nie unieważnia natychmiast access tokenów** — sesje gasną do 15 min)
`POST /enrollments` (zapis na wydarzenie z konta — body `{eventId, note}`, dane z profilu; brak telefonu w profilu → `enrollment.phone.required`, front kieruje do `/settings`) · `GET /enrollments` (moje rezerwacje: `{current, past}`, każdy z `canCancel`) · `DELETE /enrollments/{id}` (anulowanie własnego zapisu; blokada <24h; powiadamiany tylko organizator)
`POST /training-slots/{slotId}/enroll` (subskrypcja miesięczna slotu grupowego) · `GET /training-enrollments` (moje treningi cykliczne + rachunek) · `DELETE /training-enrollments/{id}` (rezygnacja; blokada przy opłaconym bieżącym/przyszłym miesiącu → `trainingenrollment.cancel.paid.future`)

### Files `/api/files` (public, cached 7 dni)
`GET /files/{folder}/{filename}` — streaming

> ⚠️ **Folder musi być na białej liście `PUBLIC_FOLDERS` w `FileController`** (`avatars`, `instructors`, `eventtypes`, `eventtypephotos`). Ten endpoint jest bez logowania i publicznie cache'owany, a sam wzorzec ścieżki (`^[a-z]+$`) przepuszczał **dowolny** folder — czyli każdy katalog kiedykolwiek założony pod `uploads/` był światowo czytelny, gdy tylko wyciekła nazwa pliku. Od białej listy **nowy folder jest domyślnie prywatny**. `trainingphotos/` (dane zdrowotne) świadomie **nie ma** tam wstępu — serwują go uwierzytelnione endpointy kalendarza 1:1.

> ⚠️ **Upload jest akceptowany po sygnaturze bajtów, nie po tym, co deklaruje.** Content-Type i rozszerzenie pisze klient. Dekodowanie było prawdziwym sprawdzeniem, ale **JDK nie ma czytnika WebP**, więc plik nieodczytany jest celowo zapisywany bez zmian, żeby webp działał — a to zostawiało `.webp` bez żadnej kontroli treści (dowolne bajty lądowały na dysku). `ImageFormat.sniff` domyka to sygnaturami (JPEG `FF D8 FF`, PNG, RIFF/WEBP), plus wymóg zgodności sygnatury z rozszerzeniem (serwowany Content-Type wynika z rozszerzenia, więc PNG podany jako `.jpg` byłby oddawany jako coś, czym nie jest) i wymóg, żeby JPEG/PNG faktycznie się zdekodował. Uszkodzony obraz to teraz **400, nie 500**. Serwowanie było już utwardzone (wymuszony Content-Type + `nosniff`), dlatego była to luka integralności zapisu, a nie stored XSS. `UploadContentValidationTest` pilnuje każdej z tych reguł. **Dodając format, dopisz sygnaturę** — samo rozszerzenie w `StorePolicy` nie wystarczy.
>
> ⚠️ **Rozmiar w bajtach nic nie mówi o koszcie dekodowania, a sufit na żądanie nie jest sufitem na proces.** JPEG o wadze 1 MB może opisywać obraz 12000×12000, a `ImageIO.read` alokuje ~4 bajty na piksel — ~576 MB w kontenerze z `mem_limit: 384m`, czyli jedno żądanie do OOM-killa. Dlatego `ImageOptimizer` czyta wymiary **z nagłówka, przed dekodowaniem** (`ImageIO.getImageReaders`) i odrzuca powyżej `MAX_PIXELS` → 400. Dotyczy wspólnej ścieżki, więc chroni też avatary i zdjęcia instruktorów.
>
> Sufit to **24 mln pikseli** (6000×4000, czyli pełnoklatkowy aparat przechodzi) i jest **policzony, nie wybrany**: sterta to ~211 MB (55% z 384 MB), więc jedno dekodowanie kosztuje ~96 MB i mieści się obok działającej aplikacji. Poprzednie 40 mln dawało ~160 MB na żądanie — jedno zabierało większość sterty, a **dwa równoległe kończyły proces** (`ExitOnOutOfMemoryError`), i wystarczyło do tego zwykłe konto zmieniające avatar. Dlatego drugą połową poprawki jest semafor `decodePermits`: **dekodowanie idzie pojedynczo** (`CONCURRENT_DECODES = 1`, kolejka fair, 10 s czekania), bo limit per żądanie mnoży się przez liczbę żądań. Przekroczony czas oczekiwania to `ImageDecodeBusyException` → **429 + `Retry-After`**, nie 503: front zamienia 5xx na komunikat „trwa aktualizacja serwisu", co byłoby nieprawdą. Zmieniając którąkolwiek z tych dwóch liczb, przelicz drugą — `ImageOptimizerTest` pilnuje obu (25 mln odrzucone, 6000×4000 wewnątrz limitu, cztery równoległe uploady nigdy nie dekodują naraz).
>
> ⚠️ **Kształt folderu i nazwy pliku egzekwuje warstwa storage, a wzorce leżą w jednym miejscu (`StoragePaths`).** Nazwy powstają wyłącznie w `storeImage` (`UUID` + rozszerzenie) i wracają z bazy, więc dziś żadna nie przychodzi z żądania — i właśnie dlatego to warstwa, a nie filtr. `rootLocation.resolve(folder).resolve(filename)` wychodzi poza katalog uploadów na nazwie z `..` i nic po drodze nie protestuje; warstwa, której bezpieczeństwo trzyma się na tym, że **każdy** wołający uważa, przestaje być bezpieczna przy pierwszej refaktoryzacji, po cichu. Zła nazwa to **wyjątek, nie ciche `false`** — inaczej wygląda jak brakujący plik. `FileController` używa tych samych wzorców, tylko odpowiada 400 zamiast rzucać: dwie kopie reguły bezpieczeństwa się rozjeżdżają, a rozjeżdża się ta, na którą nikt nie patrzy. Dokładając rozszerzenie do `StorePolicy`, dopisz je też do `StoragePaths.FILENAME` — inaczej plik da się zapisać, ale nie da się go potem odczytać ani skasować. Zamiatarka osieroconych plików czyta nazwy **z dysku**, więc nierozpoznaną pomija z ostrzeżeniem w logu (nie kasuje, ale i nie przerywa przebiegu).
>
> ℹ️ **Polityki uploadu: `StorePolicy.DEFAULT` vs `TRAINING_PHOTO`.** Łańcuch kontroli (content-type → rozszerzenie → sygnatura → zgodność → dekodowanie) żyje w **jednej** metodzie `LocalFileStorageService.storeImage`; polityka to parametr, nie druga ścieżka. `TRAINING_PHOTO` dopuszcza **wyłącznie JPEG** i przekodowuje **zawsze** — bo tylko wtedy serwer zna prawdziwe wymiary, zdejmuje EXIF (z GPS) i wie, czym są bajty, które potem odda. WebP przechodzi niezdekodowany (brak czytnika w JDK) i dla danych zdrowotnych to za mało.

### Public `/api/public` (brak auth)
`GET /instructors?category=` · `GET /instructors/{id}` · `GET /event-types?category=` · `GET /event-types/{id}` · `GET /events?category=` · `GET /events/{id}` · `GET /training-slots` (katalog slotów cyklicznych z estymatą ceny) · `GET /training-holidays`
`POST /marketing/unsubscribe` (`{token}` — rezygnacja z marketingu z linku w mailu, bez logowania; idempotentne, zawsze 204 — anti-enumeracja; front: strona `/wypisz-sie?token=`, świadomie POST nie GET, by skanery maili nie wypisywały userów prefetchem) · **`POST /marketing/unsubscribe?token=`** (to samo, wejściem skrzynki — RFC 8058 one-click) · **`GET /marketing/unsubscribe?token=`** (302 na `/wypisz-sie?token=`, **nic nie kasuje**)

> ⚠️ **Jednokliknięciowy wypis (RFC 8058): nagłówki TYLKO na wysyłce masowej, a endpoint ma trzy wejścia do jednej operacji.** Gmail rysuje własny przycisk „Wypisz się" obok nadawcy, gdy znajdzie w mailu `List-Unsubscribe: <https://…>` **oraz** `List-Unsubscribe-Post: List-Unsubscribe=One-Click` — bez drugiego nagłówka przycisku nie ma. Gmail i Yahoo wymagają ich od nadawców masowych od lutego 2024, a brak szkodzi dwutorowo: filtry gorzej oceniają wiadomość, a czytelnik, który nie znajduje wypisu, klika „to spam" — i to ten klik psuje reputację domeny, czyli **zabiera ze sobą dostarczalność maili weryfikacyjnych i resetu hasła**. Trzy reguły, których nie da się odczytać z sygnatur:
> - **Nagłówki lecą wyłącznie tam, gdzie `unsubscribeToken != null`** (`AdminUserMailService` → `BrandedMailSender.sendBulk` → `MailDispatcher.sendBulkHtml`). Nigdy na maile transakcyjne — z weryfikacji adresu ani z potwierdzenia zapisu **nie da się wypisać**, więc zadeklarowanie adresu wypisu byłoby obietnicą, której nie umiemy dotrzymać. Pilnuje `shouldNeverSendServiceMessageAsBulk`.
> - **Adres w nagłówku musi wskazywać API, nie stronę SPA, i token musi siedzieć w URL-u.** Skrzynka POST-uje sama, **nie uruchamia JavaScriptu** i nie ma skąd wziąć ciała JSON — na `/wypisz-sie` dostałaby pusty dokument i nikt nie zostałby wypisany. Stąd dwa różne adresy z jednego tokenu: `/wypisz-sie?token=` dla człowieka i `/api/public/marketing/unsubscribe?token=` dla skrzynki. Działa bez nowej konfiguracji, bo `SITE_URL` jest https i nginx ma `location /api/ → backend:8081`.
> - **Reguła one-click celowo NIE ma `consumes`** — dopasowuje się po samej obecności parametru `token`. Przypięcie do `application/x-www-form-urlencoded` sprawiłoby, że dostawca, który pominie albo zmieni typ treści, spada na handler JSON-owy i dostaje **415**: nikt nie zostaje wypisany, a dostawca notuje nieudany wypis. Skrzynce próbującej kogoś wypisać nie wolno odpowiedzieć błędem. SPA nie wysyła `token` w URL-u, więc dalej trafia w handler JSON.
> - **`GET` przekierowuje i niczego nie kasuje.** Starsze klienty poczty pokazują adres z nagłówka jako zwykły link, a 405 w twarz człowieka, który chciał się wypisać, prowadzi wprost do „to spam". Wypis zostaje POST-em, bo skanery i prefetchery same chodzą po GET-ach — `shouldRedirectAHumanToTheUnsubscribePageWithoutRevokingConsent` tego pilnuje.
> - Nagłówki ustawiane są **wewnątrz pętli ponowień** `MailDispatcher` — każda próba buduje nowy `MimeMessage`, więc ustawione raz obok pętli pojechałyby tylko z pierwszą (czyli z tą, która właśnie się nie udała).
> ⚠️ Zapis gościa (`POST /events/{id}/enroll`) **USUNIĘTY** (V17). Zapis tylko przez konto → `POST /api/user/enrollments`. Niezalogowany klik „Zapisz się" w SPA → redirect na `/logowanie` (returnTo).

### OG `/og` (brak auth, HTML z Open Graph meta tagami dla crawlerów social media)
`GET /` · `GET /{categorySlug}/rodzaj/{id}` · `GET /{categorySlug}/termin/{id}` · `GET /kadra/{id}`
Nginx wykrywa crawlery (Facebook, WhatsApp, Twitter itp.) i proxy detail pages do tych endpointów. Zwraca HTML z `og:title`, `og:description`, `og:image` + meta refresh redirect do SPA.

### SEO (brak auth)
`GET /sitemap.xml` (`SitemapController` — generowana z aktywnych terminów/rodzajów/kadry). Build frontu odpala `scripts/prerender.mjs` (`npm run build` = `tsc -b && vite build && prerender`) — dosypuje statyczne `<head>` dla tras listingowych; przy błędzie loguje `[prerender] skipped` i zostawia zwykły SPA fallback, więc build się nie wywala.

> ⚠️ **GOTCHA — CSP w `nginx.conf` musi wymieniać hosty YouTube, a `frame-src` trzeba napisać wprost.** Biblioteka filmów ładuje miniatury z `img.youtube.com` (przekierowanie na `i.ytimg.com`) i odtwarzacz w `<iframe>` z `www.youtube-nocookie.com`. Bez `img-src`/`frame-src` dla tych hostów podopieczny widzi pustą ramkę zamiast filmu, a Kapitan połamane miniatury — **tylko na produkcji**, bo dev server Vite nie wysyła CSP w ogóle, więc lokalnie i w testach wszystko wygląda dobrze. Brak `frame-src` jest szczególnie zdradliwy: ramki spadają wtedy na `default-src 'self'` i milczą. Pilnuje tego sekcja 4 w `fire-academy-hub/seo-smoke.sh` (odpalana na końcu `deploy.yml`) — dokładając zewnętrzny zasób, dopisz host do CSP i do tego testu.

> ⚠️ **GOTCHA — bot-list w `fire-academy-frontend/nginx.conf` NIGDY nie zawiera wyszukiwarek.** Reguły `if ($http_user_agent ~* ...)` na `/kadra/{id}` i `/(treningi|obozy|szkolenia)/(rodzaj|termin)/{id}` przepisują bota na `/og/*`, a `OgController` zwraca stub z `<meta http-equiv="refresh">` na ten sam URL. Crawler renderujący JS (Googlebot/bingbot/Baidu/Yandex) podąża za refreshem → znowu reguła bota → **pętla = GSC „Redirect error"** + pusta strona (zła dla SEO). Trzymaj tam wyłącznie scrapery social (FB/WhatsApp/Twitter/LinkedIn/Slack/Telegram/Discord); wyszukiwarki muszą trafiać do SPA. (Naprawione 2026-06; ten sam błąd był w climbing.)

### Admin `/api/admin` (ROLE_ADMIN)
`/instructors` — CRUD + categories (CAMP/COURSE/TRAINING) + photo upload + reorder + toggle active
`/event-types` — CRUD + `?category=` + thumbnail + gallery photos + reorder
`/events` — CRUD + `?category=` + toggle active + customName (bez auto-create EventType)
`/enrollments` — lista + admin-add + delete (`DELETE /{id}?notify=` — `notify=false` = ciche usunięcie z archiwum, bez maila o odwołaniu; admin-add ma guard duplikatu `enrollment.already.exists`. **Admin-add tylko dla istniejącego konta** — `AdminEnrollRequest{eventId, userId, note}`, dane uczestnika z konta (front: wyszukiwarka usera przez `GET /users?search=`); duplikat per `user_id+event`)
`/training-slots` — CRUD + `/batch` + `/{id}/deactivate|reactivate` + `/deleted` (archiwum) + `/{id}/cancel-session` (POST/DELETE) + `/cancel-instructor-day` + `/{id}/cancelled-sessions` + `/cancelled-sessions/overview` + `/{slotId}/enrollments` (roster, admin-add)
`/training-enrollments` — `DELETE /{id}` · `DELETE /user/{userId}` · `GET /user/{userId}/history` · `PUT /{id}/payment` (opłacone per slot) · `PUT /{id}/start` (billable_from)
`/training-payments` — `GET` (przegląd miesiąca) · `POST /pay-user/{userId}` (zbiorczo) · `/training-refunds` — `GET` · `GET /unconsumed-credit` · `POST /{id}/settle|unsettle` · `POST /settle-user/{userId}` · `/training-holidays` — `GET|POST|DELETE /{id}`
`/users` — `DELETE /{id}/training-plan` (**trwałe skasowanie planu 1:1**: treningi, wagi, cele, wiadomości, zdjęcia i notatki o tej osobie. **Nie rusza zajęć grupowych, płatności ani konta** — subskrypcja to rozliczenie kogoś, kto dalej chodzi i płaci, a nie dane o zdrowiu. Działa **także gdy flaga `is_athlete` jest już zdjęta** — to właśnie to konto ma dane bez innej drogi wyjścia z panelu. Odróżnij od `DELETE /{id}/athlete`, które **chowa i nic nie kasuje**) · `GET /{id}` (profil: dane + avatar + ustawienia + `currentEnrollments`/`pastEnrollments` — bieżące vs archiwalne po `COALESCE(endDate,startDate)`) · `GET ?search=&page=&size=&sort=&direction=` (lista/wyszukiwanie po imieniu/nazwisku/mailu, **stronicowane** — domyślnie 50/stronę, max 100; zwraca `{content, page, size, totalElements, totalPages}`. Sortowanie: `sort` ∈ {`name`, `email`, `role`, `marketing`, `created`} (whitelist, telefon niesortowalny; `marketing` = po `marketing_consent_at`), `direction` ∈ {`asc`,`desc`}, domyślnie `created`/`desc`. Lista zwraca też `marketingConsent` per user (ikona zgody w UI). **Konta z `ADMIN_HIDDEN_EMAILS` ukryte** — filtr w SQL, by liczniki/paginacja były spójne; te same konta pominięte w wyszukiwarce admin-add i jako adresaci maila zbiorczego) · `POST /email` (`{subject, message, audience, userIds}` — `audience` ∈ {`MARKETING` (tylko zgody marketingowe + auto link rezygnacji w stopce), `ALL` (komunikat serwisowy do wszystkich, bez linku), `SELECTED` (wybrane `userIds`)}; branding+podpis auto, ukryte konta pomijane) · `DELETE /{id}` (bezpieczne usunięcie: przyszłe zapisy usuwane = zwolnienie miejsca, archiwalne anonimizowane, kasowane tokeny+avatar) · `POST /{id}/logout-all` (wymuszone wylogowanie wskazanego konta — np. przejętego; kasuje jego refresh tokeny i czyści cache filtra JWT. Dozwolone także wobec adminów, inaczej niż `DELETE`, które chroni super-admina) · `POST /{id}/promote` (**tylko super-admin z `ADMIN_EMAIL`**) · `POST /{id}/demote` (**tylko super-admin z `ADMIN_EMAIL`**; nie da się zdegradować super-admina ani siebie)

> **Super-admin** = e-mail z `ADMIN_EMAIL` (`AdminEmailConfig.isAdminEmail`). `GET /api/user/me` zwraca flagę `superAdmin` (front pokazuje przyciski nadania i odebrania uprawnień admina tylko jemu). Maile admin→user: `AdminUserMailService` (logo Fire Academy, podpis „Pozdrawiam, Fire Academy", temat bez HTML-escape).

> ⚠️ **Szkielet HTML maila istnieje w JEDNYM miejscu — `BrandedMailSender`.** Wszystkie cztery serwisy (`AuthMailService`, `AdminUserMailService`, `EnrollmentMailService`, `TrainingMailService`) renderują przez niego; nie dopisuj lokalnego szablonu „bo tylko ten jeden mail jest inny". Trzy z czterech miały własne kopie tego samego szkieletu (te same kolory, ta sama ramka 600px, ta sama stopka) — nic się nie psuło i na tym polegał problem: przestylowanie wymagało znalezienia czterech kopii, a pominięta oznaczała klasę maili wyglądającą inaczej. Wybór logo robi `category` (CAMP → Fire Camp, reszta → Fire Academy); maile bez sekcji (konto, wiadomości organizatora) wołają `academyTemplate(...)`, żeby nie udawać kategorii, której nie mają. Flaga `signOff` jest istotna: `EnrollmentMailService` i `AdminUserMailService` wołają z `false`, bo podpis mają wklejony we własną treść — przełączenie na wariant automatyczny dokleiłoby im drugi. `SharedMailTemplateTest` pilnuje, że każdy serwis wypuszcza wspólną ramkę.

`/notes` — prywatne notatki właściciela: `GET|PUT|DELETE /{training|event|slot|session}/{id}` (dla `session` dodatkowo `?athleteId=&date=`) · `GET /markers?from=&to=&athleteId=` (same identyfikatory, nigdy treść; `from`/`to` opcjonalne — listy admina są otwarte w obie strony, kalendarz zawsze podaje okno). **Rodzaj celu to segment ścieżki, nie cztery rodziny endpointów.** Szczegóły i niezmienniki → sekcja „Prywatne notatki właściciela"

### Kalendarz treningów 1:1 — `pl.fireacademy.api.trainingcalendar`
> Kontrolery trenera i podopiecznego siedzą w **jednym pakiecie** (nie w `api/admin`), żeby dzielić rekordy DTO — obie role dostają identyczny kształt odpowiedzi, bo front renderuje jeden komponent dla obu. Ochrona ról bierze się ze **ścieżki**, nie z pakietu.

**Trener** `/api/admin` (ROLE_ADMIN):
`GET /athletes` (roster; nieprzeczytane najpierw) · `POST|DELETE /users/{id}/athlete` (flaga)
`GET|POST /personal-trainings` (`?athleteId=&from=&to=`) · `PUT|DELETE /personal-trainings/{id}` · `POST /{id}/duplicate` · `POST /paste` (`{sourceId, targetDate, mode: COPY|MOVE, targetAthleteId}` — **`targetAthleteId` = kalendarz otwarty na ekranie, nie właściciel źródła**; brak pola = ten sam podopieczny co źródło. Kopiowanie między podopiecznymi robi się właśnie tym polem)
`GET|POST /personal-trainings/{id}/comments` · `POST /personal-trainings/mark-seen?athleteId=` · `POST /personal-trainings/deletions/dismiss?athleteId=`
`POST /training-photos` (multipart `trainingId`,`file`,opc. `body` — **osobny prefiks pod kubełek rate-limitu**) · `GET|DELETE /personal-trainings/comments/{commentId}/photo`
`GET /personal-trainings/stats?athleteId=` (**z sygnałem przetrenowania**) · `GET /personal-trainings/weights?athleteId=&range=` (read-only, **jedyne miejsce z ostrzeżeniem o szybkim spadku**; brak zapisu — patrz V34)
`GET|POST /personal-trainings/goals` · `PUT|DELETE /personal-trainings/goals/{id}` · `POST /personal-trainings/goals/{id}/achieve` · `POST /personal-trainings/goals/{id}/reopen` (**tylko osiągnięcie automatyczne**)
`GET|POST|PUT|DELETE /exercise-videos[/{id}]` · `GET /exercise-videos/search?query=` · `POST /exercise-videos/{id}/archive|/restore` · `GET|POST|PUT|DELETE /training-templates[/{id}]`

**Podopieczny** `/api/user/my-training` (auth + flaga `is_athlete`; **brak flagi → 404, nie 403**):
> 🔒 **Zgoda RODO art. 9 bramkuje całą tę ścieżkę.** `TrainingConsentInterceptor` (rejestrowany w `WebConfig` na `/api/user/my-training/**`) zwraca **409**, dopóki podopieczny nie zaznaczy zgody — dzięki temu każdy nowy endpoint klienta jest chroniony domyślnie. Kolejność w interceptorze jest istotna: **brak flagi przepuszcza dalej** (żeby `TrainingAccessService` dał swoje 404 — 409 zdradziłoby istnienie funkcji), 409 leci wyłącznie flagowanemu bez zgody. Wyjątek: `/summary` (sam licznik do badge'a, odpytywany zanim zgoda w ogóle padnie). Zgodę zapisuje `POST /api/user/me/training-consent` — leży przy pozostałych zgodach, bo endpoint zdejmujący blokadę nie może za nią stać. Sekcja 5 polityki prywatności (`id="plan-treningowy"`) to cel linku z ekranu zgody — nie zmieniać id ani numeracji bez poprawienia linku.
> ⚠️ **Poszerzenie zakresu danych = nowa zgoda, nie dopisek.** V39 dołożyło zdjęcia (dane zdrowotne) i dlatego **wyzerowało `training_consent_at` wszystkim** — zgoda udzielona pod starym tekstem nie obejmuje nowego zakresu. Dokładając cokolwiek do listy `consent.items`, zrób to samo, inaczej zgoda przestaje odpowiadać temu, co realnie przetwarzamy.
`GET /calendar?from=&to=` (te same DTO co u trenera) · `GET /summary` (badge) · `POST|PUT|DELETE /trainings[/{id}]` · `POST /trainings/{id}/duplicate` · `POST /trainings/paste`
> ⚠️ **Wpis od trenera jest dla podopiecznego tylko do odczytu.** `PUT`, `DELETE`, `duplicate` i `paste` (jako źródło) zwracają **409** (`personaltraining.coach.readonly`), gdy `created_by_admin = true`, a woła je podopieczny. Zostaje odhaczanie i komentarze. Reguła jest **jednokierunkowa** — trener rusza cały plan, łącznie z wpisami dodanymi przez podopiecznego. Klucz to `created_by_admin` (stały od utworzenia), **nigdy `last_modified_by_admin`** (przeskakuje przy każdym odhaczeniu)
`POST|DELETE /trainings/{id}/complete` (**odhaczanie to akt podopiecznego — nie ma odpowiednika u trenera**; `rpe` wymagane przy treningu, zabronione przy zadaniu → 400) · `GET|POST /trainings/{id}/comments`
`POST /photos` (multipart `trainingId`,`file`,opc. `body`) · `GET|DELETE /comments/{commentId}/photo`
> 📷 **Zdjęcia w komentarzach** (zrzut z zegarka). Wrzucają **obie strony**. Wyłącznie **JPEG**, przekodowywany po stronie serwera (`StorePolicy.TRAINING_PHOTO`: 1280 px / q0.75 / cap 1,5 MB), maks. **3 na trening** → 409, oraz **25 dziennie na kalendarz podopiecznego** → 409.

> ⚠️ **Dzienny limit liczy się PER PODOPIECZNY, nie per wrzucający.** Limit „3 na trening" nie ogranicza niczego w skali konta: podopieczny zakłada dowolnie wiele treningów, a każdy otwiera trzy kolejne miejsca — a zdjęcia dzielą dysk z bazą, więc rosnący bez sufitu folder kończy się nie komunikatem „brak miejsca na zdjęcia", tylko Postgresem odmawiającym zapisu. Kluczowy jest jednak **kształt** limitu: trener wrzuca do **wielu kalendarzy naraz** (dwudziestu podopiecznych po trzy zdjęcia to sześćdziesiąt), więc sufit „na konto, które wrzuca" zatrzymałby trenera na siódmej osobie, nie dotknąwszy żadnego podopiecznego. Liczony per kalendarz, ta sama sesja zostawia każdy licznik na 3. Zdjęcia trenera **wliczają się do dnia podopiecznego** — inaczej limit dałoby się obejść, a plik i tak zajmuje to samo miejsce. Doba **kalendarzowa** w strefie klubu (JVM chodzi na `Europe/Warsaw`), nie ruchome 24 h: „wyczerpałeś dzisiejszy limit" to odpowiedź, a „część wrzuciłeś dziewiętnaście godzin temu" to zagadka. Liczone są **wiersze istniejące**, więc skasowanie zdjęcia zwalnia miejsce w limicie — i słusznie, bo zwalnia też miejsce na dysku. Serwowane z `Cache-Control: private, no-store` — **nigdy przez `/api/files`**. Kasuje **autor swojego** albo **trener dowolnego**; tekst komentarza zostaje, komentarz bez tekstu znika w całości. Retencja **30 dni** (`TrainingPhotoRetentionScheduler`, `0 45 3 * * *`) + nocne zamiatanie osieroconych plików (siatka bezpieczeństwa pod każdą nieudaną transakcją).
`POST /mark-seen` · `POST /deletions/dismiss` · `GET /goals` (read-only) · `GET /stats` (**bez** sygnału przetrenowania — pole nie istnieje w JSON, nie jest `false`)
`GET /weights?range=` (`QUARTER|YEAR|ALL`, domyślnie QUARTER; bez ostrzeżenia o szybkim spadku) · `PUT /weights` (upsert per dzień; **ten sam request domyka cele wagowe** — ocena odpala się tu, nie schedulerem) · `DELETE /weights/{date}`

### Dev `/api/dev` (profil `dev` only)
`POST /login` · `GET /users`

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

## Autentykacja

- Email/password: rejestracja → weryfikacja email → login → JWT
- JWT: access (15 min) + refresh (7 dni), algorithm HS256
- OAuth2 Google (opcjonalny): aktywacja przez profil `oauth2` (`SPRING_PROFILES_ACTIVE=dev,oauth2`), wymaga `OAUTH2_GOOGLE_CLIENT_ID` + `OAUTH2_GOOGLE_CLIENT_SECRET` w `.env`

> ⚠️ **Na produkcji Google jest WYŁĄCZONE, a przycisk bramkuje jedna flaga: `GOOGLE_LOGIN_ENABLED` w `frontend/src/config/features.ts`.** Backend OAuth istnieje od pierwszego szkieletu (2026-05-21) i ma testy, ale prod chodzi bez profilu `oauth2` i bez kluczy — więc `/oauth2/authorization/google` odpowiada tam **401**. Przycisk przez trzy miesiące nie miał żadnej bramki i był renderowany bezwarunkowo **na ekranie logowania i rejestracji**, czyli surowy JSON z błędem dostawał ktoś, kto właśnie zakładał konto. Tryb awarii jest cichy z czterech powodów naraz: w dev z profilem `oauth2` wszystko działa, testy OAuth są zielone, błąd widać wyłącznie po kliknięciu **tego** przycisku na prodzie (my logujemy się hasłem), a trafiają na niego głównie nowe osoby — czyli te, które nie mają jeszcze konta, żeby cokolwiek zgłosić.
>
> Ta sama flaga steruje **polityką prywatności**: logowanie Google jest tam opisane w całości (sekcja 2 — zakres danych od Google, sekcja 3 — podstawa prawna, sekcja 7 — Google jako odbiorca i transfer poza EOG), ale oznaczone `— w przygotowaniu` helperem `notYet(...)`. Uruchomienie funkcji to **jedna linia**: `false` → `true` zdejmuje markery z polityki i zamienia martwy przycisk w link. Polityka opisuje stan faktyczny, więc dopóki flaga jest wyłączona, markery **muszą** zostać — klauzula informacyjna ma być prawdziwa w chwili, gdy ktoś zakłada konto, a nie dopiero po wdrożeniu.
>
> ⚠️ **Kolejność włączania jest istotna, bo pomyłka kładzie całą aplikację, nie samo logowanie.** `application-oauth2.yml` rozwija `${OAUTH2_GOOGLE_CLIENT_ID}` i `${OAUTH2_GOOGLE_CLIENT_SECRET}` **bez wartości domyślnych**, więc profil `oauth2` bez kluczy w `.env` = Spring nie wstaje. Najpierw klucze na serwerze, potem profil, dopiero na końcu flaga. Do tego **adres powrotny musi być zarejestrowany w konsoli Google co do znaku** — `redirect-uri` to teraz `${app.site-url}/login/oauth2/code/{registrationId}`, czyli na prodzie `https://fireworkout.pl/login/oauth2/code/google`, a w dev `http://localhost:5174/...` (Vite proxy'uje to na :8081).
>
> ✅ Dwie rzeczy z audytu, które siedziały na tej ścieżce, są **domknięte**: (1) nginx ustawia `X-Forwarded-Host` i `X-Forwarded-Proto` **sam**, z własnych zmiennych, a `redirect-uri` nie jest już `{baseUrl}` — wcześniej adres powrotny dawało się nagiąć nagłówkiem żądania, bo backend chodzi z `forward-headers-strategy: framework` i wierzy w te nagłówki przy budowaniu adresów; (2) tokeny wracają z `/oauth-callback` **we fragmencie URL-a**, nie w query stringu — fragment nie jest wysyłany na serwer, więc siedmiodniowy refresh token nie ląduje w logach nginxa ani Cloudflare, a strona kasuje go z paska adresu zaraz po odczycie. Bramka `src/__architecture__/nginxForwardedHeaders.test.ts` wywala build, gdy nowy blok `location` proxy'uje na backend bez kompletu tych nagłówków.
- **Auto-admin:** email z `ADMIN_EMAIL` (env var) automatycznie dostaje ADMIN przy rejestracji
- Account lockout: 5 failed attempts → 15 min lockout
- Rate limiting: **per-IP per-kubełek**, nie per-endpoint. `RateLimitFilter` trzyma **jedną uporządkowaną tabelę reguł** (`RULES`: kubełek + limit + predykat w jednym wierszu, **pierwsza pasująca wygrywa**), a nie dwie równoległe drabinki ifów — bo dwie listy warunków, które muszą mieć tę samą kolejność, rozjeżdżają się po cichu i wtedy żądanie liczy się do jednego kubełka, a mierzy limitem drugiego. Kolejność od najbardziej szczegółowego: **`upload` 12/min** (`/api/user/my-training/photos`, `/api/admin/training-photos`, `/api/user/me/avatar`) · `auth` 15/min (`/api/auth` + **cały handshake Google**: `/oauth2`, `/login/oauth2` — obie nogi kończą się sesją, więc idą tam, gdzie reszta sposobów jej zdobycia) · **`mytraining` 120/min** · `user` 40/min · `admin` 60/min · **`files` 240/min** · `public` 120/min (`/api/public`, `/og`, `/sitemap.xml`) · **`default` 120/min**. Odpowiedź 429 niesie `Retry-After: 60`. Kalendarz 1:1 ma własny kubełek, bo samo przeglądanie generuje więcej żądań niż cała reszta `/api/user/**` razem wzięta. Kubełek `upload` racjonuje **bajty, nie żądania** — multipart jest parsowany do pamięci (do 10 MB), zanim handler zdąży cokolwiek odrzucić; **odczyt zdjęć celowo w nim nie siedzi**. **Reguła musi wymieniać KAŻDY endpoint multipart** — pominięty nie sypie błędem ani ostrzeżeniem, tylko mierzy się luźniejszym sufitem swojego prefiksu, czyli ration chroniący pamięć zasłania część drzwi do tej samej kosztownej operacji, a część nie. Tak avatar (`POST /api/user/me/avatar`) siedział przez miesiące w kubełku `user` (40/min): regułę pisano przy dokładaniu zdjęć do treningów i wymieniono w niej dwie ścieżki, które wtedy istniały. Dokładając upload, dopisz go tutaj. Kubełek `files` jest wysoki, bo galeria pobiera kilkanaście plików naraz — Cloudflare osłania trafienia, ale żądanie o nazwę, której nie ma na dysku, **za każdym razem pudłuje w cache brzegowym** i ląduje na originie
- ⚠️ **429 ani błąd sieci NIGDY nie kończy sesji.** Sesję kończy wyłącznie **401/403 od serwera** — nic innego. Domyślną odpowiedzią na każdą inną awarię jest **zachować tokeny**, i to wymaga dowodu pozytywnego: „to nie jest `ApiError`" (błąd sieci, timeout, abort) znaczy **„nie wiem"**, a nie „wyloguj". Asymetria jest cała rzecz: zostawiony martwy token nic nie kosztuje, bo następne żądanie dostanie 401, pójdzie odświeżenie i **ta** ścieżka zakończy sesję, jeśli serwer naprawdę odmawia — a wyczyszczone tokeny są nieodwracalne i użytkownik z ważnym kontem ląduje na ekranie logowania. Tak to wyglądało w climbing (ten sam template): admin kliknął szybko po panelu, dostał 429, po czym został wylogowany. Decyzję podejmują **dwa** miejsca i oba czytają `ApiError.isAuthRejection`: `AuthContext.endSessionOnlyIfRejected` (oba catche — start aplikacji i `refreshUser`) oraz `doRefresh` w `api/client.ts`. Dlatego `ApiError` leży w `utils/errors.ts`, nie w `api/client.ts`: rzuca ją też `api/auth.ts` (czyli `refreshTokens`), a import w drugą stronę byłby cyklem — bez tego odświeżanie nie ma z czego poznać, co się stało, i widzi tylko goły `Error` z tekstem. Trzy konsekwencje, które łatwo złamać: (1) przejściowy rzut po nieudanym odświeżeniu (`refreshUnavailable`) **nie może nieść statusu 401/403**, bo ten sam obiekt trafia do catcha w `AuthContext` i zostałby odczytany jako odmowa — niesie status tego, co faktycznie padło (429/502), a przy padniętej sieci 503; (2) `retry` w `main.tsx` (`shouldRetryQuery`) **nie ponawia 429** — okno limitera to stała minuta, więc ponowienie nie ma prawa się udać i tylko dokłada żądanie do pełnego kubełka; (3) reaktywne 401 idzie przez `refreshOnce`, nie `doRefresh` — inaczej N równoległych 401 to N POST-ów na `/api/auth` (15/min), czyli aplikacja sama produkuje 429, po którym się wylogowywała. Pilnują tego `src/api/session.test.ts` i `src/context/AuthContext.test.tsx`
- ⚠️ **Odmowa tokenu odświeżania musi wyjść jako 401 — dlatego `InvalidRefreshTokenException`.** Trzy przypadki w `AuthService.refreshTokens` (token nieprawidłowy · zły typ · odwołany/rotowany poza okno łaski) rzucały goły `IllegalArgumentException`, a ten mapuje się globalnie na **400**. Przy regule „sesję kończy tylko 401/403" 400 znaczy „przejściowe", więc **martwy token nigdy by nie wylogował** — aplikacja twierdziłaby, że jesteś zalogowany, a każde żądanie by padało, i jedynym wyjściem byłoby ręczne czyszczenie localStorage (przed poprawką ślepy `catch` wylogowywał tu poprawnie, przez przypadek). Wyjątek dziedziczy po `IllegalArgumentException` (żeby nie przeklasyfikowywać błędu ani nie ruszać wołających), a własny `@ExceptionHandler` daje mu **401 + `code: INVALID_REFRESH_TOKEN`**; Spring wybiera handler po hierarchii klas, nie po kolejności. **Nie używaj go do niczego, co tylko nie dostało odpowiedzi.** Status pilnują dwa testy w `AuthControllerIntegrationTest` — kontrakt jest międzywarstwowy, więc zmiana statusu tutaj cicho psuje logowanie na froncie
- **Filtr domyślnie limituje, nie przepuszcza.** Wszystko pod `/api`, czego nie objęła żadna reguła, wpada w kubełek `default` (120/min, hojnie — ma zatrzymać skrypt, nie zaskoczyć biura za jednym NAT-em). Wcześniej brak dopasowania znaczył **brak jakiegokolwiek limitu**, więc każdy nowy kontroler startował nielimitowany i nic tego nie sygnalizowało. Catch-all kończy się na `/api` **świadomie**: `/actuator/health` musi zostać bez limitu, bo healthcheck kontenera wali w niego z jednego adresu co kilka sekund, a 429 zrzuciłby kontener do `unhealthy`
- **Dopasowanie ścieżek idzie przez `under(path, base)`** = `path.equals(base) || path.startsWith(base + "/")`. Sam `startsWith("/api/user/")` **nie łapie** endpointu zamapowanego na gołej bazie (URI dokładnie `/api/user`) — w bliźniaczej apce (climbing) dokładnie tak najcięższe zapytanie chodziło bez limitu przez miesiące, pod regułą, która wyglądała na kompletną. Wymóg separatora po stronie podścieżki nadal nie pozwala `/api/users-export` udawać `/api/user`
- **Bramka `architecture/RateLimitCoverageTest`** czyta z dysku wszystkie klasowe `@RequestMapping` z `api/**Controller.java` i wywala build, gdy: baza nie ma żadnego kubełka · baza wpada w `default` (wyjątki wyłącznie przez jawną allowlistę `INTENTIONALLY_GENERIC`, dziś tylko `/api/dev`) · baza i jej podścieżka trafiają w **różne** kubełki. Osobny test-strażnik pilnuje, że regex faktycznie coś znajduje — inaczej bramka przechodziłaby pusto i wyglądałaby identycznie jak taka, która nic nie sprawdza. **Dokładając kontroler, dopisz mu regułę w `RULES`** albo świadomie zostaw go w `default` i zapisz to w allowliście
- `app.rate-limit.enabled` (domyślnie `true`) gasi filtr **wyłącznie na czas testu obciążeniowego** z jednego adresu, gdzie limiter mierzyłby sam siebie zamiast aplikacji. Nie ma go ani w profilu `dev`, ani w compose — trzeba podać jawnie
- **Limit maili per adres, nie tylko per IP** (`AuthService.claimMailQuota`): `forgot-password` i `resend-verification` wysyłają wiadomość do **obcej** skrzynki na żądanie, więc sam limit per IP ich nie ogranicza — z rotujących adresów IP dało się bombardować cudzą skrzynkę i przy okazji spalić dobowy limit relaya Gmail (500), po czym **nikt** nie dostaje weryfikacji ani resetu. Wspólny licznik **3/godzinę na adres odbiorcy** (Caffeine w pamięci), liczony **tylko gdy mail faktycznie by poszedł**. Po wyczerpaniu odpowiedź jest **identyczna** jak zwykle — komunikat „za dużo maili" byłby wyrocznią zdradzającą, że konto istnieje (te endpointy są celowo anti-enumeracyjne)
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
> ⚠️ **Obraz backendu ma domyślny profil `prod`, nie `docker`.** Wcześniej `Dockerfile` ustawiał `SPRING_PROFILES_ACTIVE=docker` — profil, którego **żaden `application-docker.yml` nigdy nie definiował**. Na produkcji ratował to compose (`${SPRING_PROFILES_ACTIVE:-prod}`), ale każde uruchomienie obrazu bez compose (debug na serwerze, nowe środowisko) startowało po cichu **bez ustawień produkcyjnych**: pula 8 zamiast 5, brak `max-connections` broniącego pamięci, gadatliwe logi i CORS na `http://localhost:*`. Nic nie ostrzegało. Domyślna wartość ma być tą samą odpowiedzią co compose, nie pułapką.

- **JVM tuning backendu (mem_limit 384m).** ENTRYPOINT w `fire-academy-backend/Dockerfile`: `-XX:MaxRAMPercentage=55.0` (~211 MB heap; + non-heap ~120 MB mieści się w 384 MB z zapasem — przy 75% było ~288 MB heap → ~408 MB > limit = ryzyko OOM-kill i wypychania bezczynnego heapu do swapu), `-XX:MaxMetaspaceSize=128m`, `-XX:+ExitOnOutOfMemoryError`, `-XX:TieredStopAtLevel=1` (C1-only JIT — skraca start na 2 vCPU; wdrożone 2026-06-20, start 103→64 s). Kontener chodzi jako **non-root** (`USER app`). **Bez wymuszonego `-XX:+UseG1GC`** — poniżej 2 GB RAM JVM ergonomicznie wybiera lekszy SerialGC (mniej pamięci natywnej niż G1 na ciasnym boxie). `$JAVA_OPTS` zachowany jako passthrough; w `docker-compose.prod.yml` `JAVA_OPTS=""` (po upgradzie RAM można tam wstawić `-XX:+UseG1GC`).
- **Mail health poza liveness probe.** `management.health.mail.enabled=false` w `application.yml` (domyślnie, niezależnie od env). Wolny SMTP (~10 s) przekraczał 5 s timeout docker healthchecka `/actuator/health` → fałszywe „unhealthy" → zbędny restart. Maile nie są liveness-critical. (W compose env `MANAGEMENT_HEALTH_MAIL_ENABLED=false` zostaje jako redundantny, jawny override.)

### Kopie zapasowe

`fire-academy-hub/fire-academy-backup.sh` — cron roota 03:00, zrzut całej bazy + wolumen uploadów,
wysyłka na `gdrive-crypt:` (**remote typu `crypt`**, więc rclone szyfruje przed wysłaniem — to jest
to, co obiecuje sekcja 7 polityki prywatności). Lokalnie 7 dni, na Dysku 90.
**Odtwarzanie: [`fire-academy-hub/RESTORE.md`](fire-academy-hub/RESTORE.md).** Skrypt jedzie na
serwer przez `deploy.yml`, tą samą drogą co `nginx.conf` — wcześniej istniał **wyłącznie** na
maszynie produkcyjnej, czyli był jedynym plikiem w systemie kopii bez własnej kopii.

> ⚠️ **`rclone copy`, NIGDY `sync`.** `sync` doprowadza cel do identyczności ze źródłem, **łącznie
z kasowaniem** — a skrypt przycina lokalnie do 7 dni, więc następnego dnia `sync` usuwał te same
pliki na Dysku. Efekt był podwójny: realne archiwum to było 7 dni (błąd zauważony po tygodniu =
nie ma z czego wracać), a cokolwiek zniszczyłoby `/backups` na serwerze propagowało się do chmury
w ciągu doby — czyli kopia off-site chroniła przed wszystkim oprócz katastrofy, dla której powstała.
Zdalną historię przycina osobna, dużo wolniejsza linijka (`rclone delete --min-age 90d`).

> ⚠️ **Zrzut dostaje właściwą nazwę dopiero po sprawdzeniu markera `PostgreSQL database dump
complete`.** Powłoka tworzy plik, zanim `pg_dump` cokolwiek odda, więc przerwany zrzut zostawia coś,
co wygląda dokładnie jak kopia. **Sam test gzipa tego nie łapie** — obcięty zrzut zwykle jest
poprawnym plikiem gzip (sprawdzone: `gunzip -t` przechodzi), po prostu SQL w środku urywa się w
połowie. Praca idzie do `.part`, marker jest jedynym tanim dowodem, że baza doszła do końca. Archiwum
plików analogicznie: `tar tzf` czyta je z powrotem, zanim dostanie prawdziwą nazwę.

> ⚠️ **Cisza jest awarią, więc pilnujemy ciszy.** `set -e` kończy skrypt bez słowa, a poczta crona
do roota na maszynie w chmurze nie dociera nigdzie — kopie potrafią przestać powstawać w marcu i
zostać zauważone w sierpniu. Skrypt pinguje zewnętrzny monitor po **udanym** przebiegu i na `/fail`
przy błędzie; brak sygnału zapala alarm po ich stronie. To wykrywa też przypadki, których żaden mail
o błędzie nie zgłosi: wyłączonego crona, wyłączoną maszynę, skasowany skrypt. URL to sekret —
`/etc/fire-academy-backup.env` na serwerze (chmod 600), **nigdy w repo**; brak pliku = ping po cichu
pomijany, więc skrypt działa i bez niego.

### CI/CD (GitHub Actions)
- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main + **skan CVE obrazu (Trivy, HIGH/CRITICAL, `ignore-unfixed`) wywala build** — tylko na merge'u do `main`, nie blokuje `deploy.yml`; znalezisko = czerwony build proszący o bump obrazu bazowego. Nie wracać do `exit-code: 0` (skan, który nic nie blokuje, to skan, którego nikt nie czyta) — jeśli szumi, zawężać `severity`
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

## Kalendarz treningów 1:1 — pułapki

Zasady, które łatwo po cichu złamać przy kolejnej zmianie. Każda ma test, który to wyłapie.

**Nakładka cykliczna NIGDY nie jest materializowana.** Sesje grupowe na kalendarzu 1:1 liczy `RecurringSessionOverlayService` przy każdym żądaniu, z tego samego kodu co rachunek (`TrainingBillingService`). Zapisanie ich jako wierszy w `personal_trainings` wygląda prościej przez jedno popołudnie i kosztuje na zawsze: dzień wolny, odwołane zajęcia, rezygnacja w połowie miesiąca, dezaktywacja slotu od 15. — każde z nich musiałoby polować na wygenerowane wiersze, a każde pudło to kalendarz niezgodny z rachunkiem. `RecurringOverlayIntegrationTest` sprawdza, że po pobraniu strony `SELECT count(*) FROM personal_trainings` = 0.

**Koszt nakładki to 3 zapytania niezależnie od zakresu.** API zakresowe w `TrainingBillingService` (`closedDatesInRange` wsadowe, czyste `sessionDatesInRange`, `addDeactivationDates`) istnieje właśnie po to. Test porównuje liczbę zapytań dla tygodnia i dla 6-tygodniowej siatki — regresja do pobierania per miesiąc wywala build.

**Ekrany rozliczeniowe pobierają dni zamknięte RAZ na stronę, nie raz na osobę.** `sessions`/`amount`/`firstSessionDate`/`isPaymentOverdue`/`partialStartDate` mają po dwa warianty: bez `closed` (wygodny dla jednej subskrypcji, kosztuje 2 zapytania) i z `closed` (dla listy). Roster slotu i miesięczny przegląd płatności liczą `closedBySlotForMonth(...)` raz i przekazują wynik dalej — dni wolne i odwołania zależą od slotu i miesiąca, **nigdy od osoby**, więc wersja per-wiersz mnożyła te same dwa zapytania przez liczbę uczestników. Tak samo `creditService.availableBalance` liczy się raz na wiersz i wchodzi do `liveAppliedFor(te, month, balance)`, a `netFor` dostaje gotowy wiersz płatności zamiast go dociągać. Zmierzone na rosterze: **9 → 2 zapytania na każdego kolejnego uczestnika** (strona dla 3 osób: 31 → 12). Zostające 2 to saldo nadpłaty, które faktycznie jest per osoba.

**Ekran „Moje treningi" (`/moje-konto/treningi`) liczy tak samo.** `getMyEnrollments` pobiera dni zamknięte **raz na stronę** dla zbioru potrzebnych miesięcy (miesiąc rozliczeniowy każdego wiersza + jego podgląd następnego), a saldo nadpłaty i wiersz płatności czyta raz i przekazuje dalej zamiast pozwalać, by `creditService` dociągnął je sobie ponownie. Wcześniej jedna linijka rachunku kosztowała dziewięć zapytań.

**Ta sama reguła dotyczy dwóch archiwów**, które rosną w nieskończoność i zwracają wszystko naraz: przegląd odwołanych zajęć (`getCancelledOverview`, 5 → 1 zapytanie na wiersz) i archiwum usuniętych slotów (`getDeletedSlots`, 1 → 0). Uczestnicy, ich płatności i nierozliczone zwroty zależą od `(slot, miesiąc)` albo od niczego — pobiera się je raz na stronę. Zostające 1 w przeglądzie to `isSessionRestorable`: decyzja wymaga wierszy zwrotów tej konkretnej sesji, a wciągnięcie tego do wsadu oznaczałoby przebudowę silnika zwrotów — świadomie zostawione.

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
