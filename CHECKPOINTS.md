# Checkpoint log

Пълна история на разговора: [История на задачите.txt](История%20на%20задачите.txt)

## Checkpoint 7 — 2026-07-21 (сейф преди тест / почивка)

**Готово за тест (v0.1.1)**
- Запис с опит за двете страни: VOICE_CALL + високоговорител/микрофон
- Overlay бутон при звънене (влачене), arm, автозапис
- Списък записи: пускане, споделяне, копие в Изтегляния, „Запази като…“, изтриване
- Лендинг + PHP брояч на изтегляния: https://call-recorder.evtinko-bg.com
- GitHub: https://github.com/filipovrz/call_recorder
- Локален APK: `deploy/public_html/downloads/evtinko-call-recorder.apk`

**Утре (тест)**
1. Качи APK v0.1.1 на хоста (ако още не е)
2. Инсталирай от сайта; overlay + „И двете страни“
3. Провери входящо/изходящо; качество на двете страни; сейф в Изтегляния

**По-късно (след обратна връзка от теста — не блокира сега)**
- Подписан release APK
- Настройки против battery kill (OEM)
- Име на контакт в списъка; вграден плеър
- По желание: PIN/заключване на списъка

## Checkpoint 6 — 2026-07-21 (довършване)

- Оправени Compose импорти; RINGING/armed overlay; duration + share
- Draggable overlay; Actions debug APK

## Checkpoint 5 — 2026-07-20 (rename)

Преименувана локална папка: `andro_kall_recorder` → `andro_call_recorder`.

## Checkpoint 4 — 2026-07-20 (пълен сейф преди почивка)

История, README/HOSTING/BUILD, GitHub, скриптове, Actions workflow; лендинг live.

## Checkpoint 3 — 2026-07-20

Лендинг качен; сайтът се отваря.

## Checkpoint 2 — 2026-07-20

Бранд Auctions Evtinko Ltd.; `applicationId` `com.auctionsevtinko.callrecorder`.

## Checkpoint 1 — 2026-07-20

Android scaffold; запис без warning tone; overlay/arm/auto-record.
