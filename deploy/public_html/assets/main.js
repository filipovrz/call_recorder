(function () {
  var year = document.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());

  var btn = document.getElementById("download-btn");
  var status = document.getElementById("apk-status");
  var countWrap = document.getElementById("download-count");
  var countNum = document.getElementById("download-count-num");

  function formatCount(n) {
    try {
      return Number(n).toLocaleString("bg-BG");
    } catch (e) {
      return String(n);
    }
  }

  function showCount(n) {
    if (!countWrap || !countNum) return;
    countNum.textContent = formatCount(n);
    countWrap.hidden = false;
  }

  function disableButton() {
    if (!btn) return;
    btn.classList.add("is-disabled");
    btn.setAttribute("aria-disabled", "true");
    btn.removeAttribute("href");
    btn.addEventListener("click", function (e) {
      e.preventDefault();
    });
  }

  fetch("count.php", { cache: "no-store" })
    .then(function (res) {
      if (!res.ok) throw new Error("stats");
      return res.json();
    })
    .then(function (data) {
      var count = data && typeof data.count === "number" ? data.count : 0;
      showCount(count);

      if (data && data.available) {
        if (status) {
          status.textContent =
            "Безплатно · Версия 0.1.1 · Android 8.0+ · Готово за изтегляне";
        }
      } else {
        disableButton();
        if (status) {
          status.textContent =
            "APK файлът още не е качен. Очаквайте скоро — или се свържете с Auctions Evtinko Ltd.";
        }
      }
    })
    .catch(function () {
      // Fallback if PHP is unavailable: try direct APK HEAD.
      if (!btn) return;
      var apkUrl = "downloads/evtinko-call-recorder.apk";
      btn.setAttribute("href", apkUrl);
      btn.setAttribute("download", "");

      fetch(apkUrl, { method: "HEAD", cache: "no-store" })
        .then(function (res) {
          if (!res.ok) throw new Error("missing");
          if (status) {
            status.textContent =
              "Безплатно · Версия 0.1.1 · Android 8.0+ · Готово за изтегляне";
          }
        })
        .catch(function () {
          disableButton();
          if (status) {
            status.textContent =
              "APK файлът още не е качен. Очаквайте скоро — или се свържете с Auctions Evtinko Ltd.";
          }
        });
    });

  if (btn) {
    btn.addEventListener("click", function () {
      // Optimistic bump after a counted download starts.
      if (!countNum || btn.classList.contains("is-disabled")) return;
      window.setTimeout(function () {
        fetch("count.php", { cache: "no-store" })
          .then(function (res) {
            return res.ok ? res.json() : null;
          })
          .then(function (data) {
            if (data && typeof data.count === "number") {
              showCount(data.count);
            }
          })
          .catch(function () {});
      }, 1200);
    });
  }
})();
