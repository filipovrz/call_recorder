# Evtinko Call Recorder

Приложение за запис на телефонни разговори за Android.  
**Издател / запазена марка:** Auctions Evtinko Ltd.

| | |
|---|---|
| **Локална папка** | `andro_call_recorder` |
| **Сайт за теглене** | https://call-recorder.evtinko-bg.com |
| **GitHub** | https://github.com/filipovrz/call_recorder |
| **Статус** | Checkpoint 6 — build оправен; debug APK през Actions |

## Какво е готово

- Android приложение (Kotlin + Jetpack Compose): arm преди обаждане, overlay при звънене, автозапис, списък записи
- Overlay бутон с влачене; споделяне на записи; показване на продължителност
- Запис **без звуков сигнал** към отсрещната страна
- Лендинг страница на Coolice хостинг (`deploy/public_html`)
- Безплатен билд **без Android Studio** (JDK 17 + SDK cmdline / GitHub Actions)

## Какво остава

1. Качи `evtinko-call-recorder.apk` в `public_html/downloads/` (ако още не е на хоста)
2. Тест на реално устройство (двустранен звук зависи от OEM)
3. По-късно: подписан release APK

## Документи

- [HOSTING.md](HOSTING.md) — какво се качва на хоста (File Manager)
- [BUILD.md](BUILD.md) — безплатен билд без Android Studio
- [CHECKPOINTS.md](CHECKPOINTS.md) — чекпойнти
- [История на задачите.txt](История%20на%20задачите.txt) — пълен сейф на чата (обновява се при „сейф“ / „допиши историята“)

## Бърз старт (друг компютър)

```bash
git clone https://github.com/filipovrz/call_recorder.git
cd call_recorder
```

Билд APK (Windows, след JDK 17 + SDK — виж BUILD.md):

```powershell
.\scripts\build-debug-apk.ps1
```

Или в GitHub: **Actions → Build APK → Run workflow** → изтегли artifact.

Качи APK в File Manager:
`/domains/call-recorder.evtinko-bg.com/public_html/downloads/evtinko-call-recorder.apk`

## Правни бележки

Записът на разговори е регулиран различно по държави. Отговорността за спазване на закона е на потребителя. Auctions Evtinko Ltd. предоставя софтуера; не дава правен съвет.

## ©

Auctions Evtinko Ltd.
