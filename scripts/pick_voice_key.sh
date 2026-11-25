#!/bin/bash
# Finds out which remote button should start voice: press it once when asked, and it's saved as VOICE_KEY in .env.
# Run after the app is on the TV (scripts/tv_install.sh), then install again so the TV uses it.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
TARGET="$TV_IP"; [[ "$TARGET" == *:* ]] || TARGET="$TARGET:5555"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"; ADB="$SDK/platform-tools/adb"; command -v "$ADB" >/dev/null || ADB=adb
"$ADB" connect "$TARGET" >/dev/null; TV=("$ADB" -s "$TARGET")
"${TV[@]}" logcat -c
echo "Press the button you want for voice on your TV remote, once. (Waiting 20 seconds.)"
LINE=$(python3 - "${TV[@]}" <<'PY'
import re, select, subprocess, sys, time
p = subprocess.Popen(sys.argv[1:] + ["logcat", "-s", "TVHOME:I"], stdout=subprocess.PIPE, text=True); end = time.time() + 20
while time.time() < end:
    if not select.select([p.stdout], [], [], 1)[0]: continue
    m = re.search(r" key (\d+) (KEYCODE_[A-Z0-9_]+)", p.stdout.readline())
    if m and not re.match(r"KEYCODE_(DPAD_|BACK|HOME|ENTER|VOLUME_)", m.group(2)): print(m.group(1), m.group(2)); break
p.kill()
PY
)
[ -n "$LINE" ] || { echo "Didn't see a button. Make sure the app is installed and try again (arrows, OK, Back and Home can't be used)."; exit 1; }
CODE=${LINE%% *}; NAME=${LINE#* }
echo "Got $NAME."
case "$NAME" in KEYCODE_ASSIST|KEYCODE_VOICE_ASSIST|KEYCODE_SEARCH) echo "Heads up: that's the Google Assistant button. Inside other apps Google may still answer it; on the home screen it's ours.";; esac
python3 - "$CODE" <<'PY'
import re,sys
v=sys.argv[1]; s=open('.env').read()
s=re.sub(r'^VOICE_KEY=.*$',f'VOICE_KEY={v}',s,flags=re.M) if re.search(r'^VOICE_KEY=',s,re.M) else s.rstrip('\n')+f'\nVOICE_KEY={v}\n'
open('.env','w').write(s)
PY
echo "Saved. Now run scripts/tv_install.sh so the TV uses it."
