# Checkpoint log / chat saves

След всяко по-голямо действие тук се записва кратко резюме (checkpoint).

## Checkpoint 2 — 2026-07-20

**Какво е готово**
- Бранд: **Auctions Evtinko Ltd.** / display name `Evtinko Call Recorder`
- `applicationId`: `com.auctionsevtinko.callrecorder`
- README насочен към APK дистрибуция от собствен хостинг (не Play Store)
- Footer в UI с издателя

**GitHub**
- Парола от чата **не е използвана** и **не е записана** никъде в проекта
- `gh` CLI липсваше при последна проверка; нужен е PAT или SSH + remote URL
- Препоръка: смени паролата на акаунта веднага (беше споделена в plain text)

**Следва**
- Безопасен push към GitHub
- Подписан release APK + линк/страница за изтегляне от хостинга
- Допълнителни функционални детайли от издателя

## Checkpoint 1 — 2026-07-20

**Какво е готово**
- Android Kotlin проект с Compose UI
- Запис чрез foreground service без warning tone
- Arm за следващо обаждане, автозапис, overlay
- Списък със записи
- Локален git commit `Initial Android call recorder scaffold`
