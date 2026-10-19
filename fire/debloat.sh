#!/usr/bin/env bash
# Disable every package in a Fire TV list (pm disable-user, never uninstall). Safe to run again, e.g. after a
# Fire OS update switches things back on. Undo with fire/restore.sh <same list>.
#
# Usage: TV_HOST=192.168.1.50 fire/debloat.sh fire/insignia-ns50f501na26.txt
source "$(dirname "$0")/common.sh"
LIST="${1:?usage: debloat.sh <list file>}"

# One pm command per shell call: on Fire OS a chained pm command can silently stop after the first.
OUT=$(list_packages "$LIST" | sed 's/^/pm disable-user --user 0 /' | tv_batch | tr -d '\r')

ok=0; protected=0; missing=0
# Fire OS answers each command with one or more lines; classify each package once, from all of its lines.
report() {
  [ -n "$pkg" ] || return 0
  case "$block" in
    *"new state: disabled"*)            printf '  %-48s disabled\n' "$pkg"; ok=$((ok + 1)) ;;
    *[Uu]nknown\ package*|*"not found"*) printf '  %-48s not on this TV\n' "$pkg"; missing=$((missing + 1)) ;;
    *)                                  printf '  %-48s refused by Fire OS (protected)\n' "$pkg"; protected=$((protected + 1)) ;;
  esac
}
pkg=""; block=""
while IFS= read -r line; do
  case "$line" in
    '$ pm disable-user --user 0 '*) report; pkg=${line#'$ pm disable-user --user 0 '}; block="" ;;
    *) block="$block $line" ;;
  esac
done <<< "$OUT"
report

echo
echo "$ok disabled, $protected refused by Fire OS, $missing not on this TV."
echo "Undo everything: TV_HOST=$TV_HOST fire/restore.sh $LIST"
