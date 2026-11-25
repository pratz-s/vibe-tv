#!/bin/bash
# Makes new keys for the TV, your scripts and the phone remote, and turns the old ones off.
# Use it if you shared the built app or the remote link by mistake. Then reinstall the TV app and scan the new remote link.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
import re,secrets
s=open('.env').read()
for k in ('APP_TOKEN','ADMIN_TOKEN','REMOTE_KEY'):
    v=secrets.token_hex(24); s=re.sub(rf'^{k}=.*$',f'{k}={v}',s,flags=re.M) if re.search(rf'^{k}=',s,re.M) else s.rstrip('\n')+f'\n{k}={v}\n'
open('.env','w').write(s)
PY
set -a; . ./.env; set +a
cd worker
for k in APP_TOKEN ADMIN_TOKEN REMOTE_KEY; do printf '%s' "${!k}" | npx wrangler secret put "$k" >/dev/null; done
cd ..
echo "New keys are live. Now run scripts/tv_install.sh, then scripts/remote_link.sh for the new phone remote."
