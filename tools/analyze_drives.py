#!/usr/bin/env python3
"""
RoadPulse field-test report: reads drive logs (navlogs/*.csv from debug builds) and writes a
Markdown report with per-drive metrics and the Gate 1 checks from docs/FIELD_TEST_PLAN.md.

Usage:  python tools/analyze_drives.py [navlogs_folder] [--include-replay]
Output: <folder>/report.md (also printed). Standard library only.
"""
import csv
import glob
import math
import os
import statistics
import sys
from collections import Counter

# Proposed Gate 1 targets. The plan says to agree final thresholds from pilot baselines:
# treat these as starting points and adjust after the first drives.
TARGETS = {
    "false_reroutes_per_100km": 1.0,   # reroutes the driver flagged as false
    "abrupt_ends": 0,                  # logs without a summary row (crash or app killed)
    "arrival_rate": 0.95,              # completed drives that detected arrival
    "battery_pct_per_hour": 15.0,      # screen on, not charging
}


def hav_m(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def fnum(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def parse_detail(detail):
    out = {}
    for part in (detail or "").split(";"):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def mmss(seconds):
    seconds = max(0, int(seconds))
    return "%d:%02d" % (seconds // 60, seconds % 60)


def analyze(path):
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    d = {"file": os.path.basename(path), "rows": len(rows)}
    if not rows:
        return d
    t0 = int(rows[0]["time_ms"])
    start = next((r for r in rows if r["event"] == "drive_start"), None)
    info = parse_detail(start["detail"]) if start else {}
    d["destination"] = info.get("destination", "?")
    d["source"] = info.get("source", "?")
    d["replay"] = d["source"] not in ("device GPS", "?")
    d["resumed"] = info.get("resumed") == "true"
    d["route_km"] = fnum(info.get("route_km"))
    d["complete"] = any(r["event"] == "summary" for r in rows)
    d["arrived"] = any(r["event"] == "arrived" for r in rows)
    # Reached = last route-matched position within 300 m of the end of the route.
    last_remaining = next((fnum(r["remaining_m"]) for r in reversed(rows)
                           if r["event"] == "fix" and r["matched"] == "true" and fnum(r["remaining_m"]) is not None), None)
    d["reached"] = d["arrived"] or (last_remaining is not None and last_remaining < 300)
    d["duration_s"] = (int(rows[-1]["time_ms"]) - t0) / 1000.0

    fixes = [r for r in rows if r["event"] == "fix"]
    d["fixes"] = len(fixes)
    matched = [r for r in fixes if r["matched"] == "true"]
    d["matched_pct"] = 100.0 * len(matched) / len(fixes) if fixes else 0.0
    d["poor_pct"] = 100.0 * sum(1 for r in fixes if r["detail"] == "poor_signal") / len(fixes) if fixes else 0.0
    acc = [fnum(r["accuracy_m"]) for r in fixes if fnum(r["accuracy_m"]) is not None]
    d["median_accuracy_m"] = statistics.median(acc) if acc else None
    offs = sorted(fnum(r["offset_m"]) for r in matched if fnum(r["offset_m"]) is not None)
    d["p95_offset_m"] = offs[int(0.95 * (len(offs) - 1))] if offs else None

    # Distance driven along matched positions, ignoring implausible jumps (> 70 m/s).
    km = 0.0
    prev = None
    for r in fixes:
        lat, lon = fnum(r["matched_lat"]), fnum(r["matched_lon"])
        if lat is None or lon is None:
            continue
        t = int(r["time_ms"])
        if prev:
            dist = hav_m(prev[0], prev[1], lat, lon)
            dt = max(0.001, (t - prev[2]) / 1000.0)
            if dist / dt < 70:
                km += dist / 1000.0
        prev = (lat, lon, t)
    d["driven_km"] = km

    events = [r for r in rows if r["event"] != "fix"]
    d["prompts"] = sum(1 for r in events if r["event"] == "prompt")
    prompt_times = [int(r["time_ms"]) for r in events if r["event"] == "prompt"]
    d["prompt_pairs_lt4s"] = sum(1 for a, b in zip(prompt_times, prompt_times[1:]) if b - a < 4000)
    d["reroutes"] = sum(1 for r in events if r["event"] == "reroute_start")
    d["reroutes_failed"] = sum(1 for r in events if r["event"] == "reroute_failed")

    # Reroutes that followed low-accuracy GPS (> 30 m within the previous 10 s): likely GPS-caused.
    gps_caused = 0
    for r in events:
        if r["event"] != "reroute_start":
            continue
        t = int(r["time_ms"])
        window = [fnum(x["accuracy_m"]) for x in fixes if t - 10000 <= int(x["time_ms"]) <= t]
        if any(a is not None and a > 30 for a in window):
            gps_caused += 1
    d["reroutes_after_poor_gps"] = gps_caused

    lost_s, lost_n, lost_at = 0.0, 0, None
    for r in events:
        if r["event"] == "gps_lost":
            lost_n += 1
            lost_at = int(r["time_ms"])
        elif r["event"] == "gps_restored" and lost_at is not None:
            lost_s += (int(r["time_ms"]) - lost_at) / 1000.0
            lost_at = None
    d["gps_lost"] = lost_n
    d["gps_lost_s"] = lost_s

    d["speed_limits"] = ", ".join(sorted({str(r["detail"]) for r in events if r["event"] == "speed_limits"})) or "-"

    d["flags"] = []
    for r in events:
        if r["event"] == "flag":
            info = parse_detail(r["detail"])
            category = (r["detail"] or "").split(";")[0]
            d["flags"].append((mmss((int(r["time_ms"]) - t0) / 1000.0), category, info.get("at", "")))

    bat = [(int(r["time_ms"]), parse_detail(r["detail"])) for r in events if r["event"] == "battery"]
    bat = [(t, int(i.get("level", "0")), i.get("charging") == "true") for t, i in bat if i.get("level")]
    unplugged = [b for b in bat if not b[2]]
    if len(unplugged) >= 2 and unplugged[-1][0] > unplugged[0][0]:
        hours = (unplugged[-1][0] - unplugged[0][0]) / 3_600_000.0
        d["battery_pct_per_hour"] = (unplugged[0][1] - unplugged[-1][1]) / hours if hours >= 0.25 else None
    else:
        d["battery_pct_per_hour"] = None
    return d


def fmt(v, digits=1, suffix=""):
    if v is None:
        return "-"
    if isinstance(v, float):
        return ("%." + str(digits) + "f%s") % (v, suffix)
    return "%s%s" % (v, suffix)


def report(drives, folder, include_replay):
    lines = ["# RoadPulse field-test report", ""]
    used = [d for d in drives if d.get("fixes") and (include_replay or not d.get("replay"))]
    skipped = len(drives) - len(used)
    lines.append("Logs analysed: %d%s." % (len(used), (" (%d simulated or empty logs skipped; use --include-replay)" % skipped) if skipped else ""))
    lines.append("")
    if not used:
        lines.append("No real-GPS drives found. Field tests need the debug app driven with Start (not Simulate).")
        return "\n".join(lines)

    total_km = sum(d["driven_km"] for d in used)
    complete = [d for d in used if d["complete"]]
    abrupt = [d for d in used if not d["complete"]]
    reached = [d for d in complete if d["reached"]]
    arrived = [d for d in reached if d["arrived"]]
    flags = Counter(f[1] for d in used for f in d["flags"])
    false_rr = flags.get("false_reroute", 0)
    reroutes = sum(d["reroutes"] for d in used)
    bats = [d["battery_pct_per_hour"] for d in used if d.get("battery_pct_per_hour") is not None]

    lines += ["## Summary", ""]
    lines.append("| Measure | Value |")
    lines.append("|---|---|")
    lines.append("| Drives / distance | %d drives, %.1f km |" % (len(used), total_km))
    lines.append("| Ended normally / abruptly | %d / %d |" % (len(complete), len(abrupt)))
    lines.append("| Arrival detected | %d of %d drives that reached the destination |" % (len(arrived), len(reached)))
    lines.append("| Reroutes (all) | %d (%.1f per 100 km) |" % (reroutes, 100 * reroutes / total_km if total_km else 0))
    lines.append("| Reroutes flagged as false | %d (%.1f per 100 km) |" % (false_rr, 100 * false_rr / total_km if total_km else 0))
    lines.append("| GPS lost events | %d (%.0f s total) |" % (sum(d["gps_lost"] for d in used), sum(d["gps_lost_s"] for d in used)))
    lines.append("| Issues flagged | %s |" % (", ".join("%s: %d" % kv for kv in sorted(flags.items())) or "none"))
    lines.append("| Battery use | %s |" % (fmt(statistics.mean(bats), 1, " %/hour") if bats else "not enough data (need 15+ min unplugged)"))
    lines.append("")

    lines += ["## Gate 1 checks (proposed targets)", ""]
    lines.append("| Check | Target | Result | Status |")
    lines.append("|---|---|---|---|")
    def row(name, target, value, ok):
        lines.append("| %s | %s | %s | %s |" % (name, target, value, "pass" if ok else "**check**"))
    fr = 100 * false_rr / total_km if total_km else 0
    row("False reroutes per 100 km", "≤ %.1f" % TARGETS["false_reroutes_per_100km"], "%.1f" % fr, fr <= TARGETS["false_reroutes_per_100km"])
    row("Crashes / killed sessions", "%d" % TARGETS["abrupt_ends"], str(len(abrupt)), len(abrupt) <= TARGETS["abrupt_ends"])
    if reached:
        rate = len(arrived) / len(reached)
        row("Arrival detected", "≥ %d%%" % (100 * TARGETS["arrival_rate"]), "%d%%" % (100 * rate), rate >= TARGETS["arrival_rate"])
    if bats:
        b = statistics.mean(bats)
        row("Battery use (screen on)", "≤ %.0f %%/h" % TARGETS["battery_pct_per_hour"], "%.1f %%/h" % b, b <= TARGETS["battery_pct_per_hour"])
    row("Wrong-prompt flags", "0 unexplained", str(flags.get("wrong_prompt", 0) + flags.get("prompt_timing", 0)), flags.get("wrong_prompt", 0) + flags.get("prompt_timing", 0) == 0)
    lines.append("")
    lines.append("Targets are proposals; the plan requires agreeing final thresholds from pilot baselines.")
    if abrupt:
        lines.append("")
        lines.append("\"Ended abruptly\" means the log has no summary row: the app crashed or was killed, *or* the log was")
        lines.append("copied while the drive was still running. Always press Stop (or arrive) before running pull-drive-logs.bat.")
    lines.append("")

    lines += ["## Drives", ""]
    lines.append("| Log | Destination | km | Time | Matched | Poor GPS | p95 offset | Reroutes (after poor GPS) | GPS lost | Prompts (<4 s pairs) | Speed limits | Arrived | Battery |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|")
    for d in used:
        lines.append("| %s | %s%s | %.1f | %s | %.0f%% | %.0f%% | %s | %d (%d) | %d (%.0f s) | %d (%d) | %s | %s | %s |" % (
            d["file"], d["destination"], " (resumed)" if d["resumed"] else "", d["driven_km"], mmss(d["duration_s"]),
            d["matched_pct"], d["poor_pct"], fmt(d["p95_offset_m"], 0, " m"), d["reroutes"], d["reroutes_after_poor_gps"],
            d["gps_lost"], d["gps_lost_s"], d["prompts"], d["prompt_pairs_lt4s"], d["speed_limits"],
            "yes" if d["arrived"] else (("**no**" if d["reached"] else "stopped early") if d["complete"] else "**ended abruptly**"),
            fmt(d.get("battery_pct_per_hour"), 1, " %/h"),
        ))
    lines.append("")

    all_flags = [(d["file"], f) for d in used for f in d["flags"]]
    if all_flags:
        lines += ["## Flagged issues", "", "| Log | Time into drive | Category | Location |", "|---|---|---|---|"]
        for file, (t, cat, at) in all_flags:
            link = ""
            if "," in at:
                lat, lon = at.split(",", 1)
                link = "[%s](https://www.openstreetmap.org/?mlat=%s&mlon=%s#map=18/%s/%s)" % (at, lat, lon, lat, lon)
            lines.append("| %s | %s | %s | %s |" % (file, t, cat, link or at))
        lines.append("")
    lines.append("Logs contain precise locations. Keep this report and the logs private.")
    return "\n".join(lines)


def main(argv):
    include_replay = "--include-replay" in argv
    args = [a for a in argv if not a.startswith("--")]
    folder = args[0] if args else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "navlogs")
    files = sorted(glob.glob(os.path.join(folder, "drive-*.csv")))
    drives = [analyze(f) for f in files]
    text = report(drives, folder, include_replay)
    with open(os.path.join(folder, "report.md"), "w", encoding="utf-8") as f:
        f.write(text + "\n")
    print(text)


if __name__ == "__main__":
    main(sys.argv[1:])
