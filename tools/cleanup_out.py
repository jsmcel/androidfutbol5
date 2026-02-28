#!/usr/bin/env python
"""Clean transient QA artifacts under tools/pcf55-updater/out."""

from __future__ import annotations

import argparse
from pathlib import Path


DEFAULT_OUT = Path(__file__).resolve().parent / "out"

# Keep only structured artifacts by default.
KEEP_SUFFIXES = {".json", ".csv", ".md"}
DELETE_SUFFIXES = {
    ".png",
    ".jpg",
    ".jpeg",
    ".bmp",
    ".xml",
    ".apk",
    ".log",
    ".txt",
    ".gif",
    ".webp",
}


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    p.add_argument("--apply", action="store_true", help="Delete files (otherwise dry-run).")
    p.add_argument("--include-subdirs", action="store_true", help="Also remove files in nested folders.")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    out_dir = args.out_dir
    if not out_dir.exists():
        print(f"Missing: {out_dir}")
        return

    it = out_dir.rglob("*") if args.include_subdirs else out_dir.glob("*")
    removed = 0
    kept = 0
    bytes_removed = 0

    for p in it:
        if not p.is_file():
            continue
        suffix = p.suffix.lower()
        if suffix in KEEP_SUFFIXES:
            kept += 1
            continue
        if suffix in DELETE_SUFFIXES:
            if args.apply:
                size = p.stat().st_size
                p.unlink(missing_ok=True)
                removed += 1
                bytes_removed += size
            else:
                removed += 1
                bytes_removed += p.stat().st_size
            continue
        kept += 1

    mode = "apply" if args.apply else "dry-run"
    print(f"Mode: {mode}")
    print(f"Out dir: {out_dir}")
    print(f"Candidates removed: {removed}")
    print(f"Bytes freed: {bytes_removed}")
    print(f"Kept files: {kept}")


if __name__ == "__main__":
    main()
