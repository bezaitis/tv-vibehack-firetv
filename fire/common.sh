# Shared setup for the fire/ scripts. Sourced, not run.
set -euo pipefail
FIRE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$FIRE_DIR/.." && pwd)"
PY="$ROOT/tools/venv/bin/python"
[ -x "$PY" ] || PY="$ROOT/tools/venv/Scripts/python.exe"
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "First run:  python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
: "${TV_HOST:?set TV_HOST to the IP address of the TV (Settings > My Fire TV > About > Network), e.g. TV_HOST=192.168.1.50}"

PKG=com.gathin.tvhome
SVC=$PKG/.FireHomeService
SVC_LONG=$PKG/$PKG.FireHomeService

tv()       { "$PY" -I "$ROOT/tools/tv.py" "$@"; }

# Switch the home watcher on, keeping any other accessibility service (e.g. VoiceView) enabled. Android drops an
# app's accessibility services from the enabled list whenever the app is force-stopped or reinstalled, so every
# script that does either calls this afterwards.
enable_watcher() {
  local cur new
  cur=$(tv 'settings get secure enabled_accessibility_services' | tr -d '\r\n')
  case "$cur" in
    *FireHomeService*) new="$cur" ;;
    null|"")           new="$SVC" ;;
    *)                 new="$cur:$SVC" ;;
  esac
  printf '%s\n' "settings put secure enabled_accessibility_services '$new'" "settings put secure accessibility_enabled 1" \
    | tv_batch >/dev/null
}
tv_batch() { "$PY" -I "$ROOT/tools/tv.py" --batch; }

# Package names from a list file: comments and blank lines dropped, trailing "# note" stripped.
list_packages() {
  local list="${1:?usage: <script> <list file>}"
  [ -f "$list" ] || { echo "No such list: $list" >&2; exit 1; }
  # POSIX classes only: macOS sed has no \s (it reads it as a literal "s").
  grep -vE '^[[:space:]]*(#|$)' "$list" | sed -E 's/[[:space:]]+#.*$//; s/[[:space:]]+$//'
}
