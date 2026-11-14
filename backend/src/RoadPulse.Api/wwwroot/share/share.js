// Live drive page: reads /v1/shares/{id} every 15 seconds. No cookies, no tracking.
(function () {
  "use strict";
  var id = location.pathname.split("/").pop();
  var el = function (x) { return document.getElementById(x); };
  var timer = null;

  function time(iso) {
    return new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  function ago(iso) {
    var s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
    if (s < 60) return "just now";
    var m = Math.round(s / 60);
    return m < 60 ? m + " min ago" : Math.round(m / 60) + " h ago";
  }
  function km(a, b, c, d) {
    var r = Math.PI / 180, x = (d - b) * r * Math.cos((a + c) / 2 * r), y = (c - a) * r;
    return Math.sqrt(x * x + y * y) * 6371;
  }
  function ended(text) {
    el("card").className = "card ended";
    el("title").textContent = text;
    el("eta").textContent = "";
    el("meta").textContent = "";
    el("actions").hidden = true;
    if (timer) clearInterval(timer);
  }
  function show(v) {
    var who = v.name ? v.name : "Your contact";
    if (v.state === "ARRIVED") {
      el("title").textContent = who + " has arrived at " + v.destination;
      el("eta").textContent = time(v.updatedAt);
    } else {
      el("title").textContent = who + " is driving to " + v.destination;
      el("eta").textContent = "Arrives around " + time(v.eta);
    }
    var left = km(v.lat, v.lon, v.destLat, v.destLon);
    el("meta").textContent = (v.state === "ARRIVED" ? "" : left.toFixed(1) + " km away · ") + "updated " + ago(v.updatedAt);
    var map = el("map");
    map.href = "https://www.openstreetmap.org/?mlat=" + v.lat.toFixed(5) + "&mlon=" + v.lon.toFixed(5) + "#map=15/" + v.lat.toFixed(5) + "/" + v.lon.toFixed(5);
    el("actions").hidden = false;
  }
  function load() {
    fetch("/v1/shares/" + encodeURIComponent(id), { cache: "no-store" })
      .then(function (r) {
        if (r.status === 404) { ended("This drive is no longer being shared."); return null; }
        if (!r.ok) throw new Error(r.status);
        return r.json();
      })
      .then(function (v) { if (v) show(v); })
      .catch(function () { el("meta").textContent = "Cannot reach the server; retrying…"; });
  }
  load();
  timer = setInterval(load, 15000);
})();
