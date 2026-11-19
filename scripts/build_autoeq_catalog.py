#!/usr/bin/env python3
"""Builds the bundled headphone correction catalogue from an AutoEq checkout.

The app ships every *recommended* AutoEq result (one per headphone model, the
most accurate measurement source AutoEq lists in results/README.md) as compact
parametric filters, so a headphone can be corrected without any network access.

Usage:
    git clone --depth 1 --filter=blob:none --sparse https://github.com/jaakkopasanen/AutoEq
    (check out results/README.md, LICENSE and the ParametricEQ.txt files it links)
    python3 scripts/build_autoeq_catalog.py path/to/AutoEq

Output: core/src/main/resources/autoeq/headphones.tsv.gz and autoeq/LICENSE.txt.

Format (UTF-8, gzip, one headphone per line, tab separated):
    name  form  source  filters
form is O (over-ear), I (in-ear) or E (earbud). filters is a comma separated list
of "<type><fc> <gain> <q>" with type L (low shelf), P (peak) or H (high shelf).
Lines starting with '#' are comments. The Preamp line is dropped on purpose,
the app derives its own headroom (see ParametricEqTextParser).
"""

import gzip
import os
import re
import subprocess
import sys
import urllib.parse

FILTER = re.compile(
    r"^Filter\s*\d*:\s*ON\s+(PK|LSC|HSC)\s+Fc\s+([0-9.]+)\s*Hz\s+Gain\s+([-+0-9.]+)\s*dB\s+Q\s+([0-9.]+)",
)
TYPES = {"PK": "P", "LSC": "L", "HSC": "H"}
LINK = re.compile(r"^- \[(.+)\]\(\./(.+)\)\s*$")


def number(text: str) -> str:
    value = float(text)
    out = f"{value:.2f}".rstrip("0").rstrip(".")
    if out.startswith("0."):
        out = out[1:]
    elif out.startswith("-0."):
        out = "-" + out[2:]
    return out or "0"


def form_of(rig_folder: str) -> str:
    if rig_folder.endswith("earbud"):
        return "E"
    if rig_folder.endswith("in-ear"):
        return "I"
    return "O"


def main(autoeq_dir: str) -> None:
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(repo_root, "core", "src", "main", "resources", "autoeq")
    os.makedirs(out_dir, exist_ok=True)

    commit = (
        subprocess.run(["git", "-C", autoeq_dir, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
        or "unknown"
    )

    rows = []
    skipped = []
    with open(os.path.join(autoeq_dir, "results", "README.md"), encoding="utf-8") as index:
        for line in index:
            match = LINK.match(line)
            if not match:
                continue
            # Some AutoEq names carry a zero-width space.
            name = match.group(1).replace("\u200b", "").strip()
            rel = urllib.parse.unquote(match.group(2))
            parts = rel.split("/")
            source, rig = parts[0], parts[1]
            path = os.path.join(autoeq_dir, "results", rel, parts[-1] + " ParametricEQ.txt")
            if not os.path.exists(path):
                skipped.append(name)
                continue
            filters = []
            with open(path, encoding="utf-8") as eq:
                for eq_line in eq:
                    f = FILTER.match(eq_line.strip())
                    if f:
                        kind, fc, gain, q = f.groups()
                        filters.append(f"{TYPES[kind]}{round(float(fc))} {number(gain)} {number(q)}")
            if not filters or "\t" in name:
                skipped.append(name)
                continue
            rows.append(f"{name}\t{form_of(rig)}\t{source}\t{','.join(filters)}")

    header = [
        f"# AutoEq recommended results, https://github.com/jaakkopasanen/AutoEq commit {commit}",
        "# MIT License, Copyright (c) 2018-2022 Jaakko Pasanen. See LICENSE.txt next to this file.",
        "# name\tform\tsource\tfilters",
    ]
    data = ("\n".join(header + rows) + "\n").encode("utf-8")
    with open(os.path.join(out_dir, "headphones.tsv.gz"), "wb") as raw:
        # mtime=0 keeps the file byte-identical between runs on the same input.
        with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0) as out:
            out.write(data)
    with open(os.path.join(autoeq_dir, "LICENSE"), encoding="utf-8") as src:
        license_text = src.read()
    with open(os.path.join(out_dir, "LICENSE.txt"), "w", encoding="utf-8") as dst:
        dst.write("Headphone correction data from AutoEq (https://github.com/jaakkopasanen/AutoEq)\n\n")
        dst.write(license_text)

    print(f"{len(rows)} headphones, {len(skipped)} skipped: {skipped}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
