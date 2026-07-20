# Andro Call Recorder

Android приложение за запис на телефонни разговори (Kotlin + Jetpack Compose).

## Статус

**Checkpoint 1** — работен scaffold + запис/UI/overlay. Готово за доуточняване и тест на устройство.

## Какво прави

- Лесно включване **преди приемане** или **по време** на обаждане:
  - превключвател „Подготви запис за следващото обаждане“
  - floating бутон (overlay) при звънене
  - автозапис при отговор
- Запис **без звуков сигнал** към отсрещната страна (няма warning tone в call path)
- Локални `.m4a` файлове в app storage
- Списък, пускане и изтриване на записи

## Правни бележки

Записът на разговори е регулиран различно по държави. В България и в много юрисдикции може да е нужно съгласие. Приложението **не** дава правен съвет — отговорността е на потребителя.

## Изисквания

- Android 8.0+ (API 26), target 34
- Права: микрофон, телефонно състояние, call log, нотификации (API 33+)
- Overlay право (по желание) за бутона върху екрана на обаждането

## Изграждане

Отвори проекта в Android Studio (Ladybug/Koala или по-нова) и синхронизирай Gradle.

```bash
./gradlew :app:assembleDebug
```

Инсталирай debug APK на устройство с реална SIM/обаждания за тест.

## Структура

- `app/.../service/CallRecordingService` — foreground запис
- `app/.../service/CallOverlayService` — бутон по време на звънене
- `app/.../receiver/PhoneStateReceiver` — RINGING / OFFHOOK / IDLE
- `app/.../recording/CallAudioRecorder` — MediaRecorder + fallback източници
- `app/.../ui/` — Compose екран и настройки

## Ограничения на Android

От Android 9+ достъпът до чистия call uplink/downlink (`VOICE_CALL`) е силно ограничен за обикновени приложения. Качеството (една или две страни) зависи от OEM/ROM. Приложението пробва избрания източник и fallback-и.

## Чекпойнти

Виж [CHECKPOINTS.md](CHECKPOINTS.md).

## Следващи стъпки

1. Тест на конкретно устройство
2. GitHub repository + първи push (след като shell/git средата работи)
3. Допълнителни детайли от теб (UX, auto rules, формат, cloud sync и т.н.)
