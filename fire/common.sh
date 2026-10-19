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
tv_batch() { "$PY" -I "$ROOT/tools/tv.py" --batch; }

# Package names from a list file: comments and blank lines dropped, trailing "# note" stripped.
list_packages() {
  local list="${1:?usage: <script> <list file>}"
  [ -f "$list" ] || { echo "No such list: $list" >&2; exit 1; }
  grep -vE '^\s*(#|$)' "$list" | sed -E 's/\s+#.*$//; s/\s+$//'
}
