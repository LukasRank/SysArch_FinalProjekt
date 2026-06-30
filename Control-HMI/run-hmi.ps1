#requires -Version 5
# Start the graphical HMI (Swing) on this laptop and connect it to the lab MQTT broker.
# The HMI talks ONLY MQTT - it never imports control-system code (CLAUDE.md sec.8).
#
#   .\run-hmi.ps1            # graphical HMI
#   .\run-hmi.ps1 -Console   # console HMI (headless/debug)
param([switch]$Console)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

$creds = Join-Path $here "mqtt-credentials.ps1"
if (-not (Test-Path $creds)) {
    throw "Missing $creds - copy mqtt-credentials.example.ps1 to mqtt-credentials.ps1 and fill in the password."
}
. $creds

$main = if ($Console) { "de.htwg.sysarch.hmi.HmiApplication" } else { "de.htwg.sysarch.hmi.HmiSwingApplication" }

$mvn = if (Test-Path "C:\Tools\maven\bin\mvn.cmd") { "C:\Tools\maven\bin\mvn.cmd" } else { "mvn" }
$env:JAVA_HOME = "C:\ST\STM32CubeIDE_2.1.1\STM32CubeIDE\plugins\com.st.stm32cube.ide.jre.win64_3.4.200.202601091518\jre"

Write-Host "Starting HMI ($main) -> $($env:MQTT_HOST):$($env:MQTT_PORT) base=$($env:MQTT_BASE_TOPIC)" -ForegroundColor Cyan
Push-Location (Join-Path $here "SysArch_HMI")
try {
    & $mvn -q exec:java "-Dexec.mainClass=$main" `
        "-Dmqtt.host=$($env:MQTT_HOST)" "-Dmqtt.port=$($env:MQTT_PORT)" `
        "-Dmqtt.baseTopic=$($env:MQTT_BASE_TOPIC)" `
        "-Dmqtt.username=$($env:MQTT_USERNAME)" "-Dmqtt.password=$($env:MQTT_PASSWORD)"
} finally { Pop-Location }
