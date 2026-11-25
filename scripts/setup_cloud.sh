#!/bin/bash
# Creates everything on your own Cloudflare account and deploys the engine. Safe to run again.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "No .env yet. Run: cp .env.example .env, then fill it in."; exit 1; }
set -a; . ./.env; set +a
: "${MOTN_KEY:?Add MOTN_KEY to .env}"
[ -n "${GEMINI_API_KEY:-}${ANTHROPIC_API_KEY:-}" ] || { echo "Add GEMINI_API_KEY (free) or ANTHROPIC_API_KEY to .env."; exit 1; }
setvar() { python3 - "$1" "$2" <<'PY'
import re,sys
k,v=sys.argv[1:]; s=open('.env').read()
s=re.sub(rf'^{k}=.*$',lambda m:f'{k}={v}',s,flags=re.M) if re.search(rf'^{k}=',s,re.M) else s.rstrip('\n')+f'\n{k}={v}\n'
open('.env','w').write(s)
PY
}
# Three separate keys, made here and never shown: one for the TV, one for your scripts, one for the phone remote link.
for k in APP_TOKEN ADMIN_TOKEN REMOTE_KEY; do if [ -z "${!k:-}" ]; then v=$(openssl rand -hex 24); setvar "$k" "$v"; export "$k=$v"; fi; done

cd worker
npm install --silent
npx wrangler whoami 2>&1 | grep -q 'You are logged in' || npx wrangler login

echo "Database"
d1id() { npx wrangler d1 list --json | python3 -c "import json,sys; print(next((d['uuid'] for d in json.load(sys.stdin) if d['name']=='vibe_tv'),''))"; }
ID=$(d1id); if [ -z "$ID" ]; then npx wrangler d1 create vibe_tv >/dev/null; ID=$(d1id); fi
[ -n "$ID" ] || { echo "Couldn't create the database."; exit 1; }
python3 - "$ID" <<'PY'
import os,sys
s=open('wrangler.template.jsonc').read().replace('__D1_ID__',sys.argv[1])
defaults={'APPS':'','CITY':'','LAT':'','LON':'','TIMEZONE':'UTC','ASSISTANT_NAME':'Jarvis'}
for k in ('SERVICES','COUNTRY','CATALOGS','APPS','CITY','LAT','LON','TIMEZONE','ASSISTANT_NAME'):
    v=os.environ.get(k) or defaults.get(k)
    if v is None: sys.exit(k+' is missing from .env')
    s=s.replace(f'__{k}__',v.replace('"',''))
open('wrangler.jsonc','w').write(s)
PY
npx wrangler d1 execute vibe_tv --remote --file schema.sql --yes >/dev/null

echo "Search index"
if ! npx wrangler vectorize get vibe-tv-titles >/dev/null 2>&1; then
  npx wrangler vectorize create vibe-tv-titles --dimensions=384 --metric=cosine >/dev/null
fi
HAVE=$(npx wrangler vectorize list-metadata-index vibe-tv-titles 2>/dev/null || true)
index() { echo "$HAVE" | grep -qw "$1" || npx wrangler vectorize create-metadata-index vibe-tv-titles --propertyName="$1" --type="$2" >/dev/null; }
index type string; index orig string; index year number; index runtime number
for l in hin eng tam tel mal kan; do index "has_$l" boolean; done

echo "Keys"
put() { printf '%s' "$2" | npx wrangler secret put "$1" >/dev/null; }
for k in GEMINI_API_KEY ANTHROPIC_API_KEY SPOTIFY_CLIENT_ID; do if [ -n "${!k:-}" ]; then put "$k" "${!k}"; fi; done
put MOTN_KEY "$MOTN_KEY"; put APP_TOKEN "$APP_TOKEN"; put ADMIN_TOKEN "$ADMIN_TOKEN"; put REMOTE_KEY "$REMOTE_KEY"

echo "Deploy"
URL=$(npx wrangler deploy 2>&1 | grep -oE 'https://[a-zA-Z0-9.-]+\.workers\.dev' | head -1)
[ -n "$URL" ] || { echo "Deploy finished without a URL. Run: cd worker && npx wrangler deploy"; exit 1; }
cd ..; setvar WORKER_URL "$URL"
echo "Engine is live at $URL"
