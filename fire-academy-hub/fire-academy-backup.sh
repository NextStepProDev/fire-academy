#!/bin/bash
#
# Nightly backup: the whole database, the uploads volume, encrypted off-site.
#
# Runs from root's crontab at 03:00 — see the install note at the bottom of this file. It used to
# live only on the production box, which meant a rebuild of that box lost it: the one file in the
# backup system that had no backup. It ships from the repo now, the same way nginx.conf and
# setup-swap.sh do.
#
# Three rules this script exists to keep, each one learned the hard way somewhere:
#
#   1. NEVER let the off-site copy mirror deletions. `rclone sync` makes the destination identical
#      to the source, deletions included — so anything that wipes /backups here (a bad disk, a
#      stray rm, ransomware) reaches Google Drive on the next run, and a mistake nobody notices for
#      a week is unrecoverable because the copies from before it were pruned. `copy` only ever adds;
#      the remote is pruned separately and far more slowly.
#   2. NEVER publish a dump that did not finish. The shell creates the output file before pg_dump
#      writes a byte, so an interrupted dump leaves something that looks exactly like a backup — and
#      a truncated dump is usually still a VALID gzip, so testing the compression proves nothing.
#      Work goes to a .part file, is checked for pg_dump's own end marker, and only then takes the
#      real name.
#   3. NEVER rely on being told about failure. Cron mails root, root's mail goes nowhere on a cloud
#      box, and `set -e` exits in silence. A backup that stopped running in March is discovered in
#      August. The ping below inverts that: an external service expects to hear from us daily and
#      raises the alarm when it does not — which also covers the cases no failure mail could ever
#      report, like the machine being off or the cron entry being gone.

set -euo pipefail

DATE=$(date +%Y-%m-%d)
DB_DIR="/backups/db"
FILES_DIR="/backups/files"
DB_BACKUP="${DB_DIR}/${DATE}.sql.gz"
FILES_BACKUP="${FILES_DIR}/${DATE}.tar.gz"
COMPOSE_DIR="/opt/fire-academy"
LOG="/var/log/fire-academy-backup.log"
REMOTE="gdrive-crypt:"

# How long copies live. Local is short because it is only a staging area and shares the disk with
# the database; off-site is long because that is the copy you reach for when you discover a problem
# late, and "late" is the whole reason it exists. Gzipped dumps are small enough that a quarter of
# history costs nothing worth counting.
LOCAL_RETENTION_DAYS=7
REMOTE_RETENTION_DAYS=90

# Optional, and kept OUT of this file on purpose: the ping URL is a shared secret, and anyone
# holding it can report a success we never had. Put HEALTHCHECK_URL=... in this file on the server,
# readable by root only.
ENV_FILE="/etc/fire-academy-backup.env"
# shellcheck source=/dev/null
[ -f "$ENV_FILE" ] && . "$ENV_FILE"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-}"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }

ping_healthcheck() {
    [ -n "$HEALTHCHECK_URL" ] || return 0
    # Never let the alarm bell take the backup down with it: this is best-effort by design.
    curl -fsS -m 10 --retry 3 "${HEALTHCHECK_URL}$1" >/dev/null 2>&1 || \
        log "WARN: could not reach the health check endpoint"
}

# Fires on any failed command, because of set -e. Tells the monitor immediately rather than leaving
# it to notice the silence hours later.
on_failure() {
    local line=$1
    log "FAILED at line ${line}"
    ping_healthcheck "/fail"
}
trap 'on_failure $LINENO' ERR

mkdir -p "$DB_DIR" "$FILES_DIR"
log "=== Backup start ==="

# --- database -----------------------------------------------------------------------------------

log "DB backup..."
docker compose -f "${COMPOSE_DIR}/docker-compose.prod.yml" exec -T postgres \
    pg_dump -U fireacademy fireacademy | gzip > "${DB_BACKUP}.part"

# pg_dump signs off with its own end marker. Its presence is the only cheap proof that the database
# reached the end of the dump instead of dying halfway through a table.
if ! gunzip -c "${DB_BACKUP}.part" | tail -5 | grep -q "PostgreSQL database dump complete"; then
    log "FAILED: dump has no completion marker — refusing to publish it"
    rm -f "${DB_BACKUP}.part"
    ping_healthcheck "/fail"
    exit 1
fi

mv "${DB_BACKUP}.part" "$DB_BACKUP"
log "DB OK: $(du -sh "$DB_BACKUP" | cut -f1)"

# --- uploaded files -----------------------------------------------------------------------------

log "Files backup..."
docker run --rm \
    -v fire-academy_fa_uploads_data_prod:/data:ro \
    -v "${FILES_DIR}:/backup" \
    alpine tar czf "/backup/${DATE}.tar.gz.part" -C /data .

# Reading the archive back is what separates "tar exited 0" from "the archive can be opened".
if ! tar tzf "${FILES_BACKUP}.part" >/dev/null 2>&1; then
    log "FAILED: files archive will not read back — refusing to publish it"
    rm -f "${FILES_BACKUP}.part"
    ping_healthcheck "/fail"
    exit 1
fi

mv "${FILES_BACKUP}.part" "$FILES_BACKUP"
log "Files OK: $(du -sh "$FILES_BACKUP" | cut -f1)"

# --- off-site -------------------------------------------------------------------------------

# `copy`, never `sync`. See rule 1 at the top. The remote keeps its own, much longer history, and
# the local prune below cannot reach it.
log "Copy to Google Drive (encrypted remote)..."
rclone copy /backups "$REMOTE" --log-file="$LOG" --log-level NOTICE

# Pruned separately and slowly. --min-age is a filter, so nothing newer can be caught by it.
log "Pruning off-site copies older than ${REMOTE_RETENTION_DAYS} days..."
rclone delete "$REMOTE" --min-age "${REMOTE_RETENTION_DAYS}d" --log-file="$LOG" --log-level NOTICE

# --- local prune --------------------------------------------------------------------------------

find "$DB_DIR" -name "*.sql.gz" -mtime "+${LOCAL_RETENTION_DAYS}" -delete
find "$FILES_DIR" -name "*.tar.gz" -mtime "+${LOCAL_RETENTION_DAYS}" -delete
# Leftovers from a run that died mid-dump. Never uploaded — they never got their real name — but
# they do take up disk until something clears them.
find "$DB_DIR" "$FILES_DIR" -name "*.part" -mtime +1 -delete

log "=== Backup done ==="
ping_healthcheck ""

# --- installing this on the server ----------------------------------------------------------------
#
#   sudo install -m 0755 fire-academy-backup.sh /usr/local/bin/fire-academy-backup.sh
#   sudo crontab -l | grep -q fire-academy-backup || \
#       (sudo crontab -l; echo "0 3 * * * /usr/local/bin/fire-academy-backup.sh") | sudo crontab -
#
# For the failure alarm, create an account at https://healthchecks.io (free), add a check that
# expects a daily ping, and put its URL on the server — root-only, never in this repo:
#
#   printf 'HEALTHCHECK_URL=https://hc-ping.com/YOUR-UUID\n' | sudo tee /etc/fire-academy-backup.env
#   sudo chmod 600 /etc/fire-academy-backup.env
#
# Restoring is documented in RESTORE.md, next to this file. Read it before you need it.
