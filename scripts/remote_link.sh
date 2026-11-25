#!/bin/bash
# Prints the link to your phone remote, and a QR code to scan. Treat the link like a password: anyone with it can control your TV.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
: "${WORKER_URL:?Run scripts/setup_cloud.sh first}" "${REMOTE_KEY:?Run scripts/setup_cloud.sh first}"
LINK="$WORKER_URL/r/$REMOTE_KEY"
if [ "${1:-}" = "--show" ]; then echo "$LINK"; fi
(cd worker && npx --yes qrcode-terminal "$LINK" --small 2>/dev/null) || echo "Couldn't draw a QR code. Run with --show to print the link instead."
echo "Scan it with your phone's camera, then add the page to your home screen."
