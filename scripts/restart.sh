#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Restarting MarketStream..."
bash "$SCRIPT_DIR/stop.sh"
bash "$SCRIPT_DIR/start.sh"
