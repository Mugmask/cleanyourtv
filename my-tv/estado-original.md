# Estado de la TV antes del 2026-10-21

Tomado del backup de ese día (el backup completo queda fuera del repo: tiene el serial y el android_id).

- **Home:** `com.google.android.tvlauncher/.MainActivity`
- **Protector de pantalla:** `com.android.dreams.basic/com.android.dreams.basic.Colors`
- **Animaciones:** `window_animation_scale`, `transition_animation_scale` y `animator_duration_scale` en 0.5
- **Paquetes ya desactivados** (21, no los tocó esta sesión y `restaurar-todo.ps1` los deja así):

```
com.android.printspooler
com.google.android.backdrop
com.google.android.feedback
com.google.android.katniss
com.google.android.leanbacklauncher
com.google.android.leanbacklauncher.recommendations
com.google.android.marvin.talkback
com.google.android.play.games
com.google.android.speech.pumpkin
com.google.android.tv.bugreportsender
com.google.android.tvrecommendations
com.google.android.videos
com.google.android.youtube.tvkids
com.google.android.youtube.tvmusic
com.hisense.app.logreport
com.hisense.hibrowser
com.hisense.storemode
com.hisense.tv.dlna
com.hisense.tv.hisremotenow
com.hisense.tv.hisremotenowserver
fusion.android.tv.demo
```

Para volver a activar alguno: `tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable <paquete>'`
(por ejemplo `com.google.android.katniss` y `com.google.android.speech.pumpkin` devuelven el Asistente).
