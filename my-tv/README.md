# Mi TV: Hisense HiSmartTV A4

Hisense HiSmartTV A4 (device `xiaoyushan`, MStar/MediaTek), Android TV 9, en `192.168.100.5`.
Cambios hechos el 2026-10-21 sobre el repo original
([Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv), remote `upstream`).

## Qué tiene la TV ahora

- **Debloat:** `debloat.txt` (3 paquetes) más el Home de Google (`com.google.android.tvlauncher`)
  desactivado. Los otros 21 paquetes desactivados ya venían así de antes (ver `estado-original.md`).
- **Launcher** `com.gathin.tvhome` con los cambios de este fork:
  - Fuente Inter (OFL) en vez de Outfit; sin saludo; hora y fecha en una línea; sin título sobre el dock.
  - Dock de vidrio esmerilado: copia chica y desenfocada del fondo (`WallpaperPlayer.backdrop`,
    `DockDecoration`), porque Android 9 no desenfoca en vivo.
  - Tecla Input del control: en Home abre el menú del header; sobre un HDMI o una app abre un panel
    flotante a la derecha (`InputsPanelActivity`) con tilde en la entrada actual. El menú de entradas vive
    en `InputsMenu`, compartido por los dos. El selector de Google (`VIEW_INPUTS`) se fue con su Home.
  - Protector de pantalla (`ClockDream`): la foto del tema, hora abajo a la derecha y fecha "Mié 21 Oct".
  - Tarjeta "reproduciendo" sobre el dock (`NowPlaying`): tapa, tema, artista y ⏮ ⏯ ⏭ de Spotify
    (`music_app` en `config.xml`). Arriba desde el dock entra a la tarjeta; arriba otra vez, a la barra.
    Esta TV es low-RAM y Android no deja habilitar `MediaListener` (lee todas las sesiones): la tarjeta se
    conecta al `MediaBrowserService` de Spotify, que deja usar el control a cualquier app. Con Spotify
    cerrado, Play lo abre; si estaba en pausa, Spotify se pone al frente y Home vuelve solo a los ~4 s.
- **Tema** `theme/`: foto "Lofoten, Norway" (CC0, ver `theme/CREDITS.txt`), biblioteca de apps activada.
- **Dock:** YouTube, Netflix, Max, Spotify (`launcher/app/src/main/res/values/config.xml`).

## Reinstalar / actualizar el launcher

Con la depuración USB prendida en la TV:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
launcher\build-and-install.ps1 -TvHost 192.168.100.5 -InputKeyService
tools\theme.ps1 -TvHost 192.168.100.5 my-tv\theme
$env:TV_HOST = '192.168.100.5'
tools\venv\Scripts\python.exe -I tools\tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
```

`-InputKeyService` va siempre: sin él la tecla Input del control no hace nada.

**Firma del APK:** se firma con la keystore de debug de la compu donde se compila
(`%USERPROFILE%\.android\debug.keystore`). Si compilás en otra máquina, la TV rechaza la actualización y
hay que desinstalar el launcher primero, lo que borra orden, biblioteca y tema. Guardá esa keystore fuera
del repo (no se sube: es una clave).

## Volver todo para atrás

```powershell
my-tv\restaurar-todo.ps1 -TvHost 192.168.100.5 -DryRun   # ver qué va a hacer
my-tv\restaurar-todo.ps1 -TvHost 192.168.100.5           # hacerlo
```

Reactiva lo que se desactivó, devuelve el Home y el protector de Google y desinstala el launcher.
`-KeepLauncher` hace todo menos desinstalarlo. Solo el tema: `tools\theme.ps1 -TvHost 192.168.100.5 -Reset`.

## Ojo

- Cada reinstalación del launcher borra la elección de Home ("Result set changed, dropping preferred
  activity": el menú de fábrica de Hisense cuenta como candidato pero no aparece en el selector). Después
  de reinstalar, apretá Home y elegí **Clean Your TV → Siempre**.

- Si al apretar Home aparece "¿qué app usar?", elegí **Clean Your TV → Siempre**. La otra opción,
  `com.hisense.uifac`, es el menú de fábrica de Hisense: no la abras.
- Una actualización de firmware puede reactivar paquetes: `tools\disable.ps1 -TvHost 192.168.100.5 my-tv\debloat.txt`.
- Al terminar de usar ADB, apagá la depuración USB: el puerto 5555 queda abierto y sin cifrar en la red.

## Rendimiento medido (tools/bench.py, 2026-10-21)

| | Home de Google | Este launcher |
|---|---|---|
| Respuesta a una tecla (p50) | 26 ms | 25 ms |
| Jank | 0% | 0% |
| RAM del launcher | 52 MB | 30 MB |
| Arranque en frío (foto 3840 px) | 600 ms | 774 ms |

La ganancia es publicidad, privacidad (el launcher no tiene permiso de internet) y uso; en rendimiento,
22 MB de RAM. La foto ya se achicó a 1920×1080 para el arranque en frío; falta medirlo de nuevo.
