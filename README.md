# Evtinko Call Recorder

Приложение за запис на телефонни разговори за Android.  
**Издател / запазена марка:** Auctions Evtinko Ltd.

| | |
|---|---|
| **Локална папка** | `andro_call_recorder` |
| **Сайт за теглене** | https://call-recorder.evtinko-bg.com |
| **GitHub** | https://github.com/filipovrz/call_recorder |
| **Версия** | 0.1.1 |
| **Статус** | Checkpoint 7 — сейф преди реален тест |

## Какво е готово

- Arm / автозапис / overlay бутон при звънене (с влачене)
- Опит за **двете страни**: VOICE_CALL + високоговорител и микрофон
- Запис **без звуков сигнал** към отсрещната страна
- Списък записи: пускане, споделяне, копие в Изтегляния, „Запази като…“
- Безплатен лендинг + брояч на изтегляния (PHP на твоя хост)
- Билд без Android Studio (JDK 17 / GitHub Actions)

## Какво остава (след тест)

1. Реален тест на телефон/таблет
2. По обратна връзка: доработки
3. По-късно: подписан release APK

## Документи

- [HOSTING.md](HOSTING.md) — качване на хоста (File Manager) + брояч
- [BUILD.md](BUILD.md) — безплатен билд без Android Studio
- [CHECKPOINTS.md](CHECKPOINTS.md) — чекпойнти
- [История на задачите.txt](История%20на%20задачите.txt) — сейф на чата

## Бърз старт

```bash
git clone https://github.com/filipovrz/call_recorder.git
cd call_recorder
```

APK (Actions artifact или локално):  
`deploy/public_html/downloads/evtinko-call-recorder.apk`  
→ качи в `public_html/downloads/` на хоста.

## Правни бележки

Записът на разговори е регулиран различно по държави. Отговорността е на потребителя. Auctions Evtinko Ltd. предоставя софтуера; не дава правен съвет.

## ©

Auctions Evtinko Ltd.
