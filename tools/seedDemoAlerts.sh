#!/bin/bash
#
# Seed (or remove) demo alerts for the timeline graph and gantt view.
#
#   ./seedDemoAlerts.sh                 # firing-now + 7 days of intermittent history
#   ./seedDemoAlerts.sh --clean         # remove every demo alert, change nothing else
#   WINDOW_DAYS=2 ./seedDemoAlerts.sh   # shorter history window
#   HISTORY_STATUS=ACKED ./seedDemoAlerts.sh
#
# Historical alerts are written as RESOLVED by default, which is what they are.
# Note that the backend deletes RESOLVED alerts whose endsAt is older than
# resolved.remove.minutes on its next ingest, so that property must exceed
# WINDOW_DAYS for the history to stick around (7 days = 10080 minutes).
#
set -euo pipefail

CONTAINER="${MONGO_CONTAINER:-compose-stack_mongodb_1}"
DB="${MONGO_DB:-AlertViewer}"
USER="${MONGO_USER:-my-user}"
PASS="${MONGO_PASS:-abcde}"
HISTORY_STATUS="${HISTORY_STATUS:-RESOLVED}"
WINDOW_DAYS="${WINDOW_DAYS:-7}"

CLEAN=false
if [ "${1:-}" = "--clean" ]; then CLEAN=true; fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PRELUDE="const CLEAN=$CLEAN; const HISTORY_STATUS='$HISTORY_STATUS'; const WINDOW_DAYS=$WINDOW_DAYS;"

podman exec -i "$CONTAINER" mongosh --quiet \
  -u "$USER" -p "$PASS" --authenticationDatabase admin "$DB" \
  --eval "$PRELUDE$(cat "$SCRIPT_DIR/seedDemoAlerts.js")"
