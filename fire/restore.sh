#!/usr/bin/env bash
# Undo the fire/ changes: re-enable every package in the list and switch off the home watcher, so Amazon's home is
# back exactly as it was (it was never disabled). Other accessibility services you use are kept.
#
# Usage: TV_HOST=192.168.1.50 fire/restore.sh fire/insignia-ns50f501na26.txt [--uninstall]
#   --uninstall   also removes the launcher app itself
#   With no list, only the home watcher is switched off.
source "$(dirname "$0")/common.sh"
LIST=""; UNINSTALL=0
for a in "$@"; do
  case "$a" in --uninstall) UNINSTALL=1 ;; *) LIST="$a" ;; esac
done

if [ -n "$LIST" ]; then
  echo "== Re-enabling the packages in $(basename "$LIST")"
  list_packages "$LIST" | sed 's/^/pm enable --user 0 /' | tv_batch | tr -d '\r' \
    | grep -E 'new state' | sed 's/^/  /' || true
fi

echo "== Switching off the home watcher"
CUR=$(tv 'settings get secure enabled_accessibility_services' | tr -d '\r\n')
REST=$(printf '%s' "$CUR" | tr ':' '\n' | grep -vxF "$SVC" | grep -vxF "$SVC_LONG" | grep -vx 'null' | paste -sd: - || true)
if [ -z "$REST" ]; then
  printf '%s\n' "settings delete secure enabled_accessibility_services" "settings put secure accessibility_enabled 0" | tv_batch >/dev/null
else
  tv "settings put secure enabled_accessibility_services '$REST'" >/dev/null
fi

if [ "$UNINSTALL" = 1 ]; then
  echo "== Uninstalling the launcher"
  tv "pm uninstall $PKG"
fi

tv 'input keyevent KEYCODE_HOME' >/dev/null || true
echo
echo "Done. Amazon's home screen is back. Still disabled on the TV (should be empty if you used the same list):"
tv 'pm list packages -d' | tr -d '\r' | sed 's/^package:/  /'
