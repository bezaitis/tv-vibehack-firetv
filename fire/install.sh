#!/usr/bin/env bash
# Install the launcher on a Fire TV and switch on FireHomeService, which brings it back over Amazon's home screen.
# Amazon's launcher is not disabled and Home is not reassigned (Fire OS refuses both); turning the service off
# gives Amazon's home back. Run again after any reinstall: Android switches accessibility services off then.
#
# Usage: TV_HOST=192.168.1.50 fire/install.sh
#   APK=<path>     install that APK (e.g. the one GitHub Actions built; see fire/README.md)
#   otherwise uses fire/tvhome-fire.apk if it exists, else builds launcher/ (needs JDK 17 + Android SDK).
#   FORCE_BUILD=1  always build from source.
source "$(dirname "$0")/common.sh"

APK="${APK:-$FIRE_DIR/tvhome-fire.apk}"
if [ "${FORCE_BUILD:-0}" = 1 ] || [ ! -f "$APK" ]; then
  echo "== Building the launcher"
  (
    cd "$ROOT/launcher"
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || { [ -d /opt/homebrew/opt/openjdk@17 ] && echo /opt/homebrew/opt/openjdk@17; } || java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')}"
    export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk; do [ -d "$d" ] && echo "$d" && break; done)}"
    export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
    export PATH="$JAVA_HOME/bin:$PATH"
    [ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
    ./gradlew assembleRelease -q --console=plain
  )
  APK="$ROOT/launcher/app/build/outputs/apk/release/app-release.apk"
fi
echo "== Installing $(basename "$APK")"

case "$(tv 'pm has-feature amazon.hardware.fire_tv' | tr -d '\r')" in
  *true*) ;;
  *) echo "ERROR: this TV does not report as a Fire TV. For Android TV / Google TV use launcher/build-and-install.sh." >&2; exit 1 ;;
esac

"$PY" -I "$ROOT/tools/b64push.py" "$APK" /data/local/tmp/tvhome.apk
OUT=$(tv 'pm install -r /data/local/tmp/tvhome.apk; rm -f /data/local/tmp/tvhome.apk' | tr -d '\r')
echo "$OUT"
case "$OUT" in
  *Success*) ;;
  *UPDATE_INCOMPATIBLE*|*"signatures do not match"*)
    echo "ERROR: a copy signed with a different key is already installed (each GitHub build has its own key)." >&2
    echo "Remove it first:  TV_HOST=$TV_HOST tools/venv/bin/python tools/tv.py 'pm uninstall $PKG'   then run this again." >&2; exit 1 ;;
  *) echo "ERROR: install failed; nothing else was changed." >&2; exit 1 ;;
esac

echo "== Turning on the home watcher"
# Append to enabled_accessibility_services so any service you already use (e.g. VoiceView) stays on.
CUR=$(tv 'settings get secure enabled_accessibility_services' | tr -d '\r\n')
case "$CUR" in
  *FireHomeService*) NEW="$CUR" ;;
  null|"")           NEW="$SVC" ;;
  *)                 NEW="$CUR:$SVC" ;;
esac
printf '%s\n' \
  "pm grant $PKG android.permission.ACCESS_FINE_LOCATION" \
  "cmd package compile -m speed -f $PKG" \
  "settings put secure enabled_accessibility_services '$NEW'" \
  "settings put secure accessibility_enabled 1" \
  "settings get secure enabled_accessibility_services" \
  "am start -W -n $PKG/.MainActivity" \
  | tv_batch | grep -E '^\$ settings get|FireHomeService|^(Status|Error)' || true

echo
echo "Installed. Press Home on the remote: Amazon's home flashes, then this one comes up."
echo "To get Amazon's home back: Settings > Accessibility > turn off 'Home screen over Fire TV', or fire/restore.sh."
