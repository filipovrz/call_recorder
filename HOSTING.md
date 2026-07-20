# Хостинг — call-recorder.evtinko-bg.com (Coolice)

Панел (File Manager):  
https://sofia.coolice.cloud:2222/evo/filemanager/files?path=/domains/call-recorder.evtinko-bg.com/public_html

Локална папка за качване:  
`D:\Filpov Ne Pipai\Projects\andro_call_recorder\deploy\public_html\`

## Важно

В `public_html` се качва **само уеб страницата** (+ по-късно APK).  
**Не** качвай целия Android проект (`app`, `gradle`, `.git` и т.н.) — кодът не трябва да е публичен.

Приложението (APK) още **не е билднато**. Страницата ще работи веднага; бутонът „Изтегли“ ще е неактивен, докато качим APK.

---

## Какво да качиш (откъде → къде)

Отвори локално в Explorer:

`D:\Filpov Ne Pipai\Projects\andro_call_recorder\deploy\public_html`

| От компютъра | В File Manager (`.../public_html`) |
|---|---|
| `index.html` | качи като `index.html` (замести стария, ако има) |
| `robots.txt` | качи като `robots.txt` |
| папка `assets` (цялата) | създай/качи папка `assets` с `styles.css` и `main.js` |
| папка `downloads` | създай/качи папка `downloads` (засега може само с `README.txt`) |

Крайна структура на хоста трябва да е:

```
public_html/
  index.html
  robots.txt
  assets/
    styles.css
    main.js
  downloads/
    README.txt          ← временно
    evtinko-call-recorder.apk   ← по-късно, след build
```

---

## Стъпки в File Manager (без SSH)

1. Влез в линка на File Manager по-горе.
2. Ако в `public_html` има default `index.html` / `index.php` от хостинга — преименувай го на `index.old.html` (за всеки случай).
3. Качи `index.html` и `robots.txt` в корена на `public_html`.
4. Създай папка `assets` → влез в нея → качи:
   - `styles.css`
   - `main.js`  
   (от локалната `deploy\public_html\assets\`)
5. Създай папка `downloads` (празна или с README — няма значение за страницата).
6. Отвори в браузър: https://call-recorder.evtinko-bg.com/

Очакван резултат: зелена лендинг страница „Evtinko Call Recorder“ / Auctions Evtinko Ltd.  
Бутонът „Изтегли APK“ е сив, докато липсва APK — нормално.

---

## После: APK

Когато имаме подписан APK:

1. Преименувай го локално на `evtinko-call-recorder.apk`
2. Качи го в `public_html/downloads/`
3. Обнови страницата — бутонът става активен

---

## SSH (по желание, по-късно)

- Хост: `sofia.coolice.cloud`
- Порт: **22** (не 2222)
- Път: `domains/call-recorder.evtinko-bg.com/public_html`
- Нужен е DirectAdmin **Username** (не имейл)

Докато качваш през File Manager, SSH не е задължителен.

## Продължение на друг компютър

1. `git clone https://github.com/filipovrz/call_recorder.git`
2. Виж [BUILD.md](BUILD.md) за APK
3. Качи APK в `downloads/` както по-горе
4. Историята на работата е в `История на задачите.txt`
