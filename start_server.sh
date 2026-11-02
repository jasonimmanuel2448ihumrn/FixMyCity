#!/bin/bash
PORT="${1:-8080}"
chmod +x "$(dirname "$0")/server/start.sh"
exec "$(dirname "$0")/server/start.sh" "$PORT"
