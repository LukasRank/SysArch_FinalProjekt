#requires -Version 5
# Build the self-contained control jar and copy it to the lab server (ea-pc165) via scp.
# On the server the broker AND the control system run on the same host, so the control
# reaches the broker as localhost:1883 and the PLC as ea-pc111:506 (CLAUDE.md sec.7).
#
#   .\deploy-control.ps1 -RzUser myrzlogin
#   .\deploy-control.ps1 -RzUser myrzlogin -Run     # also start it on the server afterwards
param(
    [Parameter(Mandatory = $true)][string]$RzUser,   # your HTWG RZ login (FHKN.<RzUser>)
    [string]$Server = "ea-pc165.ei.htwg-konstanz.de",
    [string]$RemoteDir = "~/elevator",
    [switch]$Run
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$ctrl = Join-Path $here "SysArch_Control"
$ssh  = "FHKN.$RzUser@$Server"

Write-Host "[1/3] Building self-contained jar..." -ForegroundColor Cyan
Push-Location $ctrl
try { mvn -q -DskipTests clean package } finally { Pop-Location }

$jar = Join-Path $ctrl "target\elevator-control-0.1.0.jar"
if (-not (Test-Path $jar)) { throw "Build did not produce $jar" }

Write-Host "[2/3] Copying jar to $ssh : $RemoteDir" -ForegroundColor Cyan
ssh $ssh "mkdir -p $RemoteDir"
scp $jar "${ssh}:$RemoteDir/elevator-control.jar"

# Read credentials so we can print (and optionally run) the right command.
$creds = Join-Path $here "mqtt-credentials.ps1"
if (Test-Path $creds) { . $creds }
$user = if ($env:MQTT_USERNAME) { $env:MQTT_USERNAME } else { "E" }

# On the server the broker is local; the password is passed at launch, never stored on disk.
$remoteCmd = "java -Dmqtt.host=localhost -Dmqtt.username=$user -Dmqtt.password=<PASSWORD> " +
             "-jar $RemoteDir/elevator-control.jar --sim --mqtt"

Write-Host "[3/3] Done. Run on the server with:" -ForegroundColor Green
Write-Host "    ssh $ssh" -ForegroundColor Yellow
Write-Host "    $remoteCmd" -ForegroundColor Yellow
Write-Host "  (use --modbus instead of --sim once the easymodbus wiring is in place, CLAUDE.md sec.9)"

if ($Run) {
    if (-not $env:MQTT_PASSWORD) { throw "MQTT_PASSWORD not set (fill mqtt-credentials.ps1) - cannot auto-run." }
    Write-Host "Starting control on $Server ..." -ForegroundColor Cyan
    $cmd = "java -Dmqtt.host=localhost -Dmqtt.username=$user -Dmqtt.password=$($env:MQTT_PASSWORD) " +
           "-jar $RemoteDir/elevator-control.jar --sim --mqtt"
    ssh $ssh $cmd
}
