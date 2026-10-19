#!/usr/bin/env bash
# Push a theme (config.json, optional wallpaper.jpg) to the launcher on a Fire TV and restart it.
# Fire OS 8 does not let adb write to the launcher's storage folder, which tools/theme.sh relies on, so this
# hands the files to the launcher through its adb-only ThemeProvider instead. Keys: themes/README.md.
#
# Usage: TV_HOST=192.168.1.50 fire/theme.sh themes/insignia
#        TV_HOST=192.168.1.50 fire/theme.sh --reset      # back to the built-in look
source "$(dirname "$0")/common.sh"
URI=content://com.gathin.tvhome.theme
TMP=/data/local/tmp

restart() { tv "am force-stop $PKG; am start -n $PKG/.MainActivity" >/dev/null; }

if [ "${1:-}" = --reset ]; then
  printf '%s\n' "content delete --uri $URI/config.json" "content delete --uri $URI/wallpaper.jpg" | tv_batch >/dev/null
  restart; echo "Theme removed; the launcher is back to its built-in look."; exit 0
fi

THEME="${1:?usage: theme.sh <theme folder> | --reset}"
[ -f "$THEME/config.json" ] || { echo "No config.json in $THEME" >&2; exit 1; }
python3 -c 'import json,sys; json.load(open(sys.argv[1]))' "$THEME/config.json" 2>/dev/null \
  || { echo "$THEME/config.json is not valid JSON" >&2; exit 1; }

# A theme that asks for a wallpaper but has none would silently show plain colour; say so instead.
if grep -q '"wallpaper"[[:space:]]*:[[:space:]]*true' "$THEME/config.json" && [ ! -f "$THEME/wallpaper.jpg" ]; then
  echo "WARNING: config.json asks for a wallpaper but $THEME/wallpaper.jpg is missing; pushing without one." >&2
fi

send() {   # local file, theme file name
  "$PY" -I "$ROOT/tools/b64push.py" "$1" "$TMP/$2" >/dev/null
  OUT=$(tv "content write --uri $URI/$2 < $TMP/$2; echo rc=\$?; rm -f $TMP/$2" | tr -d '\r')
  case "$OUT" in
    *rc=0*) echo "  $2 sent" ;;
    *) echo "ERROR sending $2: $OUT" >&2
       echo "If it mentions the provider or a permission, reinstall the launcher with fire/install.sh first." >&2; exit 1 ;;
  esac
}

send "$THEME/config.json" config.json
if [ -f "$THEME/wallpaper.jpg" ]; then
  send "$THEME/wallpaper.jpg" wallpaper.jpg
else
  tv "content delete --uri $URI/wallpaper.jpg" >/dev/null || true
fi
restart
echo "Theme $(basename "$THEME") is on the TV. Undo with: fire/theme.sh --reset"
