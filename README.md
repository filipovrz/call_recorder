# Evtinko Call Recorder

Приложение за запис на телефонни разговори за Android.  
**Запазена марка / издател:** Auctions Evtinko Ltd.

## Дистрибуция

Приложението **не** се публикува в Google Play. Крайните потребители го теглят и инсталират като APK от хостинг акаунта на издателя (sideload). Нужно е „Инсталиране от неизвестни източници“ / разрешение за браузъра/файловия мениджър.

Build артефакт: `app/build/outputs/apk/release/app-release.apk` (след подписване) или debug за вътрешни тестове.

## Статус

**Checkpoint 2** — бранд Auctions Evtinko Ltd.; дистрибуция през собствен хостинг; локален git commit. GitHub remote чака безопасна автентикация (PAT/SSH), без парола в чата.

## Какво прави

- Лесно включване **преди приемане** или **по време** на обаждане (arm / overlay / автозапис)
- Запис **без звуков сигнал** към отсрещната страна
- Локални `.m4a` записи + списък play/delete

## Правни бележки

Записът на разговори е регулиран различно по държави. Отговорността за спазване на закона е на потребителя. Auctions Evtinko Ltd. предоставя софтуерния инструмент; не дава правен съвет.

## Изисквания

- Android 8.0+ (API 26), target 34
- `applicationId`: `com.auctionsevtinko.callrecorder`
- Права: микрофон, телефонно състояние, call log, нотификации; overlay по желание

## Изграждане

Отвори в Android Studio и синхронизирай Gradle:

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Качи подписания release APK на хостинга и дай линк за изтегляне.

## GitHub

Локалното хранилище е инициализирано. За remote **не** се ползва парола за акаунт (GitHub я отхвърля за git). Нужни са:

1. Personal Access Token (classic/`repo`) **или** SSH ключ  
2. `git remote add origin <url>`  
3. `git push -u origin master`

Имейл за контакт/акаунт: виж настройките на GitHub акаунта на издателя — **не** записвай пароли в README или в кода.

## Структура

- `service/CallRecordingService` — foreground запис  
- `service/CallOverlayService` — бутон при звънене  
- `receiver/PhoneStateReceiver` — RINGING / OFFHOOK / IDLE  
- `recording/CallAudioRecorder` — MediaRecorder + fallback  
- `ui/` — Compose екран  

## Чекпойнти

Виж [CHECKPOINTS.md](CHECKPOINTS.md).

## ©

Auctions Evtinko Ltd.
