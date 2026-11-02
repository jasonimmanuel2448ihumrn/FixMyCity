#!/bin/bash
PORT="${1:-8080}"
echo "Starting FixMyCity Local Image Storage Server on port $PORT..."
python3 "$(dirname "$0")/server.py" "$PORT"
