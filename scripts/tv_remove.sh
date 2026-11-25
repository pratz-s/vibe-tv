#!/bin/bash
# Takes the app off your TV and gives Google's home back.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
# TV_IP may include a port (Wireless debugging); otherwise the classic 5555 is used.
TARGET="$TV_IP"; [[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
# Use Android Studio's bundled Java when no other Java is installed.
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && ! /usr/libexec/java_home >/dev/null 2>&1 && [ -d "$JBR" ]; then export JAVA_HOME="$JBR"; fi
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; ADB="$SDK/platform-tools/adb"; command -v "$ADB" >/dev/null || ADB=adb
"$ADB" connect "$TARGET" | tail -1
"$ADB" -s "$TARGET" uninstall tv.home.app
echo "Removed."
