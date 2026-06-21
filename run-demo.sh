#!/usr/bin/env bash
#
# One-command demo: starts the control system (simulation + MQTT) in the
# background and the graphical HMI in the foreground. Closing the HMI window
# stops the controller again — so you never end up with a stray/duplicate
# controller. Requires a running MQTT broker on localhost:1883
# (e.g. `brew install mosquitto && brew services start mosquitto`).
#
set -euo pipefail
cd "$(dirname "$0")"

CTRL_FILTER='exec.args=--sim --mqtt'

# Make sure no controller from a previous run is still around (avoids two
# controllers publishing to the same topic).
pkill -f "$CTRL_FILTER" 2>/dev/null || true
sleep 1

# Warn if the broker is not reachable (the controller would fall back to
# console logging and the HMI would stay empty).
if ! nc -z localhost 1883 >/dev/null 2>&1; then
  echo "WARNING: no MQTT broker on localhost:1883."
  echo "  Start one with:  brew services start mosquitto   (or:  mosquitto -v)"
  echo
fi

echo "Starting control system (simulation + MQTT) ..."
mvn -q exec:java -Dexec.args="--sim --mqtt" > /tmp/elevator-controller.log 2>&1 &
CTRL_PID=$!

# Stop the controller whenever this script exits (HMI closed or Ctrl+C).
cleanup() {
  echo
  echo "Stopping control system ..."
  kill "$CTRL_PID" 2>/dev/null || true
  pkill -f "$CTRL_FILTER" 2>/dev/null || true
}
trap cleanup EXIT

# Give the controller a moment to connect to the broker.
sleep 6
echo "Controller log: /tmp/elevator-controller.log"
echo "Starting graphical HMI ... (close the window to stop everything)"
mvn -q exec:java -Dexec.mainClass=de.htwg.sysarch.hmi.HmiSwingApplication
