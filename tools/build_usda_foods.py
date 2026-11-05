#!/usr/bin/env python3
"""Builds app/src/main/assets/foods.json from USDA FoodData Central "SR Legacy" (public domain).

Usage:
    python3 tools/build_usda_foods.py                 # download from fdc.nal.usda.gov
    python3 tools/build_usda_foods.py --zip FILE.zip  # use a local copy

Output: a JSON array of [fdc_id, name, kcal, protein, carbs, fat, portions] per 100 g,
where portions is "label=grams|label=grams" (see FoodEntity.portions in the app).
"""
import argparse
import csv
import io
import json
import os
import sys
import urllib.request
import zipfile

URL = "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "foods.json")

ENERGY_KCAL, PROTEIN, FAT, CARBS = "1008", "1003", "1004", "1005"


def read_csv(z, name):
    path = next((n for n in z.namelist() if n.endswith("/" + name) or n == name), None)
    if path is None:
        sys.exit(f"{name} not found in archive")
    with z.open(path) as f:
        yield from csv.DictReader(io.TextIOWrapper(f, encoding="utf-8"))


def num(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def fmt(x):
    return f"{x:g}"


def portion_label(row, units):
    desc = (row.get("portion_description") or "").strip()
    modifier = (row.get("modifier") or "").strip()
    amount = num(row.get("amount")) or 1.0
    unit = units.get(row.get("measure_unit_id") or "", "")
    if desc and desc.lower() != "quantity not specified":
        label = desc
    elif unit and unit != "undetermined":
        label = f"{fmt(amount)} {unit}" + (f", {modifier}" if modifier else "")
    elif modifier:
        label = f"{fmt(amount)} {modifier}"
    else:
        return None
    return label.replace("|", "/").replace("=", "-")[:60]


def build(z):
    names = {r["fdc_id"]: r["description"].strip() for r in read_csv(z, "food.csv")}
    nutrients = {}
    for r in read_csv(z, "food_nutrient.csv"):
        nid = r["nutrient_id"]
        if nid in (ENERGY_KCAL, PROTEIN, FAT, CARBS):
            nutrients.setdefault(r["fdc_id"], {})[nid] = num(r["amount"])
    units = {}
    try:
        units = {r["id"]: r["name"].strip() for r in read_csv(z, "measure_unit.csv")}
    except SystemExit:
        pass
    portions = {}
    for r in read_csv(z, "food_portion.csv"):
        grams = num(r.get("gram_weight"))
        label = portion_label(r, units)
        if grams and grams > 0 and label:
            seq = int(num(r.get("seq_num")) or 99)
            portions.setdefault(r["fdc_id"], []).append((seq, label, grams))

    out = []
    for fdc_id, name in names.items():
        n = nutrients.get(fdc_id, {})
        kcal = n.get(ENERGY_KCAL)
        if kcal is None or not name:
            continue
        ps = sorted(portions.get(fdc_id, []))
        seen, enc = set(), []
        for _, label, grams in ps:
            if label not in seen:
                seen.add(label)
                enc.append(f"{label}={round(grams, 1):g}")
        out.append([
            fdc_id, name, round(kcal, 1),
            round(n.get(PROTEIN) or 0.0, 2), round(n.get(CARBS) or 0.0, 2), round(n.get(FAT) or 0.0, 2),
            "|".join(enc[:6]),
        ])
    out.sort(key=lambda r: r[1].lower())
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--zip")
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--min-foods", type=int, default=5000)
    a = ap.parse_args()
    if a.zip:
        data = open(a.zip, "rb").read()
    else:
        req = urllib.request.Request(URL, headers={"User-Agent": "Liftlog build script"})
        data = urllib.request.urlopen(req, timeout=300).read()
    foods = build(zipfile.ZipFile(io.BytesIO(data)))
    if len(foods) < a.min_foods:
        sys.exit(f"only {len(foods)} foods parsed; expected at least {a.min_foods}")
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(foods, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {len(foods)} foods to {a.out} ({os.path.getsize(a.out) // 1024} KB)")


if __name__ == "__main__":
    main()
