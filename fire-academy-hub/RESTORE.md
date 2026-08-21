# Odtwarzanie z kopii zapasowej

Procedura na dzień, w którym baza produkcyjna przestaje istnieć. **Przećwicz ją, zanim będzie
potrzebna** — kopia, z której nigdy nie odtwarzano, jest hipotezą, nie kopią zapasową. Sekcja
„Ćwiczenie" na końcu opisuje, jak to zrobić bez dotykania produkcji.

Kopie robi `fire-academy-backup.sh` (cron roota, 03:00). Dwa osobne zbiory:

| co | gdzie lokalnie | ile dni | na Dysku Google |
|---|---|---|---|
| zrzut bazy | `/backups/db/RRRR-MM-DD.sql.gz` | 7 | 90 |
| pliki (avatary, zdjęcia, galerie) | `/backups/files/RRRR-MM-DD.tar.gz` | 7 | 90 |

Zdalny dysk to `gdrive-crypt:` — **remote typu `crypt`**, czyli rclone szyfruje pliki przed
wysłaniem i Google nie widzi ich treści ani prawdziwych nazw. Deszyfrowanie dzieje się samo przy
pobieraniu przez rclone; bez konfiguracji rclone z tej maszyny pliki są bezużyteczne. To także
znaczy, że **utrata konfiguracji rclone = utrata dostępu do kopii** — patrz „Czego pilnować".

---

## 1. Skąd wziąć kopię

Jeśli pliki są jeszcze na serwerze, pomiń ten krok. Jeśli nie:

```bash
rclone ls gdrive-crypt:db | tail -20                      # co jest dostępne
rclone copy gdrive-crypt:db/2026-08-20.sql.gz /tmp/restore/
rclone copy gdrive-crypt:files/2026-08-20.tar.gz /tmp/restore/
```

Sprawdź, czy zrzut jest kompletny, **zanim** cokolwiek skasujesz:

```bash
gunzip -c /tmp/restore/2026-08-20.sql.gz | tail -5 | grep "PostgreSQL database dump complete"
```

Brak tej linijki = plik jest ucięty. Weź starszy i nie ruszaj produkcji.

---

## 2. Odtworzenie bazy

> ⚠️ Kasuje bieżącą zawartość bazy. Upewnij się, że odtwarzasz właściwy dzień.

```bash
cd /opt/fire-academy
docker compose -f docker-compose.prod.yml stop backend      # nikt nie pisze w trakcie

gunzip -c /tmp/restore/2026-08-20.sql.gz | \
  docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U fireacademy -d fireacademy

docker compose -f docker-compose.prod.yml start backend
```

Backend zatrzymujemy celowo: Flyway i JPA piszą przy starcie, a odtwarzanie do bazy, w której coś
się zmienia, kończy się konfliktami kluczy w połowie.

---

## 3. Odtworzenie plików

```bash
docker run --rm \
  -v fire-academy_fa_uploads_data_prod:/data \
  -v /tmp/restore:/backup:ro \
  alpine sh -c "rm -rf /data/* && tar xzf /backup/2026-08-20.tar.gz -C /data"
```

Wolumen musi nazywać się dokładnie tak — to nazwa nadana przez compose (prefiks projektu
`fire-academy_`). Sprawdź w razie wątpliwości: `docker volume ls | grep uploads`.

---

## 4. Sprawdzenie, czy się udało

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  psql -U fireacademy -d fireacademy -c \
  "SELECT (SELECT count(*) FROM users) AS users,
          (SELECT count(*) FROM personal_trainings) AS treningi,
          (SELECT count(*) FROM enrollments) AS zapisy;"

curl -sf https://fireworkout.pl/actuator/health && echo " backend żyje"
```

Liczby porównaj z tym, czego się spodziewasz. Zero użytkowników po odtworzeniu znaczy, że zrzut był
pusty — wróć do kroku 1 i weź starszy.

---

## Ćwiczenie (zrób to raz, na spokojnie)

Bez dotykania produkcji, na dowolnej maszynie z Dockerem:

```bash
docker run -d --name restore-test -e POSTGRES_PASSWORD=test \
  -e POSTGRES_USER=fireacademy -e POSTGRES_DB=fireacademy -p 55432:5432 postgres:17-alpine
sleep 5
gunzip -c 2026-08-20.sql.gz | docker exec -i restore-test psql -U fireacademy -d fireacademy
docker exec -i restore-test psql -U fireacademy -d fireacademy -c "SELECT count(*) FROM users;"
docker rm -f restore-test
```

Jeśli liczba użytkowników się zgadza — kopie działają i wiecie o tym, zamiast zakładać.

---

## Czego pilnować

- **Konfiguracja rclone jest równie ważna jak same kopie.** Zaszyfrowane pliki bez niej to szum.
  Trzymaj kopię `~/.config/rclone/rclone.conf` (albo samych haseł remote'u `crypt`) w menedżerze
  haseł, poza tym serwerem. Utrata serwera razem z konfiguracją = utrata wszystkich kopii.
- **Cisza to awaria.** Skrypt pinguje monitor po każdym udanym przebiegu; brak sygnału ma zapalić
  alarm. Jeśli nie skonfigurowano `HEALTHCHECK_URL`, nikt się nie dowie, że kopie przestały powstawać.
- **Sprawdzaj ćwiczeniem, nie logiem.** Log mówi, że plik powstał. Tylko odtworzenie mówi, że da się
  z niego wrócić.
