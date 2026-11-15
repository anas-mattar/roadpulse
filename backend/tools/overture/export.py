"""
Downloads Overture Maps places (open data: Meta, Microsoft, Foursquare, BrightQuery) for an area
and writes them as one JSON object per line, for `import-overture` in the RoadPulse API.

  python export.py /out/overture-places.jsonl [--release 2026-10-21.0] [--bbox minLon,minLat,maxLon,maxLat]

Only the needed columns of the area are read from Overture's public cloud storage (no account).
Default area: Malaysia, Singapore and Brunei (same as the OpenStreetMap map file).
"""
import argparse
import json
import re
import sys
import urllib.request

import duckdb

BUCKET = "https://overturemaps-us-west-2.s3.amazonaws.com"
DEFAULT_BBOX = "99.5,0.8,119.5,7.5"


def latest_release() -> str:
    with urllib.request.urlopen(f"{BUCKET}/?list-type=2&prefix=release/&delimiter=/", timeout=60) as r:
        xml = r.read().decode()
    releases = re.findall(r"<Prefix>release/([^/<]+)/</Prefix>", xml)
    if not releases:
        sys.exit("Could not find an Overture release.")
    return sorted(releases)[-1]


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("out")
    p.add_argument("--release")
    p.add_argument("--bbox", default=DEFAULT_BBOX)
    # Low-confidence places are kept (some real shops have 0.3) but ranked lower by the search.
    p.add_argument("--min-confidence", type=float, default=0.25)
    a = p.parse_args()
    release = a.release or latest_release()
    w, s, e, n = (float(x) for x in a.bbox.split(","))
    print(f"Overture release {release}, area {a.bbox}", flush=True)

    con = duckdb.connect()
    con.execute("INSTALL httpfs; LOAD httpfs;")
    # Public bucket: no account or keys (ignores any AWS keys set on the computer).
    con.execute("CREATE OR REPLACE SECRET overture (TYPE s3, PROVIDER config, KEY_ID '', SECRET '', REGION 'us-west-2')")
    src = f"read_parquet('s3://overturemaps-us-west-2/release/{release}/theme=places/type=place/*', hive_partitioning=1)"
    rows = con.execute(f"""
        SELECT id, names.primary, basic_category,
               addresses[1].freeform, addresses[1].locality,
               (bbox.ymin + bbox.ymax) / 2, (bbox.xmin + bbox.xmax) / 2, confidence
        FROM {src}
        WHERE bbox.xmin BETWEEN ? AND ? AND bbox.ymin BETWEEN ? AND ?
          AND names.primary IS NOT NULL
          AND coalesce(confidence, 1) >= ?
          AND coalesce(operating_status, 'open') <> 'permanently_closed'
    """, [w, e, s, n, a.min_confidence]).fetchall()

    with open(a.out, "w", encoding="utf-8") as f:
        for pid, name, category, street, city, lat, lon, conf in rows:
            address = ", ".join(x for x in (street, city) if x)
            f.write(json.dumps({
                "id": pid, "name": name.strip()[:200], "category": category,
                "address": address[:200] or None, "lat": round(lat, 6), "lon": round(lon, 6),
                "confidence": round(conf, 3) if conf is not None else None,
            }, ensure_ascii=False) + "\n")
    print(f"Wrote {len(rows)} places to {a.out}", flush=True)


if __name__ == "__main__":
    main()
