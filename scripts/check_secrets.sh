#!/bin/bash
# Looks through the files git would publish for keys, private IDs, home network addresses and personal files.
# It runs before every commit (see SETUP.md). To also catch your own names, put one word per line in .private-words (never committed).
set -uo pipefail
cd "$(dirname "$0")/.."
FILES=$(git ls-files -co --exclude-standard 2>/dev/null || find . -type f -not -path './.git/*')
bad=0
flag() { echo "  $1"; bad=1; }
for f in .env taste.md worker/wrangler.jsonc tv-app/local.properties .private-words; do echo "$FILES" | grep -qx "$f" && flag "$f would be published. It must stay private."; done
PAT='(AIza[0-9A-Za-z_-]{30,}|sk-ant-[0-9A-Za-z_-]{20,}|sk-[0-9A-Za-z]{32,}|ghp_[0-9A-Za-z]{30,}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|\b[0-9a-f]{40,}\b|\b(192\.168|10\.[0-9]{1,3})\.[0-9]{1,3}\.[0-9]{1,3}\b|[a-z0-9-]+\.[a-z0-9-]+\.workers\.dev)'
while IFS= read -r f; do
  [ -f "$f" ] || continue
  case "$f" in *.png|*.jpg|*.ogg|*.wav|*.ttf|*.jar|*package-lock.json|*.example*) continue;; esac
  hits=$(grep -nIEo "$PAT" "$f" 2>/dev/null | head -3)
  [ -n "$hits" ] && flag "$f: looks like a key, private ID or address: $(echo "$hits" | cut -c1-24 | tr '\n' ' ')…"
  if [ -s .private-words ]; then w=$(grep -nIiwF -f <(grep -v '^\s*$' .private-words) "$f" 2>/dev/null | head -2); [ -n "$w" ] && flag "$f: mentions one of your private words (line $(echo "$w" | cut -d: -f1 | tr '\n' ' '))"; fi
done <<< "$FILES"
if [ $bad = 0 ]; then echo "No secrets or personal details found."; else echo "Fix the lines above before publishing."; exit 1; fi
