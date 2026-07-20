# Checkpoint log / chat saves

След всяко по-голямо действие тук се записва кратко резюме (checkpoint) и статусът на чата.

## Checkpoint 1 — 2026-07-20

**Какво е готово**
- Android Kotlin проект `AndroCallRecorder` (Compose UI)
- Запис чрез foreground service без warning tone към отсрещната страна
- Подготовка за следващо обаждане (`armed`)
- Автозапис при OFFHOOK
- Overlay бутон при звънене (SYSTEM_ALERT_WINDOW)
- Списък със записи + play/delete
- README с правна бележка

**Какво чака следващи детайли от потребителя**
- Целево устройство / производител
- Автоматичен vs ръчен режим по подразбиране
- GitHub remote URL / акаунт
- Език на UI (BG по подразбиране сега)
- Иска ли се запис и на двете страни с по-агресивни техники (OEM-specific)

**Технически риск**
- На Android 9+ двустранен запис често е ограничен; има fallback верига от audio sources.
