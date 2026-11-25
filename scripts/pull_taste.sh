#!/bin/bash
# Brings what the TV has learned (your saves, loves, "not for me", what you watched, music, how you use the TV)
# into a "From the TV" section at the end of taste.md. Your own sections above it are never touched.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
curl -fsS "$WORKER_URL/taste/learned" -H "authorization: Bearer $ADMIN_TOKEN" -A curl/8 | python3 -c '
import sys, json, re
d = json.load(sys.stdin); md = open("taste.md").read()
L = ["## From the TV", "Updated automatically from what I do on the TV. Edit the sections above instead; this part is rewritten each time.", ""]
def sec(title, items):
    if items: L.extend(["### " + title] + ["- " + i for i in items] + [""])
sec("Saved to watch later", d.get("save"))
sec("Loved (said or showed on the TV)", d.get("loved"))
sec("Not for me (said or chosen on the TV)", d.get("skip"))
sec("Finished on the TV", d.get("finished"))
sec("Watched lately", d.get("watched"))
if d.get("genres"): sec("Taste scores by genre", [d["genres"]])
m = d.get("music") or {}
if m.get("plays"): sec("Music", [x for x in ["On repeat: " + ", ".join(m["onRepeat"][:8]) if m.get("onRepeat") else "", "Top artists: " + ", ".join(m["artists"][:8]) if m.get("artists") else "", "Playlists: " + ", ".join(m["playlists"][:5]) if m.get("playlists") else "", "When: " + m["when"] if m.get("when") else ""] if x])
sec("How I use the TV", d.get("life"))
block = "\n".join(L).rstrip() + "\n"
md = re.split(r"\n## From the TV\n", md)[0].rstrip() + "\n\n" + block
open("taste.md", "w").write(md)
print("taste.md updated: From the TV section rewritten.")
'
