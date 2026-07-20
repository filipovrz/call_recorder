# Хостинг — call-recorder.evtinko-bg.com (Coolice)

Панел (File Manager):  
https://sofia.coolice.cloud:2222/evo/filemanager/files?path=/domains/call-recorder.evtinko-bg.com/public_html

Локална папка за качване:  
`andro_call_recorder/deploy/public_html/`

## Важно

В `public_html` се качва **само уеб страницата** (+ APK).  
**Не** качвай целия Android проект (`app`, `gradle`, `.git` и т.н.).

Дистрибуцията е **безплатна** от този хост — без Google Play.

---

## Какво да качиш

| От компютъра | В File Manager (`.../public_html`) |
|---|---|
| `index.html` | `index.html` |
| `robots.txt` | `robots.txt` |
| `download.php` | `download.php` (брои тегленията + сервира APK) |
| `count.php` | `count.php` (показва брояча на страницата) |
| папка `assets/` | `assets/` (`styles.css`, `main.js`) |
| папка `data/` | `data/` (`.htaccess` + `downloads.json`) |
| папка `downloads/` | `downloads/` (`.htaccess` + `evtinko-call-recorder.apk`) |

Крайна структура:

```
public_html/
  index.html
  robots.txt
  download.php
  count.php
  assets/
    styles.css
    main.js
  data/
    .htaccess
    downloads.json      ← брояч (започва от 0)
  downloads/
    .htaccess           ← блокира директен линк към APK
    evtinko-call-recorder.apk
```

След качване: правата на `data/` и `data/downloads.json` трябва да позволяват запис от PHP
(обикновено 755 за папка / 644 за файл; ако броенето не работи — пробвай 775 / 666).

---

## Брояч на изтегляния

- Бутонът води към `download.php` → +1 към брояча → тегли APK.
- На страницата се вижда: „Изтеглено N пъти“ (от `count.php`).
- Директният URL към `.apk` е блокиран, за да не се заобикаля броенето.

Проверка: https://call-recorder.evtinko-bg.com/count.php  
трябва да върне JSON като `{"count":0,"available":true,"version":"0.1.0"}`.

---

## APK

Локално копие след Actions билд:

`deploy/public_html/downloads/evtinko-call-recorder.apk`

Качи го в `public_html/downloads/` със същото име.

---

## SSH (по желание)

- Хост: `sofia.coolice.cloud`
- Порт: **22** (не 2222)
- Път: `domains/call-recorder.evtinko-bg.com/public_html`
