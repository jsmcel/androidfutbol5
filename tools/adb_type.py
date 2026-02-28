#!/usr/bin/env python
"""Type deterministic keyevents into Android (useful for DOSBox shells)."""

from __future__ import annotations

import argparse
import subprocess
import time
from typing import Dict


CHAR_TO_KEYCODE: Dict[str, int] = {
    "a": 29,
    "b": 30,
    "c": 31,
    "d": 32,
    "e": 33,
    "f": 34,
    "g": 35,
    "h": 36,
    "i": 37,
    "j": 38,
    "k": 39,
    "l": 40,
    "m": 41,
    "n": 42,
    "o": 43,
    "p": 44,
    "q": 45,
    "r": 46,
    "s": 47,
    "t": 48,
    "u": 49,
    "v": 50,
    "w": 51,
    "x": 52,
    "y": 53,
    "z": 54,
    "0": 7,
    "1": 8,
    "2": 9,
    "3": 10,
    "4": 11,
    "5": 12,
    "6": 13,
    "7": 14,
    "8": 15,
    "9": 16,
    " ": 62,
    ".": 56,
    ",": 55,
    "/": 76,
    "\\": 73,
    "-": 69,
    "_": 69,  # Shift variants intentionally mapped to base key.
}


def adb(serial: str | None, *args: str) -> None:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    subprocess.run(cmd, check=True)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--serial", default=None, help="ADB device serial")
    p.add_argument("--text", required=True, help="Text to type (ASCII subset)")
    p.add_argument("--enter", action="store_true", help="Send ENTER keyevent after text")
    p.add_argument("--delay-ms", type=int, default=45, help="Delay between keyevents")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    delay = max(0, int(args.delay_ms)) / 1000.0
    text = args.text.lower()

    for ch in text:
        if ch not in CHAR_TO_KEYCODE:
            raise SystemExit(f"Unsupported char: {ch!r}")
        adb(args.serial, "shell", "input", "keyevent", str(CHAR_TO_KEYCODE[ch]))
        if delay:
            time.sleep(delay)

    if args.enter:
        adb(args.serial, "shell", "input", "keyevent", "66")


if __name__ == "__main__":
    main()
