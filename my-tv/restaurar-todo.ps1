# Deja la TV (Hisense HiSmartTV A4, xiaoyushan, Android 9) como estaba antes de esta sesión del 2026-10-21:
# vuelve a activar lo que se desactivó, devuelve el Home y el protector de pantalla de Google y desinstala el
# launcher (con su tema y sus servicios del control). Los 21 paquetes que ya venían desactivados de antes quedan
# como estaban (ver estado-original.md). No depende de ~/.cleanyourtv: los comandos van escritos acá.
#
# Uso (con la depuración USB prendida en la TV):
#   my-tv\restaurar-todo.ps1 -TvHost 192.168.100.5 -DryRun     muestra los comandos sin tocar nada
#   my-tv\restaurar-todo.ps1 -TvHost 192.168.100.5             los ejecuta
#   my-tv\restaurar-todo.ps1 -TvHost 192.168.100.5 -KeepLauncher   todo menos desinstalar el launcher
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$DryRun,
    [switch]$KeepLauncher
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$cmds = @(
    # Lo que desactivó esta sesión (my-tv\debloat.txt) y el Home de Google.
    'pm enable com.mstar.android.tv.disclaimercustomization',
    'pm enable android.autoinstalls.config.hisense.xiaoyushan',
    'pm enable com.jamdeo.tv.mediacenter',
    'pm enable com.google.android.tvlauncher',
    'cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity',
    # El protector de pantalla de colores que tenía.
    'settings put secure screensaver_components com.android.dreams.basic/com.android.dreams.basic.Colors',
    # Valores de animación registrados antes de empezar.
    'settings put global window_animation_scale 0.5',
    'settings put global transition_animation_scale 0.5',
    'settings put global animator_duration_scale 0.5'
)
if (-not $KeepLauncher) {
    # Desinstalar el launcher se lleva su tema, su servicio de la tecla Input y su preferencia de Home.
    $cmds += 'pm uninstall com.gathin.tvhome'
}
$cmds += 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1'

if ($DryRun) {
    $cmds | ForEach-Object { Write-Host $_ }
    return
}
# tv.py --batch corre un comando por llamada: encadenados, pm puede cortar después del primero.
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
Write-Host 'Listo. Home debería ser com.google.android.tvlauncher/.MainActivity (última línea de arriba).'
Write-Host 'Al terminar, apagá la depuración USB en Opciones de desarrollador.'
