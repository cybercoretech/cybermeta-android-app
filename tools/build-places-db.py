#!/usr/bin/env python3
"""Builds the compact offline reverse-geocoding database for Cybermeta.

Downloads GeoNames cities15000 (cities with population > 15,000) and compiles
them into a flat, binary asset file: app/src/main/assets/places.bin.
"""

import os
import struct
import sys
import urllib.request
import zipfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
ASSETS_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "assets")
OUTPUT_PATH = os.path.join(ASSETS_DIR, "places.bin")
GEONAMES_URL = "https://download.geonames.org/export/dump/cities15000.zip"
TMP_ZIP = "/tmp/cities15000.zip"


def ensure_data():
    if not os.path.exists(TMP_ZIP):
        print(f"Downloading {GEONAMES_URL}...")
        urllib.request.urlretrieve(GEONAMES_URL, TMP_ZIP)
    print(f"Reading from {TMP_ZIP}...")
    with zipfile.ZipFile(TMP_ZIP) as z:
        with z.open("cities15000.txt") as f:
            return f.read().decode("utf-8").splitlines()


def build_binary(lines):
    records = bytearray()
    strings = bytearray()
    str_map = {}

    def intern_string(s: str) -> int:
        if s in str_map:
            return str_map[s]
        off = len(strings)
        strings.extend(s.encode("utf-8") + b"\0")
        str_map[s] = off
        return off

    count = 0
    for line in lines:
        parts = line.split("\t")
        if len(parts) >= 15:
            name = parts[1]
            try:
                lat = float(parts[4])
                lon = float(parts[5])
            except ValueError:
                continue
            country = parts[8].strip()
            admin = parts[10].strip()
            # Stored as "Name\tAdmin\tCountry"
            label = f"{name}\t{admin}\t{country}"
            str_off = intern_string(label)
            records.extend(struct.pack("<ffi", lat, lon, str_off))
            count += 1

    header = struct.pack("<4sHI", b"CYPL", 1, count)
    return header + records + strings, count


def main():
    os.makedirs(ASSETS_DIR, exist_ok=True)
    lines = ensure_data()
    data, count = build_binary(lines)
    with open(OUTPUT_PATH, "wb") as f:
        f.write(data)
    size_kb = len(data) / 1024.0
    print(f"Wrote {count} cities to {OUTPUT_PATH} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
