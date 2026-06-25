# Copy this file to "mqtt-credentials.ps1" (which is gitignored) and fill in your
# group's broker credentials. The run-*.ps1 / deploy-control.ps1 scripts source it
# so secrets never live in git or on the command line.
#
#   cp mqtt-credentials.example.ps1 mqtt-credentials.ps1
#
$env:MQTT_HOST       = "ea-pc165.ei.htwg-konstanz.de"  # lab broker (HTWG VPN), or "localhost" via SSH tunnel
$env:MQTT_PORT       = "1883"
$env:MQTT_BASE_TOPIC = "/SysArch/E"                     # Group E
$env:MQTT_USERNAME   = "E"                              # your group's broker user
$env:MQTT_PASSWORD   = "CHANGE_ME"                      # your group's broker password
