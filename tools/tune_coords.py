#!/usr/bin/env python
"""Single-point coordinate tuner: click one mapped point and capture before/after."""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from pathlib import Path


def adb(serial: str | None, *args: str) -> None:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    subprocess.run(cmd, check=True)


def screencap(serial: str | None, out_path: Path) -> None:
    remote = "/sdcard/pcf_tune_coords.png"
    adb(serial, "shell", "screencap", "-p", remote)
    adb(serial, "pull", remote, str(out_path))


def click(serial: str | None, x: int, y: int, action: str, duration_ms: int) -> None:
    if action == "tap":
        adb(serial, "shell", "input", "tap", str(x), str(y))
        return
    if action == "double_tap":
        adb(serial, "shell", "input", "tap", str(x), str(y))
        time.sleep(0.12)
        adb(serial, "shell", "input", "tap", str(x), str(y))
        return
    if action == "long_press":
        adb(serial, "shell", "input", "swipe", str(x), str(y), str(x), str(y), str(duration_ms))
        return
    raise ValueError(f"Unsupported action: {action}")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", default=None)
    p.add_argument("--map-file", type=Path, required=True)
    p.add_argument("--screen", required=True)
    p.add_argument("--point", required=True)
    p.add_argument("--out-dir", type=Path, default=Path(__file__).resolve().parent / "out" / "coord_tuning")
    p.add_argument("--duration-ms", type=int, default=700)
    p.add_argument("--pause-ms", type=int, default=900)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    data = json.loads(args.map_file.read_text(encoding="utf-8"))
    point = data["screens"][args.screen][args.point]
    x = int(point["x"])
    y = int(point["y"])
    action = str(point.get("action", "long_press"))

    args.out_dir.mkdir(parents=True, exist_ok=True)
    stem = f"{args.screen}__{args.point}__{x}_{y}"
    before = args.out_dir / f"{stem}__before.png"
    after = args.out_dir / f"{stem}__after.png"

    screencap(args.serial, before)
    click(args.serial, x, y, action=action, duration_ms=args.duration_ms)
    time.sleep(max(0, args.pause_ms) / 1000.0)
    screencap(args.serial, after)

    print(f"Point: {args.screen}.{args.point}")
    print(f"Action: {action} @ ({x},{y})")
    print(f"Before: {before}")
    print(f"After : {after}")


if __name__ == "__main__":
    main()
