#!/usr/bin/env python3
"""Builds app/src/main/assets/exercises.json from hasaneyldrm/exercises-dataset.

The dataset's data (names, muscles, equipment, instructions) is MIT licensed. Its media
(thumbnails and GIFs, (c) Gym visual - https://gymvisual.com/) is NOT copied: the app loads
it at runtime from the dataset's repository by the relative paths kept here.

Usage:
    python3 tools/build_exercises.py                 # download data/exercises.json
    python3 tools/build_exercises.py --json FILE     # use a local copy
Output: JSON array of objects; "steps" maps each language code to its instruction steps.
"""
import argparse
import json
import os
import sys
import urllib.request

URL = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/HEAD/data/exercises.json"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "exercises.json")

SMALL = {"a", "an", "and", "at", "for", "in", "of", "on", "or", "the", "to", "with", "v.", "vs"}
UPPER = {"ez", "trx", "bosu", "db", "kb", "jm"}


def clean(name):
    # A few source names carry a mis-decoded degree sign ("45в°").
    return name.replace("в°", "°").replace("  ", " ").strip()


def title(name):
    words = clean(name).split(" ")
    out = []
    for i, w in enumerate(words):
        lw = w.lower()
        if lw in UPPER:
            out.append(w.upper())
        elif i > 0 and lw in SMALL:
            out.append(lw)
        else:
            # Capitalise after "(" and "-" too: "(with arm blaster)" -> "(With Arm Blaster)"
            parts = []
            for seg in w.split("-"):
                if seg.startswith("("):
                    parts.append("(" + seg[1:2].upper() + seg[2:])
                else:
                    parts.append(seg[:1].upper() + seg[1:])
            out.append("-".join(parts))
    return " ".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json")
    ap.add_argument("--out", default=OUT)
    a = ap.parse_args()
    if a.json:
        data = json.load(open(a.json, encoding="utf-8"))
    else:
        req = urllib.request.Request(URL, headers={"User-Agent": "Liftlog build script"})
        data = json.loads(urllib.request.urlopen(req, timeout=300).read())
    out = []
    for r in data:
        steps = {}
        for lang, lst in (r.get("instruction_steps") or {}).items():
            cleaned = [clean(x) for x in (lst or []) if x and x.strip()]
            if cleaned:
                steps[lang] = cleaned
        for lang, text in (r.get("instructions") or {}).items():
            if lang not in steps and text and text.strip():
                steps[lang] = [clean(text)]
        out.append({
            "id": "edb_" + r["id"],
            "name": title(r["name"]),
            "bodyPart": r.get("body_part") or r.get("category") or "",
            "equipment": r.get("equipment") or "",
            "target": r.get("target") or "",
            "secondary": [m for m in (r.get("secondary_muscles") or []) if m],
            "steps": steps,
            "image": r.get("image") or "",
            "gif": r.get("gif_url") or "",
        })
    if len(out) < 1000:
        sys.exit(f"only {len(out)} exercises; expected ~1300")
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {len(out)} exercises to {a.out} ({os.path.getsize(a.out) // 1024} KB)")


if __name__ == "__main__":
    main()
