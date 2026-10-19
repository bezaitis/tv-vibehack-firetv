#!/usr/bin/env bash
# Read-only: shows what a list would do on this TV, and saves a backup of the TV's current state.
# Changes nothing on the TV.
#
# Usage: TV_HOST=192.168.1.50 fire/check.sh fire/insignia-ns50f501na26.txt
source "$(dirname "$0")/common.sh"
LIST="${1:?usage: check.sh <list file>}"

echo "== TV"
tv --probe
case "$(tv 'pm has-feature amazon.hardware.fire_tv' | tr -d '\r')" in
  *true*) ;;
  *) echo "WARNING: this does not report as a Fire TV. Use the main README for Android TV / Google TV." ;;
esac

ALL=$(tv 'pm list packages' | tr -d '\r' | sed 's/^package://')
OFF=$(tv 'pm list packages -d' | tr -d '\r' | sed 's/^package://')

BACKUP="$FIRE_DIR/backup-$(date +%Y%m%d-%H%M%S).txt"
{
  echo "# Fire TV state before any change, $(date)"
  echo "# enabled_accessibility_services: $(tv 'settings get secure enabled_accessibility_services' | tr -d '\r')"
  echo "# Already disabled before this fork touched anything:"
  printf '%s\n' "$OFF" | sed '/^$/d; s/^/#   /'
  echo "# All packages:"
  printf '%s\n' "$ALL" | sed '/^$/d'
} > "$BACKUP"
echo "Saved the TV's package list to $BACKUP"

echo
echo "== What $(basename "$LIST") would do"
n_do=0; n_off=0; n_missing=0
while read -r p; do
  if ! printf '%s\n' "$ALL" | grep -qx "$p"; then
    printf '  %-48s not on this TV (skipped harmlessly)\n' "$p"; n_missing=$((n_missing + 1))
  elif printf '%s\n' "$OFF" | grep -qx "$p"; then
    printf '  %-48s already disabled\n' "$p"; n_off=$((n_off + 1))
  else
    printf '  %-48s will be disabled\n' "$p"; n_do=$((n_do + 1))
  fi
done < <(list_packages "$LIST")
echo
echo "$n_do to disable, $n_off already off, $n_missing not on this TV."

echo
echo "== Amazon packages on this TV that the list does not mention (left alone)"
LISTED=$(list_packages "$LIST")
printf '%s\n' "$ALL" | grep -E '^com\.(amazon|fireos|ivona)\.' | sort | while read -r p; do
  printf '%s\n' "$LISTED" | grep -qx "$p" || echo "  $p"
done
