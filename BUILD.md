# Безплатен билд на APK (без Android Studio)

Два начина — и двата са безплатни.

## Начин A (препоръчан за теб): GitHub Actions в облака

1. Създай безплатен акаунт/repo в GitHub.
2. Качи този проект (или дай Personal Access Token — **не парола** — и аз пушвам).
3. В GitHub: **Actions** → **Build APK** → **Run workflow**.
4. Изчакай зелена отметка → отвори run → **Artifacts** → изтегли `evtinko-call-recorder-debug`.
5. Преименувай APK на `evtinko-call-recorder.apk`.
6. Качи го в хоста: `public_html/downloads/evtinko-call-recorder.apk`.

Готово — бутонът на сайта става активен.

## Начин B: на този компютър (JDK 17 + Android command-line tools)

Нужно е веднъж:

1. **OpenJDK 17** (безплатно) — чрез winget:
   ```powershell
   winget install --id Microsoft.OpenJDK.17 -e
   ```
2. **Android command-line tools** (безплатно от Google) — скриптът `scripts\setup-android-sdk.ps1` ги сваля.
3. После билд:
   ```powershell
   cd "D:\Filpov Ne Pipai\Projects\andro_call_recorder"
   .\scripts\build-debug-apk.ps1
   ```
4. Готовият файл е около:
   `app\build\outputs\apk\debug\app-debug.apk`
5. Копирай/преименувай до `evtinko-call-recorder.apk` и качи в `downloads/` на хоста.

Debug APK е достатъчен за тест и sideload от твоя сайт. По-късно правим подписан release.
