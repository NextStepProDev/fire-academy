# Fire Academy — infrastruktura

> Wydzielone z `CLAUDE.md`. Serwer produkcyjny, JVM, kopie zapasowe, CI/CD, zmienne środowiskowe.
> Porty dev zostały w `CLAUDE.md` (sekcja „Local Dev Workflow").

## Infrastruktura

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
- `ci-backend.yml` / `ci-frontend.yml`: testy przy push/PR na main + **skan CVE obrazu (Trivy, `ignore-unfixed`)**. Uwaga na dwa progi, bo to nie jest jeden krok: **raport SARIF bierze HIGH i CRITICAL i nigdy nie wywala builda** (`exit-code: 0`, ląduje w zakładce Security), a **bramka bierze wyłącznie CRITICAL i wywala** (`exit-code: 1`). Czyli HIGH jest widoczne, ale nie blokuje — świadomie, żeby build nie czerwienił się od podatności, na które nie ma jeszcze łatki w obrazie bazowym. Tylko na merge'u do `main`, nie blokuje `deploy.yml`; znalezisko = czerwony build proszący o bump obrazu bazowego. Nie wracać do `exit-code: 0` w bramce (skan, który nic nie blokuje, to skan, którego nikt nie czyta) — jeśli szumi, zawężać `severity`. Ten sam kształt raport+bramka powtarza się w cotygodniowych skanach obrazu i obrazu Postgresa
- `deploy.yml`: ręczny trigger → SSH → `docker compose pull && up -d`

### Zmienne środowiskowe (`.env`)
`POSTGRES_DB/USER/PASSWORD`, `MAIL_HOST/PORT/USERNAME/PASSWORD`, `JWT_SECRET`, `GHCR_OWNER`, `VERSION`, `ADMIN_EMAIL`
Opcjonalne: `ADMIN_HIDDEN_EMAILS` (CSV — konta techniczne/deweloperskie z adminem do testów, **ukryte na liście użytkowników** w panelu, filtr w SQL `AdminEmailConfig.isHiddenEmail`); profil `oauth2`: `OAUTH2_GOOGLE_CLIENT_ID/SECRET`

---
