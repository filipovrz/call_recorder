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

  function statusReady(version, minAndroid) {
    var v = version || "?";
    var min = minAndroid || "8.0";
    return "Безплатно · Версия " + v + " · Android " + min + "+ · Готово за изтегляне";
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
          status.textContent = statusReady(data.version, data.minAndroid);
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
      // Fallback: version.json + direct APK check
      if (!btn) return;
      var apkUrl = "downloads/evtinko-call-recorder.apk";
      btn.setAttribute("href", apkUrl);
      btn.setAttribute("download", "");

      Promise.all([
        fetch("version.json", { cache: "no-store" })
          .then(function (r) {
            return r.ok ? r.json() : {};
          })
          .catch(function () {
            return {};
          }),
        fetch(apkUrl, { method: "HEAD", cache: "no-store" }),
      ])
        .then(function (parts) {
          var meta = parts[0] || {};
          var head = parts[1];
          if (!head.ok) throw new Error("missing");
          if (status) {
            status.textContent = statusReady(meta.version, meta.minAndroid);
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
