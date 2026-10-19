#!/usr/bin/env bash
# List every app the launcher can show, by name, with the package name a theme needs
# (dock_apps, grid_apps, hidden_apps in themes/<name>/config.json).
#
# Usage: TV_HOST=192.168.1.50 fire/apps.sh
source "$(dirname "$0")/common.sh"
OUT=$(tv 'content query --uri content://com.gathin.tvhome.theme/apps' | tr -d '\r')
case "$OUT" in
  *Row:*) ;;
  *) echo "The launcher didn't answer; install the latest build with fire/install.sh first." >&2; echo "$OUT" >&2; exit 1 ;;
esac
# Rows look like: Row: 0 name=Netflix, package=com.netflix.ninja
printf '%s\n' "$OUT" | sed -nE 's/^Row: [0-9]+ name=(.*), package=([A-Za-z0-9_.]+)$/\1|\2/p' \
  | while IFS='|' read -r name pkg; do printf '  %-32s %s\n' "$name" "$pkg"; done
