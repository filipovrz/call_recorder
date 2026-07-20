(function () {
  var year = document.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());

  var btn = document.getElementById("download-btn");
  var status = document.getElementById("apk-status");
  if (!btn) return;

  var apkUrl = btn.getAttribute("href");

  fetch(apkUrl, { method: "HEAD" })
    .then(function (res) {
      if (!res.ok) throw new Error("missing");
      if (status) status.textContent = "Безплатно · Версия 0.1.0 · Android 8.0+ · Готово за изтегляне";
    })
    .catch(function () {
      btn.classList.add("is-disabled");
      btn.setAttribute("aria-disabled", "true");
      btn.removeAttribute("download");
      btn.addEventListener("click", function (e) {
        e.preventDefault();
      });
      if (status) {
        status.textContent =
          "APK файлът още не е качен. Очаквайте скоро — или се свържете с Auctions Evtinko Ltd.";
      }
    });
})();
