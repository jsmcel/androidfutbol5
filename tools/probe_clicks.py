#!/usr/bin/env python
"""Probe DOSBox screen coordinates and log visual impact per click."""

from __future__ import annotations

import argparse
import csv
import subprocess
import time
from pathlib import Path
from typing import Iterable, List, Tuple

import cv2  # type: ignore
import numpy as np


def adb(serial: str | None, *args: str) -> None:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    subprocess.run(cmd, check=True)


def screenshot(serial: str | None, out_path: Path) -> None:
    remote = "/sdcard/pcf_probe_screen.png"
    adb(serial, "shell", "screencap", "-p", remote)
    adb(serial, "pull", remote, str(out_path))


def click(serial: str | None, x: int, y: int, action: str, duration_ms: int) -> None:
    if action == "tap":
        adb(serial, "shell", "input", "tap", str(x), str(y))
    elif action == "double_tap":
        adb(serial, "shell", "input", "tap", str(x), str(y))
        time.sleep(0.12)
        adb(serial, "shell", "input", "tap", str(x), str(y))
    elif action == "long_press":
        adb(serial, "shell", "input", "swipe", str(x), str(y), str(x), str(y), str(duration_ms))
    else:
        raise ValueError(f"Unsupported action: {action}")


def parse_range(spec: str) -> List[int]:
    # Example: "820:980:20"
    a, b, c = spec.split(":")
    start = int(a)
    stop = int(b)
    step = int(c)
    if step <= 0:
        raise ValueError("step must be > 0")
    vals = list(range(start, stop + 1, step))
    if not vals:
        raise ValueError(f"empty range: {spec}")
    return vals


def mad_roi(base: np.ndarray, other: np.ndarray, roi: Tuple[int, int, int, int]) -> float:
    x1, y1, x2, y2 = roi
    b = base[y1:y2, x1:x2]
    o = other[y1:y2, x1:x2]
    d = np.abs(b.astype(np.int16) - o.astype(np.int16))
    return float(d.mean())


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", default=None, help="ADB device serial")
    p.add_argument("--base-shot", type=Path, required=True, help="Screenshot with baseline screen state")
    p.add_argument("--out-dir", type=Path, default=Path(__file__).resolve().parent / "out" / "probe_clicks")
    p.add_argument("--x-range", default="780:980:20", help="x range start:stop:step")
    p.add_argument("--y-range", default="280:420:20", help="y range start:stop:step")
    p.add_argument("--roi", default="120,180,1010,520", help="ROI x1,y1,x2,y2 for diff scoring")
    p.add_argument("--action", choices=["tap", "double_tap", "long_press"], default="long_press")
    p.add_argument("--duration-ms", type=int, default=700)
    p.add_argument("--pause-ms", type=int, default=700)
    p.add_argument("--csv", type=Path, default=None)
    p.add_argument("--stop-on-threshold", type=float, default=18.0, help="Stop when ROI MAD >= threshold")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    csv_path = args.csv or (args.out_dir / "probe_results.csv")

    base = cv2.imread(str(args.base_shot), cv2.IMREAD_GRAYSCALE)
    if base is None:
        raise SystemExit(f"Cannot read baseline: {args.base_shot}")

    xs = parse_range(args.x_range)
    ys = parse_range(args.y_range)
    roi = tuple(int(v) for v in args.roi.split(","))
    if len(roi) != 4:
        raise SystemExit("ROI must be x1,y1,x2,y2")

    rows: List[dict[str, object]] = []
    best: dict[str, object] | None = None

    for y in ys:
        for x in xs:
            click(args.serial, x, y, action=args.action, duration_ms=args.duration_ms)
            time.sleep(max(0, args.pause_ms) / 1000.0)
            shot = args.out_dir / f"probe_{x}_{y}.png"
            screenshot(args.serial, shot)
            cur = cv2.imread(str(shot), cv2.IMREAD_GRAYSCALE)
            if cur is None:
                continue
            mad = mad_roi(base, cur, roi)  # Higher = bigger state change in ROI.
            row = {"x": x, "y": y, "action": args.action, "mad_roi": round(mad, 4), "shot": str(shot)}
            rows.append(row)
            if best is None or float(row["mad_roi"]) > float(best["mad_roi"]):
                best = row
            print(f"x={x:4d} y={y:4d} mad_roi={mad:8.3f}")
            if mad >= args.stop_on_threshold:
                print(f"STOP threshold reached at ({x},{y}) mad={mad:.3f}")
                break
        else:
            continue
        break

    with csv_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["x", "y", "action", "mad_roi", "shot"])
        w.writeheader()
        w.writerows(rows)

    print(f"Rows: {len(rows)}")
    if best:
        print(f"Best: x={best['x']} y={best['y']} mad_roi={best['mad_roi']}")
    print(f"CSV: {csv_path}")


if __name__ == "__main__":
    main()
