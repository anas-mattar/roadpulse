#!/usr/bin/env python3
"""Runs a fixed list of trips through the routing server and measures each route.

Why: to change how routes are chosen (penalties for small streets, turns, gated roads...) without
guessing. Run it before and after a change and compare; fill the waze_* columns in trips.csv with
what Waze suggests for the same trips to compare against it too.

  python route_tests.py                                  # every profile, public routing server
  python route_tests.py --server http://localhost:8002   # your own routing server
  python route_tests.py --profiles current calmer --out results.csv

For each trip and profile: minutes and km (the server's estimate without traffic), km on small
streets (residential and service roads), number of turns and U-turns, toll or not, and the road
that carries most of the trip. Standard library only.
"""
import argparse, csv, json, sys, time, urllib.request
from collections import Counter

TURNS = {9, 10, 11, 12, 13, 14, 15, 16}      # slight right ... slight left (Valhalla maneuver types)
U_TURNS = {12, 13}
SMALL = {"residential", "service_other"}


def post(server, path, body, pause):
    time.sleep(pause)  # be gentle with a shared server
    req = urllib.request.Request(server.rstrip("/") + "/" + path, json.dumps(body).encode(),
                                 {"Content-Type": "application/json", "User-Agent": "RoadPulse-route-tests/0.1"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)


def measure(server, trip, options, pause):
    route = post(server, "route", {
        "locations": [{"lat": trip["from_lat"], "lon": trip["from_lon"]}, {"lat": trip["to_lat"], "lon": trip["to_lon"]}],
        "costing": "auto", "costing_options": {"auto": options}, "units": "kilometers"}, pause)["trip"]
    leg = route["legs"][0]
    types = [m["type"] for m in leg["maneuvers"]]
    # Which roads the route uses. "edge_walk" follows the route exactly; a few shapes need "map_snap".
    trace = {}
    for match in ("edge_walk", "map_snap"):
        try:
            trace = post(server, "trace_attributes", {"encoded_polyline": leg["shape"], "shape_match": match, "costing": "auto"}, pause)
            break
        except Exception:
            continue
    by_class, by_name, toll = Counter(), Counter(), False
    for e in trace.get("edges", []):
        by_class[e.get("road_class", "?")] += e.get("length", 0)
        for n in e.get("names", [])[:1]:
            by_name[n] += e.get("length", 0)
        toll = toll or bool(e.get("toll"))
    return {
        "minutes": round(route["summary"]["time"] / 60, 1), "km": round(route["summary"]["length"], 1),
        "small_km": round(sum(by_class[c] for c in SMALL), 2),
        "turns": sum(t in TURNS for t in types), "u_turns": sum(t in U_TURNS for t in types),
        "toll": "yes" if toll else "no", "via": by_name.most_common(1)[0][0] if by_name else "",
    }


def main():
    here = __file__.rsplit("/", 1)[0] if "/" in __file__ else "."
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--server", default="https://valhalla1.openstreetmap.de")
    p.add_argument("--trips", default=here + "/trips.csv")
    p.add_argument("--profile-file", default=here + "/profiles.json")
    p.add_argument("--profiles", nargs="*", help="profile names (default: all)")
    p.add_argument("--out", help="write every measurement to this CSV file")
    p.add_argument("--pause", type=float, default=0.7, help="seconds between requests")
    a = p.parse_args()

    profiles = json.load(open(a.profile_file, encoding="utf-8"))
    names = a.profiles or list(profiles)
    trips = []
    for row in csv.DictReader(open(a.trips, encoding="utf-8")):
        for k in ("from_lat", "from_lon", "to_lat", "to_lon"):
            row[k] = float(row[k])
        trips.append(row)

    rows = []
    for trip in trips:
        for name in names:
            try:
                m = measure(a.server, trip, profiles[name], a.pause)
            except Exception as e:  # one failing trip must not stop the run
                print(f"  {trip['name']} [{name}]: failed ({e})", file=sys.stderr)
                continue
            rows.append({"trip": trip["name"], "profile": name, **m,
                         "waze_minutes": trip.get("waze_minutes", ""), "waze_km": trip.get("waze_km", ""), "waze_via": trip.get("waze_via", "")})
            print(f"{trip['name']:38} {name:11} {m['minutes']:6} min {m['km']:6} km  small streets {m['small_km']:5} km  "
                  f"turns {m['turns']:2}  u-turns {m['u_turns']}  toll {m['toll']:3}  via {m['via']}")

    print("\nTotals per profile (all trips):")
    for name in names:
        mine = [r for r in rows if r["profile"] == name]
        if mine:
            print(f"  {name:11} {sum(r['minutes'] for r in mine):7.1f} min  {sum(r['km'] for r in mine):7.1f} km  "
                  f"small streets {sum(r['small_km'] for r in mine):6.2f} km  turns {sum(r['turns'] for r in mine):3}  "
                  f"u-turns {sum(r['u_turns'] for r in mine)}  trips {len(mine)}")
    if a.out and rows:
        with open(a.out, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(rows[0]))
            w.writeheader()
            w.writerows(rows)
        print(f"\nWrote {a.out}")


if __name__ == "__main__":
    main()
