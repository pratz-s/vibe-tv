#!/bin/bash
# Builds the app with your engine's address and installs it on your TV over Wi-Fi.
# First turn on developer options and network debugging on the TV, then approve this computer when the TV asks.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
# TV_IP may include a port (Wireless debugging); otherwise the classic 5555 is used.
TARGET="$TV_IP"; [[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
# Use Android Studio's bundled Java when no other Java is installed.
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && ! /usr/libexec/java_home >/dev/null 2>&1 && [ -d "$JBR" ]; then export JAVA_HOME="$JBR"; fi
: "${TV_IP:?Add TV_IP to .env}" "${WORKER_URL:?Run scripts/setup_cloud.sh first}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[ -d "$SDK" ] || { echo "Android SDK not found. Set ANDROID_HOME."; exit 1; }
echo "sdk.dir=$SDK" > tv-app/local.properties
ADB="$SDK/platform-tools/adb"; command -v "$ADB" >/dev/null || ADB=adb
(cd tv-app && ./gradlew -q assembleRelease)
"$ADB" connect "$TARGET" | tail -1
TV=("$ADB" -s "$TARGET")
"${TV[@]}" install -r tv-app/app/build/outputs/apk/release/app-release.apk | tail -1
# A reinstall stops the Home button service, so it's switched off and on again, keeping any others you have.
SVC=tv.home.app/tv.home.app.HomeService
OTHERS=$("${TV[@]}" shell settings get secure enabled_accessibility_services | tr -d '\r' | tr ':' '\n' | grep -v -E "^$|^null$|tv\.home\.app" | paste -sd: - || true)
"${TV[@]}" shell settings put secure enabled_accessibility_services "${OTHERS:-null}"
sleep 3
"${TV[@]}" shell settings put secure enabled_accessibility_services "${OTHERS:+$OTHERS:}$SVC"
"${TV[@]}" shell settings put secure accessibility_enabled 1
# A reinstall can leave the service marked as crashed; switch it off and on until Android binds it cleanly.
for _ in 1 2 3; do
  sleep 3
  "${TV[@]}" shell dumpsys accessibility | grep -q "Crashed services:{}" && break
  "${TV[@]}" shell settings put secure enabled_accessibility_services "${OTHERS:-null}"; sleep 3
  "${TV[@]}" shell settings put secure enabled_accessibility_services "${OTHERS:+$OTHERS:}$SVC"
done
"${TV[@]}" shell am start -n tv.home.app/.MainActivity | tail -1
echo "Installed. Press Home on your remote."
