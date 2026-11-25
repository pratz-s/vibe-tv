#!/bin/bash
# Wipes everything the engine has learned about you: taste, saves, what you watched and heard, searches, activity and the Spotify link.
# The catalogue of films and shows stays. This can't be undone.
set -euo pipefail
cd "$(dirname "$0")/.."
read -r -p "This permanently deletes everything your engine knows about you. Type delete to go ahead: " ok
[ "$ok" = "delete" ] || { echo "Nothing deleted."; exit 0; }
cd worker
npx wrangler d1 execute vibe_tv --remote --yes --command "DELETE FROM signals; DELETE FROM asks; DELETE FROM watches; DELETE FROM screentime; DELETE FROM remote; DELETE FROM outcomes; DELETE FROM listens; DELETE FROM together; DELETE FROM activity; DELETE FROM profile;" >/dev/null
echo "Done. Your engine has forgotten you. Run scripts/push_taste.sh to start again from taste.md."
