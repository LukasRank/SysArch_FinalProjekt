#requires -Version 5
# Start the control system locally with the in-memory PLC simulation and expose it to the
# HMI over the lab MQTT broker. (On the server you run the jar instead - see deploy-control.ps1.)
#
#   .\run-control.ps1           # --sim --mqtt
#   .\run-control.ps1 -Modbus   # --modbus --mqtt (needs the real PLC + easymodbus, CLAUDE.md sec.9)
param([switch]$Modbus)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

$creds = Join-Path $here "mqtt-credentials.ps1"
if (-not (Test-Path $creds)) {
    throw "Missing $creds - copy mqtt-credentials.example.ps1 to mqtt-credentials.ps1 and fill in the password."
}
. $creds

$plc = if ($Modbus) { "--modbus" } else { "--sim" }

$mvn = if (Test-Path "C:\Tools\maven\bin\mvn.cmd") { "C:\Tools\maven\bin\mvn.cmd" } else { "mvn" }
$env:JAVA_HOME = "C:\ST\STM32CubeIDE_2.1.1\STM32CubeIDE\plugins\com.st.stm32cube.ide.jre.win64_3.4.200.202601091518\jre"

Write-Host "Starting control ($plc --mqtt) -> $($env:MQTT_HOST):$($env:MQTT_PORT) base=$($env:MQTT_BASE_TOPIC)" -ForegroundColor Cyan
Push-Location (Join-Path $here "SysArch_Control")
try {
    & $mvn -q exec:java "-Dexec.args=$plc --mqtt" `
        "-Dmqtt.host=$($env:MQTT_HOST)" "-Dmqtt.port=$($env:MQTT_PORT)" `
        "-Dmqtt.baseTopic=$($env:MQTT_BASE_TOPIC)" `
        "-Dmqtt.username=$($env:MQTT_USERNAME)" "-Dmqtt.password=$($env:MQTT_PASSWORD)"
} finally { Pop-Location }
