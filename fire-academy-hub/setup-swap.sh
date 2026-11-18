#!/usr/bin/env bash
#
# setup-swap.sh — 2 GB of swap for the production server.
#
# Critical on a production machine with little memory: without swap the backend
# (Java/Spring) + PostgreSQL end in OOM. Creates /swapfile, sets swappiness 10
# and makes both survive a reboot (fstab + sysctl).
#
# Usage (the Deploy workflow runs it every time):
#   sudo bash setup-swap.sh
#
# The script is idempotent — safe to run again.

set -euo pipefail

SWAPFILE="/swapfile"
SWAP_SIZE="2G"
SWAPPINESS=10
SYSCTL_FILE="/etc/sysctl.d/99-swappiness.conf"

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script needs root. Run: sudo bash setup-swap.sh" >&2
  exit 1
fi

echo "==> Swap setup (${SWAP_SIZE} @ ${SWAPFILE}, swappiness ${SWAPPINESS})"

# 1. Create the swap file (unless it exists)
if [[ -f "${SWAPFILE}" ]]; then
  echo "    ${SWAPFILE} already exists — not creating it."
else
  echo "    Creating ${SWAPFILE} (${SWAP_SIZE})..."
  if ! fallocate -l "${SWAP_SIZE}" "${SWAPFILE}" 2>/dev/null; then
    # fallocate can fail on some file systems (e.g. ext without support) — fall back to dd
    echo "    fallocate unavailable, using dd..."
    dd if=/dev/zero of="${SWAPFILE}" bs=1M count=2048 status=progress
  fi
  chmod 600 "${SWAPFILE}"
  mkswap "${SWAPFILE}"
fi

# 2. Permissions (just in case the file existed with loose ones)
chmod 600 "${SWAPFILE}"

# 3. Turn swap on (unless it is active)
if swapon --show=NAME --noheadings | grep -qx "${SWAPFILE}"; then
  echo "    Swap already active."
else
  echo "    Turning swap on..."
  swapon "${SWAPFILE}"
fi

# 4. Persist in /etc/fstab (survives a reboot)
if grep -qE "^[[:space:]]*${SWAPFILE}[[:space:]]" /etc/fstab; then
  echo "    The /etc/fstab entry already exists."
else
  echo "    Adding the /etc/fstab entry..."
  echo "${SWAPFILE} none swap sw 0 0" >> /etc/fstab
fi

# 5. Swappiness — persisted through sysctl.d + applied at once
echo "    Setting vm.swappiness=${SWAPPINESS}..."
echo "vm.swappiness=${SWAPPINESS}" > "${SYSCTL_FILE}"
sysctl -w vm.swappiness="${SWAPPINESS}" >/dev/null

echo "==> Done. Current state:"
swapon --show
echo "    vm.swappiness = $(cat /proc/sys/vm/swappiness)"
free -h
