"use strict";
// RoadPulse moderation page. Talks only to this server's /v1/moderation API with the moderator key
// (kept in sessionStorage for this tab). All server text is inserted with textContent, never as HTML.
(() => {
  const KEY = "roadpulse.moderatorKey";
  const TYPES = {
    POLICE: { label: "Police", color: "#1E66F5", short: "POL" },
    CRASH: { label: "Crash", color: "#D20F39", short: "CR" },
    HAZARD: { label: "Hazard", color: "#DF8E1D", short: "HZ" },
    TRAFFIC_JAM: { label: "Traffic jam", color: "#E8590C", short: "JAM" },
    ROAD_CLOSED: { label: "Road closed", color: "#4C4F69", short: "X" },
  };
  const REASONS = { FAKE: "fake", SPAM: "spam", OFFENSIVE: "offensive", WRONG_LOCATION: "wrong location" };
  const $ = (id) => document.getElementById(id);
  let key = sessionStorage.getItem(KEY) || "";
  let tab = "queue";
  let timer = null;

  // ---- helpers -------------------------------------------------------------------------------
  function el(tag, props = {}, ...children) {
    const e = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k === "class") e.className = v;
      else if (k === "text") e.textContent = v;
      else if (k === "bg") e.style.backgroundColor = v; // CSSOM: allowed by the page's CSP, unlike style=""
      else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v);
    }
    for (const c of children) if (c != null) e.append(c);
    return e;
  }

  async function api(path, options = {}) {
    const res = await fetch("/v1/moderation" + path, {
      ...options,
      headers: { "X-Moderator-Key": key, ...(options.body ? { "Content-Type": "application/json" } : {}) },
    });
    if (res.status === 401) { signOut("Your key was not accepted. Sign in again."); throw new Error("unauthorized"); }
    if (res.status === 429) throw new Error("Too many requests. Wait a minute and try again.");
    if (res.status === 404) throw new Error("Not found.");
    if (!res.ok) {
      let detail = "";
      try { const p = await res.json(); detail = Object.values(p.errors || {}).flat().join(" ") || p.title || ""; } catch { /* not JSON */ }
      throw new Error(detail || "Request failed (HTTP " + res.status + ").");
    }
    return res.status === 204 ? null : res.json();
  }

  function toast(text) {
    const t = $("toast");
    t.textContent = text; t.hidden = false;
    clearTimeout(toast.t); toast.t = setTimeout(() => (t.hidden = true), 3500);
  }

  function ago(iso) {
    const min = Math.max(0, Math.round((Date.now() - new Date(iso)) / 60000));
    return min < 1 ? "just now" : min < 60 ? min + " min ago" : min < 1440 ? Math.floor(min / 60) + " h ago" : Math.floor(min / 1440) + " d ago";
  }
  function until(iso) {
    const min = Math.round((new Date(iso) - Date.now()) / 60000);
    return min <= 0 ? "expired" : min < 60 ? "expires in " + min + " min" : "expires in " + Math.floor(min / 60) + " h";
  }
  const shortId = (id) => id.slice(0, 8);
  const when = (iso) => new Date(iso).toLocaleString();

  // ---- reason dialog -------------------------------------------------------------------------
  function askReason(title, hint, withRemoveAll) {
    return new Promise((resolve) => {
      const d = $("reasonDialog");
      $("reasonTitle").textContent = title;
      $("reasonHint").textContent = hint;
      $("reasonText").value = "";
      $("removeAllRow").hidden = !withRemoveAll;
      $("removeAll").checked = true;
      const done = (value) => { d.close(); resolve(value); };
      $("reasonCancel").onclick = () => done(null);
      $("reasonForm").onsubmit = (e) => {
        e.preventDefault();
        const reason = $("reasonText").value.trim();
        if (reason.length < 3) { $("reasonText").focus(); return; }
        done({ reason, removeActiveReports: $("removeAll").checked });
      };
      d.oncancel = () => resolve(null);
      d.showModal();
      $("reasonText").focus();
    });
  }

  async function act(label, hint, path, withRemoveAll = false) {
    const answer = await askReason(label, hint, withRemoveAll);
    if (!answer) return;
    try {
      const body = withRemoveAll ? answer : { reason: answer.reason };
      await api(path, { method: "POST", body: JSON.stringify(body) });
      toast(label + ": done");
      await load();
    } catch (e) { if (e.message !== "unauthorized") toast(e.message); }
  }

  // ---- rendering -----------------------------------------------------------------------------
  function statusPill(status) {
    const map = { PendingReview: ["hidden", "Hidden for review"], Active: ["active", "On the map"], Removed: ["removed", "Removed"] };
    const [cls, text] = map[status] || ["removed", status];
    return el("span", { class: "pill " + cls, text });
  }

  function reportItem(item) {
    const r = item.report;
    const t = TYPES[r.type] || { label: r.type, color: "#777", short: "?" };
    const flags = Object.entries(item.flagReasons || {}).map(([k, n]) => n + " " + (REASONS[k] || k)).join(", ");
    const map = "https://www.openstreetmap.org/?mlat=" + r.lat + "&mlon=" + r.lon + "#map=17/" + r.lat + "/" + r.lon;
    const actions = el("div", { class: "actions" });
    if (item.status !== "Removed")
      actions.append(el("button", { class: "danger", text: "Remove", onclick: () =>
        act("Remove report", "Takes the " + t.label.toLowerCase() + " report off every map.", "/reports/" + r.id + "/remove") }));
    if (item.status !== "Active")
      actions.append(el("button", { text: "Restore", onclick: () =>
        act("Restore report", "Puts it back on the map. Later flags will not hide it automatically.", "/reports/" + r.id + "/restore") }));
    actions.append(item.deviceBlocked
      ? el("button", { class: "ghost", text: "Unblock device", onclick: () =>
          act("Unblock device", "Device " + shortId(item.deviceId) + " can report again.", "/devices/" + item.deviceId + "/unblock") })
      : el("button", { class: "ghost", text: "Block device", onclick: () =>
          act("Block device", "Device " + shortId(item.deviceId) + " can no longer report or vote.", "/devices/" + item.deviceId + "/block", true) }));

    return el("div", { class: "item" },
      el("div", { class: "icon", bg: t.color, text: t.short }),
      el("div", {},
        el("div", { class: "title" }, t.label, statusPill(item.status), item.deviceBlocked ? el("span", { class: "pill blocked", text: "Device blocked" }) : null),
        el("div", { class: "meta" }, "Reported " + ago(r.createdAt) + " · " + until(r.expiresAt) + " · " +
          r.confirmations + " still there · " + r.dismissals + " not there"),
        el("div", { class: "meta" }, item.flags ? item.flags + " abuse flag" + (item.flags > 1 ? "s" : "") + ": " + flags : "No abuse flags"),
        el("div", { class: "meta" }, "Reporter ", el("span", { class: "mono", text: shortId(item.deviceId) }),
          ": " + item.deviceReports + (item.deviceReports === 1 ? " report, " : " reports, ") + item.deviceRemovedReports + " removed · ",
          el("a", { href: map, target: "_blank", rel: "noopener noreferrer", text: "Show on map" }), " · ",
          el("span", { class: "mono", text: "ID " + r.id }))),
      actions);
  }

  const KINDS = { SHOP: "Shop", MALL: "Shopping mall", FOOD: "Food", CAFE: "Cafe", CLINIC: "Clinic", HOSPITAL: "Hospital",
    OFFICE: "Office", HOTEL: "Hotel", RESIDENCE: "Residence", BUILDING: "Building", ENTRANCE: "Entrance", PARKING: "Parking",
    FUEL: "Petrol station", SCHOOL: "School", WORSHIP: "Place of worship", BANK: "Bank", GYM: "Gym", OTHER: "Other" };

  function placeItem(item) {
    const p = item.place;
    const map = "https://www.openstreetmap.org/?mlat=" + p.lat + "&mlon=" + p.lon + "#map=18/" + p.lat + "/" + p.lon;
    return el("div", { class: "item" },
      el("div", { class: "icon", bg: "#0F6E56", text: "NEW" }),
      el("div", {},
        el("div", { class: "title" }, p.name, el("span", { class: "pill active", text: KINDS[p.kind] || p.kind })),
        p.note ? el("div", { class: "meta", text: "Note: " + p.note }) : null,
        el("div", { class: "meta" }, "Added " + ago(item.createdAt) + " · ",
          el("a", { href: map, target: "_blank", rel: "noopener noreferrer", text: "Show on map" })),
        el("div", { class: "meta" }, "Added by ", el("span", { class: "mono", text: shortId(item.deviceId) }),
          ": " + item.devicePlaces + (item.devicePlaces === 1 ? " place, " : " places, ") + item.deviceRejected + " rejected · ",
          el("span", { class: "mono", text: "ID " + p.id }))),
      el("div", { class: "actions" },
        el("button", { text: "Approve", onclick: () =>
          act("Approve place", "\"" + p.name + "\" will appear in everyone's search.", "/places/" + p.id + "/approve") }),
        el("button", { class: "danger", text: "Reject", onclick: () =>
          act("Reject place", "\"" + p.name + "\" stays hidden. It is deleted after 90 days.", "/places/" + p.id + "/reject") })));
  }

  function renderList(container, items, emptyText) {
    container.replaceChildren(...(items.length ? items.map(reportItem) : [el("div", { class: "empty", text: emptyText })]));
  }

  function deviceItem(d) {
    return el("div", { class: "item" },
      el("div", { class: "icon", bg: "#534AB7", text: "DEV" }),
      el("div", {},
        el("div", { class: "title" }, "Device ", el("span", { class: "mono", text: d.id }), d.blocked ? el("span", { class: "pill blocked", text: "Blocked" }) : null),
        el("div", { class: "meta" }, "Registered " + when(d.createdAt) + (d.blockedAt ? " · blocked " + when(d.blockedAt) : "")),
        el("div", { class: "meta" }, d.reports + " reports · " + d.activeReports + " on the map now · " + d.removedReports + " removed")),
      el("div", { class: "actions" }, d.blocked
        ? el("button", { text: "Unblock", onclick: () => act("Unblock device", "This device can report again.", "/devices/" + d.id + "/unblock").then(() => lookup(d.id)) })
        : el("button", { class: "danger", text: "Block", onclick: () => act("Block device", "This device can no longer report or vote.", "/devices/" + d.id + "/block", true).then(() => lookup(d.id)) })));
  }

  // ---- data ----------------------------------------------------------------------------------
  async function load() {
    try {
      if (tab === "queue") {
        const items = await api("/queue");
        renderList($("queueList"), items, "Nothing to review. Reports appear here when drivers flag them.");
        const hidden = items.filter((i) => i.status === "PendingReview").length;
        $("queueCount").hidden = hidden === 0; $("queueCount").textContent = hidden;
      } else if (tab === "places") {
        const items = await api("/places");
        $("placesList").replaceChildren(...(items.length ? items.map(placeItem)
          : [el("div", { class: "empty", text: "No new places to check. They appear here when drivers add a missing place." })]));
        $("placesCount").hidden = items.length === 0; $("placesCount").textContent = items.length;
      } else if (tab === "recent") {
        renderList($("recentList"), await api("/reports?limit=100"), "No reports yet.");
      } else if (tab === "audit") {
        const rows = await api("/audit?limit=200");
        $("auditRows").replaceChildren(...(rows.length ? rows.map((a) => el("tr", {},
          el("td", { text: when(a.at) }), el("td", { text: a.moderator }), el("td", { text: a.action.replaceAll("_", " ").toLowerCase() }),
          el("td", {}, a.targetType + " ", el("span", { class: "mono", text: shortId(a.targetId) })), el("td", { text: a.reason })))
          : [el("tr", {}, el("td", { colspan: "5", class: "muted", text: "No moderator actions yet." }))]));
      }
      $("updated").textContent = "Updated " + new Date().toLocaleTimeString();
    } catch (e) { if (e.message !== "unauthorized") toast(e.message); }
  }

  async function lookup(id) {
    const out = $("lookupResult");
    if (!/^[0-9a-f-]{36}$/i.test(id)) { out.replaceChildren(el("div", { class: "empty", text: "That is not an ID. Copy the full ID shown under a report." })); return; }
    try { out.replaceChildren(reportItem(await api("/reports/" + id))); return; } catch (e) { if (e.message !== "Not found.") { toast(e.message); return; } }
    try { out.replaceChildren(deviceItem(await api("/devices/" + id))); } catch (e) {
      out.replaceChildren(el("div", { class: "empty", text: e.message === "Not found." ? "No report or device with that ID." : e.message }));
    }
  }

  // ---- navigation and sign-in ----------------------------------------------------------------
  function show(name) {
    tab = name;
    document.querySelectorAll("[role=tab]").forEach((b) => b.setAttribute("aria-selected", String(b.dataset.tab === name)));
    document.querySelectorAll("[data-panel]").forEach((p) => (p.hidden = p.dataset.panel !== name));
    if (name !== "lookup") load();
  }

  function signedIn(isIn) {
    $("login").hidden = isIn; $("app").hidden = !isIn; $("signOut").hidden = !isIn;
    clearInterval(timer);
    if (isIn) { show(tab); timer = setInterval(() => { if (tab === "queue" || tab === "recent" || tab === "places") load(); }, 30000); }
  }

  function signOut(message) {
    key = ""; sessionStorage.removeItem(KEY);
    signedIn(false);
    $("loginError").hidden = !message; $("loginError").textContent = message || "";
  }

  $("loginForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    key = $("key").value.trim();
    $("loginError").hidden = true;
    try {
      const res = await fetch("/v1/moderation/queue", { headers: { "X-Moderator-Key": key } });
      if (res.status === 401) { signOut("That key was not accepted. Check MODERATOR_KEY_HASH in backend\\.env and restart the API."); return; }
      if (!res.ok) throw new Error("HTTP " + res.status);
      sessionStorage.setItem(KEY, key); $("key").value = "";
      signedIn(true);
    } catch (err) { signOut("Could not reach the server (" + err.message + ")."); }
  });
  $("signOut").addEventListener("click", () => signOut());
  $("refresh").addEventListener("click", () => (tab === "lookup" ? lookup($("lookupId").value.trim()) : load()));
  $("lookupForm").addEventListener("submit", (e) => { e.preventDefault(); lookup($("lookupId").value.trim()); });
  document.querySelectorAll("[role=tab]").forEach((b) => b.addEventListener("click", () => show(b.dataset.tab)));

  signedIn(Boolean(key));
})();
