#!/usr/bin/env python3
"""ADB navigation helper for Magic DOSBox + PC Futbol QA."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional

from PIL import Image


PKG = "bruenor.magicbox.free"
ACTIVITY = "bruenor.magicbox.free.uiGameStarterActivity"
DEFAULT_OUT_DIR = Path(__file__).resolve().parent / "out"
DEFAULT_BUTTON_MAP = Path(__file__).resolve().parent / "dosbox_button_map.json"
PCF_REF_WIDTH = 1080
PCF_REF_HEIGHT = 810
BRIGHTNESS_THRESHOLD = 20


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    # Format: [x1,y1][x2,y2]
    left, right = bounds.split("][")
    x1, y1 = left.strip("[]").split(",")
    x2, y2 = right.strip("[]").split(",")
    return int(x1), int(y1), int(x2), int(y2)


def center(bounds: str) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(bounds)
    return (x1 + x2) // 2, (y1 + y2) // 2


@dataclass
class Node:
    text: str
    rid: str
    cls: str
    clickable: bool
    bounds: str

    @property
    def cxy(self) -> tuple[int, int]:
        return center(self.bounds)


@dataclass
class CoordTransform:
    mode: str
    offset_x: float
    offset_y: float
    scale_x: float
    scale_y: float
    frame_width: int
    frame_height: int
    run_width: int
    run_height: int

    def apply(self, x: int, y: int) -> tuple[int, int]:
        tx = int(round(self.offset_x + (x * self.scale_x)))
        ty = int(round(self.offset_y + (y * self.scale_y)))
        return tx, ty

    def as_dict(self) -> dict[str, object]:
        return {
            "mode": self.mode,
            "offset_x": round(self.offset_x, 4),
            "offset_y": round(self.offset_y, 4),
            "scale_x": round(self.scale_x, 6),
            "scale_y": round(self.scale_y, 6),
            "frame_width": self.frame_width,
            "frame_height": self.frame_height,
            "run_width": self.run_width,
            "run_height": self.run_height,
            "reference_space": f"{PCF_REF_WIDTH}x{PCF_REF_HEIGHT}",
        }


class DosboxNavigator:
    def __init__(self, out_dir: Path, serial: Optional[str] = None, dry_run: bool = False):
        self.out_dir = out_dir
        self.serial = serial
        self.dry_run = dry_run
        self._coord_transform_cache: Optional[CoordTransform] = None
        self.out_dir.mkdir(parents=True, exist_ok=True)

    def _adb_prefix(self) -> list[str]:
        cmd = ["adb"]
        if self.serial:
            cmd += ["-s", self.serial]
        return cmd

    def adb(self, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        cmd = self._adb_prefix() + list(args)
        if self.dry_run:
            print("[dry-run]", " ".join(cmd))
            return subprocess.CompletedProcess(cmd, 0, "", "")
        return subprocess.run(cmd, check=check, capture_output=True, text=True)

    def shell(self, command: str, check: bool = True) -> subprocess.CompletedProcess[str]:
        return self.adb("shell", command, check=check)

    def tap(self, x: int, y: int, pause: float = 0.25) -> None:
        self.shell(f"input tap {x} {y}")
        time.sleep(pause)

    def double_tap(self, x: int, y: int, gap: float = 0.12, pause: float = 0.35) -> None:
        self.tap(x, y, pause=gap)
        self.tap(x, y, pause=pause)

    def long_press(self, x: int, y: int, duration_ms: int = 650, pause: float = 0.35) -> None:
        self.shell(f"input swipe {x} {y} {x} {y} {duration_ms}")
        time.sleep(pause)

    def swipe(self, x1: int, y1: int, x2: int, y2: int, duration_ms: int = 200, pause: float = 0.3) -> None:
        self.shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")
        time.sleep(pause)

    def keyevent(self, keycode: int, pause: float = 0.25) -> None:
        self.shell(f"input keyevent {keycode}")
        time.sleep(pause)

    def start_magic(self) -> None:
        self.shell(f"am start -n {PKG}/{ACTIVITY}")
        time.sleep(1.0)

    def screenshot(self, name: str) -> Path:
        if not name.lower().endswith(".png"):
            name += ".png"
        remote = f"/sdcard/{name}"
        local = self.out_dir / name
        self.shell(f"screencap -p {remote}")
        self.adb("pull", remote, str(local))
        return local

    def dump_ui(self, name: str = "ui_state.xml") -> tuple[Path, list[Node]]:
        safe = self._sanitize_name(name, ".xml")
        remote = f"/sdcard/{safe}"
        local = self.out_dir / safe
        self.shell(f"uiautomator dump {remote}")
        self.adb("pull", remote, str(local))
        root = ET.parse(local).getroot()
        nodes: list[Node] = []
        for n in root.iter("node"):
            a = n.attrib
            nodes.append(
                Node(
                    text=a.get("text", ""),
                    rid=a.get("resource-id", ""),
                    cls=a.get("class", ""),
                    clickable=a.get("clickable", "false") == "true",
                    bounds=a.get("bounds", "[0,0][0,0]"),
                )
            )
        return local, nodes

    @staticmethod
    def _sanitize_name(name: str, suffix: str) -> str:
        if not name.lower().endswith(suffix):
            name = f"{name}{suffix}"
        stem = name[: -len(suffix)]
        stem = re.sub(r"[^A-Za-z0-9_.-]+", "_", stem).strip("_")
        if not stem:
            stem = "artifact"
        return f"{stem}{suffix}"

    @staticmethod
    def _largest_true_run(mask: list[bool]) -> tuple[int, int]:
        best_start = 0
        best_end = -1
        cur_start = -1
        for i, flag in enumerate(mask):
            if flag and cur_start < 0:
                cur_start = i
            elif not flag and cur_start >= 0:
                cur_end = i - 1
                if (cur_end - cur_start) > (best_end - best_start):
                    best_start, best_end = cur_start, cur_end
                cur_start = -1
        if cur_start >= 0:
            cur_end = len(mask) - 1
            if (cur_end - cur_start) > (best_end - best_start):
                best_start, best_end = cur_start, cur_end
        return best_start, best_end

    def detect_coord_transform(self, refresh: bool = False) -> CoordTransform:
        if self._coord_transform_cache is not None and not refresh:
            return self._coord_transform_cache

        shot = self.screenshot("nav_autocalib_frame.png")
        img = Image.open(shot).convert("L")
        w, h = img.size

        top_h = max(240, min(h, int(h * 0.75)))
        col_profile = list(img.crop((0, 0, w, top_h)).resize((w, 1), resample=Image.BILINEAR).getdata())
        col_mask = [v >= BRIGHTNESS_THRESHOLD for v in col_profile]
        x1, x2 = self._largest_true_run(col_mask)
        if x2 < x1:
            x1, x2 = 0, w - 1

        run_w = x2 - x1 + 1
        if run_w < int(PCF_REF_WIDTH * 0.5):
            x1, x2 = 0, w - 1
            run_w = w

        row_profile = list(img.crop((x1, 0, x2 + 1, h)).resize((1, h), resample=Image.BILINEAR).getdata())
        row_mask = [v >= BRIGHTNESS_THRESHOLD for v in row_profile]
        y1, y2 = self._largest_true_run(row_mask)
        if y2 < y1:
            y1, y2 = 0, min(h, PCF_REF_HEIGHT) - 1

        run_h = y2 - y1 + 1
        if run_h < int(PCF_REF_HEIGHT * 0.5):
            y1 = 0
            run_h = min(h, PCF_REF_HEIGHT)
            y2 = y1 + run_h - 1

        transform = CoordTransform(
            mode="autocalib",
            offset_x=float(x1),
            offset_y=float(y1),
            scale_x=float(run_w) / float(PCF_REF_WIDTH),
            scale_y=float(run_h) / float(PCF_REF_HEIGHT),
            frame_width=w,
            frame_height=h,
            run_width=run_w,
            run_height=run_h,
        )
        self._coord_transform_cache = transform
        (self.out_dir / "nav_coord_transform.json").write_text(
            json.dumps(transform.as_dict(), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return transform

    def pcf_to_device(self, x: int, y: int, refresh: bool = False) -> tuple[int, int]:
        t = self.detect_coord_transform(refresh=refresh)
        return t.apply(x, y)

    @staticmethod
    def find_first(nodes: Iterable[Node], *, rid: Optional[str] = None, text: Optional[str] = None) -> Optional[Node]:
        for n in nodes:
            if rid is not None and n.rid != rid:
                continue
            if text is not None and n.text != text:
                continue
            return n
        return None

    @staticmethod
    def find_all(nodes: Iterable[Node], *, rid: Optional[str] = None, text: Optional[str] = None) -> list[Node]:
        out = []
        for n in nodes:
            if rid is not None and n.rid != rid:
                continue
            if text is not None and n.text != text:
                continue
            out.append(n)
        return out

    def tap_node(self, node: Node, pause: float = 0.35) -> None:
        x, y = node.cxy
        self.tap(x, y, pause=pause)

    def assert_caption(self, caption: str, nodes: list[Node], context: str) -> None:
        cap = self.find_first(nodes, rid="bruenor.magicbox.free:id/dialog_theme_caption")
        if cap is None or cap.text != caption:
            got = cap.text if cap else "<none>"
            raise RuntimeError(f"{context}: expected caption '{caption}', got '{got}'")

    def open_general_settings(self) -> None:
        # Recover from nested dialogs first; BACK does not always return to General settings.
        for attempt in range(1, 6):
            _, nodes = self.dump_ui(f"nav_settings_probe_{attempt}.xml")
            cap = self.find_first(nodes, rid="bruenor.magicbox.free:id/dialog_theme_caption")
            caption = cap.text if cap else ""
            if caption == "General settings":
                return

            if caption == "Gestures":
                btn = self.find_first(nodes, rid="bruenor.magicbox.free:id/gestures_confirm")
                if btn:
                    self.tap_node(btn, pause=0.7)
                    continue

            # Generic confirm / close fallbacks for nested dialogs.
            for rid in (
                "bruenor.magicbox.free:id/mouse_confirm",
                "bruenor.magicbox.free:id/confirm",
                "bruenor.magicbox.free:id/dialog_theme_close",
            ):
                btn = self.find_first(nodes, rid=rid)
                if btn:
                    self.tap_node(btn, pause=0.6)
                    break
            else:
                # Last resort: Android BACK.
                self.keyevent(4, pause=0.7)
                continue

        _, final_nodes = self.dump_ui("nav_settings_fail_state.xml")
        cap = self.find_first(final_nodes, rid="bruenor.magicbox.free:id/dialog_theme_caption")
        got = cap.text if cap else "<none>"
        raise RuntimeError(f"open_general_settings: expected caption 'General settings', got '{got}'")

    def open_settings_tile(self, label: str) -> None:
        _, nodes = self.dump_ui(f"nav_settings_find_{label.replace(' ', '_')}.xml")
        text_nodes = self.find_all(nodes, rid="bruenor.magicbox.free:id/settings_item_text", text=label)
        if not text_nodes:
            # Fallback to case-insensitive exact match.
            text_nodes = [
                n
                for n in nodes
                if n.rid == "bruenor.magicbox.free:id/settings_item_text" and n.text.lower() == label.lower()
            ]
        if not text_nodes:
            raise RuntimeError(f"settings tile '{label}' not found")
        self.tap_node(text_nodes[0], pause=0.6)

    def set_mouse_mode(self, mode: str = "absolute") -> None:
        self.open_settings_tile("Mouse")
        _, nodes = self.dump_ui("nav_mouse_dialog.xml")
        self.assert_caption("Mouse", nodes, "set_mouse_mode")

        rid = {
            "disabled": "bruenor.magicbox.free:id/mouse_disabled",
            "absolute": "bruenor.magicbox.free:id/mouse_absolute",
            "relative": "bruenor.magicbox.free:id/mouse_relative",
        }[mode]
        target = self.find_first(nodes, rid=rid)
        if target is None:
            raise RuntimeError(f"mouse mode control not found: {mode}")
        self.tap_node(target, pause=0.3)

        confirm = self.find_first(nodes, rid="bruenor.magicbox.free:id/confirm")
        if confirm is None:
            raise RuntimeError("mouse confirm button not found")
        self.tap_node(confirm, pause=0.7)

    def _open_gesture_choice(self, gesture_label: str) -> None:
        _, nodes = self.dump_ui(f"nav_gestures_before_{gesture_label}.xml")
        self.assert_caption("Gestures", nodes, "_open_gesture_choice")
        labels = self.find_all(nodes, rid="bruenor.magicbox.free:id/gesture_menuitem_label")
        match = next((n for n in labels if n.text == gesture_label), None)
        if match is None:
            raise RuntimeError(f"gesture label not found: {gesture_label}")
        self.tap_node(match, pause=0.6)

    def _set_mouse_button_dialog(self, button: str) -> None:
        button = button.capitalize()
        _, nodes = self.dump_ui("nav_mouse_button_dialog.xml")
        value = self.find_first(nodes, rid="bruenor.magicbox.free:id/mouse_button_value") or self.find_first(
            nodes, rid="bruenor.magicbox.free:id/longpress_button_value"
        )
        minus = self.find_first(nodes, rid="bruenor.magicbox.free:id/mouse_button_minus") or self.find_first(
            nodes, rid="bruenor.magicbox.free:id/longpress_button_minus"
        )
        plus = self.find_first(nodes, rid="bruenor.magicbox.free:id/mouse_button_plus") or self.find_first(
            nodes, rid="bruenor.magicbox.free:id/longpress_button_plus"
        )
        confirm = self.find_first(nodes, rid="bruenor.magicbox.free:id/mouse_confirm") or self.find_first(
            nodes, rid="bruenor.magicbox.free:id/longpress_confirm"
        )
        if None in (value, minus, plus, confirm):
            raise RuntimeError("mouse button dialog controls missing")

        # Cycle until target is reached. Some dialogs are flaky with a single tap.
        def read_value() -> str:
            _, n = self.dump_ui("nav_mouse_button_dialog_progress.xml")
            v = self.find_first(n, rid="bruenor.magicbox.free:id/mouse_button_value") or self.find_first(
                n, rid="bruenor.magicbox.free:id/longpress_button_value"
            )
            if v is None:
                raise RuntimeError("mouse button value disappeared during configuration")
            return v.text

        current = value.text
        # Empirically, minus reliably cycles Right -> Middle -> Left.
        if current != button:
            for _ in range(4):
                self.tap_node(minus, pause=0.4)
                current = read_value()
                if current == button:
                    break

        # Fallback in case the dialog has reversed order.
        if current != button:
            for _ in range(4):
                self.tap_node(plus, pause=0.4)
                current = read_value()
                if current == button:
                    break

        if current != button:
            raise RuntimeError(f"failed to set mouse button to {button}, got {current}")

        # Re-dump once more and then confirm.
        _, nodes2 = self.dump_ui("nav_mouse_button_dialog_verify.xml")
        confirm2 = self.find_first(nodes2, rid="bruenor.magicbox.free:id/mouse_confirm") or self.find_first(
            nodes2, rid="bruenor.magicbox.free:id/longpress_confirm"
        )
        if confirm2 is None:
            raise RuntimeError("mouse button confirm missing after verification")
        self.tap_node(confirm2, pause=0.7)

    def set_gesture_mouse_button(self, gesture_label: str, button: str = "Left") -> None:
        self._open_gesture_choice(gesture_label)

        # "Choose" dialog: pick option "Mouse".
        _, choose_nodes = self.dump_ui(f"nav_choose_{gesture_label.replace(' ', '_')}.xml")
        choose_caption = self.find_first(choose_nodes, rid="bruenor.magicbox.free:id/dialog_theme_caption")
        if choose_caption is None or choose_caption.text != "Choose":
            got = choose_caption.text if choose_caption else "<none>"
            raise RuntimeError(f"expected Choose dialog for {gesture_label}, got {got}")
        mouse_opt = self.find_first(choose_nodes, rid="bruenor.magicbox.free:id/image_viewer_item_text", text="Mouse")
        if mouse_opt is None:
            raise RuntimeError("Mouse option not found in Choose dialog")
        self.tap_node(mouse_opt, pause=0.6)

        self._set_mouse_button_dialog(button=button)

        # Verify value in gestures list.
        _, nodes = self.dump_ui(f"nav_gestures_after_{gesture_label.replace(' ', '_')}.xml")
        labels = self.find_all(nodes, rid="bruenor.magicbox.free:id/gesture_menuitem_label")
        values = self.find_all(nodes, rid="bruenor.magicbox.free:id/gesture_menuitem_value")
        value_map: dict[str, str] = {}
        for i, lbl in enumerate(labels):
            if i < len(values):
                value_map[lbl.text] = values[i].text
        expected = f"{button.capitalize()} button"
        got = value_map.get(gesture_label)
        if got != expected:
            raise RuntimeError(f"gesture '{gesture_label}' expected '{expected}', got '{got}'")

    def close_gestures_and_save(self) -> None:
        _, nodes = self.dump_ui("nav_gestures_before_confirm.xml")
        cap = self.find_first(nodes, rid="bruenor.magicbox.free:id/dialog_theme_caption")
        if cap and cap.text == "Gestures":
            confirm = self.find_first(nodes, rid="bruenor.magicbox.free:id/gestures_confirm")
            if confirm is None:
                raise RuntimeError("gestures confirm button not found")
            self.tap_node(confirm, pause=0.7)

        # Depending on app state, confirm may jump directly to canvas.
        self.open_general_settings()
        _, nodes2 = self.dump_ui("nav_settings_before_save.xml")
        save = self.find_first(nodes2, rid="bruenor.magicbox.free:id/settings_dialog_butsavelayout")
        if save is None:
            raise RuntimeError("settings save button not found")
        self.tap_node(save, pause=0.9)

    def configure_navigation(self, mouse_mode: str = "absolute") -> None:
        self.open_general_settings()
        self.set_mouse_mode(mouse_mode)
        # Mouse confirm can return either to settings panel or directly to game.
        self.open_general_settings()
        self.open_settings_tile("Gestures")
        self.set_gesture_mouse_button("Double tap", button="Left")
        self.set_gesture_mouse_button("Longpress", button="Left")
        self.close_gestures_and_save()

    def run_sequence(self, sequence_path: Path, screenshot_prefix: str = "seq") -> None:
        steps = json.loads(sequence_path.read_text(encoding="utf-8"))
        if not isinstance(steps, list):
            raise RuntimeError("sequence file must be a JSON list")
        for i, step in enumerate(steps, start=1):
            action = step.get("action")
            if action == "tap":
                self.tap(int(step["x"]), int(step["y"]), pause=float(step.get("pause", 0.25)))
            elif action == "double_tap":
                self.double_tap(
                    int(step["x"]),
                    int(step["y"]),
                    gap=float(step.get("gap", 0.12)),
                    pause=float(step.get("pause", 0.35)),
                )
            elif action == "long_press":
                self.long_press(
                    int(step["x"]),
                    int(step["y"]),
                    duration_ms=int(step.get("duration_ms", 650)),
                    pause=float(step.get("pause", 0.35)),
                )
            elif action == "swipe":
                self.swipe(
                    int(step["x1"]),
                    int(step["y1"]),
                    int(step["x2"]),
                    int(step["y2"]),
                    duration_ms=int(step.get("duration_ms", 200)),
                    pause=float(step.get("pause", 0.3)),
                )
            elif action == "keyevent":
                self.keyevent(int(step["keycode"]), pause=float(step.get("pause", 0.25)))
            elif action == "sleep":
                time.sleep(float(step.get("seconds", 1.0)))
            elif action == "screenshot":
                name = str(step.get("name", f"{screenshot_prefix}_{i:03d}.png"))
                self.screenshot(name)
            else:
                raise RuntimeError(f"unknown action in sequence step {i}: {action}")

    def click_button(
        self,
        *,
        screen: str,
        button: str,
        button_map_path: Path = DEFAULT_BUTTON_MAP,
        pause: Optional[float] = None,
        coords_mode: str = "auto",
    ) -> None:
        data = json.loads(button_map_path.read_text(encoding="utf-8"))
        screens = data.get("screens", {})
        if screen not in screens:
            raise RuntimeError(f"screen '{screen}' not found in map: {button_map_path}")
        buttons = screens[screen]
        if button not in buttons:
            raise RuntimeError(f"button '{button}' not found in screen '{screen}' map")

        spec = buttons[button]
        action = spec.get("action")
        x = int(spec.get("x"))
        y = int(spec.get("y"))
        duration_ms = int(spec.get("duration_ms", 650))
        wait = float(pause) if pause is not None else float(spec.get("pause", 0.8))

        source_space = "pcf"
        if coords_mode == "pcf":
            source_space = "pcf"
        elif coords_mode == "device":
            source_space = "device"
        else:
            # auto: spec > map meta > default pcf
            source_space = str(
                spec.get("coord_space")
                or data.get("meta", {}).get("coordinate_space")
                or "pcf_1080x810"
            ).lower()

        if "device" not in source_space:
            x, y = self.pcf_to_device(x, y)

        if action == "tap":
            self.tap(x, y, pause=wait)
        elif action == "double_tap":
            self.double_tap(x, y, pause=wait)
        elif action == "long_press":
            self.long_press(x, y, duration_ms=duration_ms, pause=wait)
        else:
            raise RuntimeError(f"unsupported mapped action '{action}' for {screen}.{button}")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Magic DOSBox navigation helper over ADB")
    p.add_argument("--serial", help="adb device serial")
    p.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR, help="output folder for xml/png artifacts")
    p.add_argument("--dry-run", action="store_true", help="print adb commands without executing")

    sub = p.add_subparsers(dest="cmd", required=True)

    c_conf = sub.add_parser("configure", help="configure input for deterministic DOSBox navigation")
    c_conf.add_argument("--mouse-mode", choices=["disabled", "absolute", "relative"], default="absolute")

    c_start = sub.add_parser("start-magic", help="bring Magic DOSBox to foreground")
    c_start.set_defaults(_noop=True)

    c_shot = sub.add_parser("screenshot", help="capture device screenshot into out-dir")
    c_shot.add_argument("--name", required=True)

    c_dump = sub.add_parser("dump-ui", help="dump uiautomator xml into out-dir")
    c_dump.add_argument("--name", default="ui_state.xml")

    c_act = sub.add_parser("act", help="raw canvas action helpers")
    c_act.add_argument("action", choices=["tap", "double_tap", "long_press", "swipe", "keyevent"])
    c_act.add_argument("coords", nargs="+", help="coordinates/keycode")
    c_act.add_argument("--duration-ms", type=int, default=650)
    c_act.add_argument("--pause", type=float, default=0.35)

    c_seq = sub.add_parser("run-seq", help="run a JSON sequence of adb actions")
    c_seq.add_argument("--file", type=Path, required=True)
    c_seq.add_argument("--screenshot-prefix", default="seq")

    c_cal = sub.add_parser("calibrate", help="auto-detect coord transform for 1080x810 PCF map space")
    c_cal.add_argument("--refresh", action="store_true")

    c_btn = sub.add_parser("click-btn", help="click a mapped DOS button by logical name")
    c_btn.add_argument("--screen", required=True, help="logical screen id from button map")
    c_btn.add_argument("--button", required=True, help="logical button id from button map")
    c_btn.add_argument("--map-file", type=Path, default=DEFAULT_BUTTON_MAP)
    c_btn.add_argument("--pause", type=float, default=None, help="override post-action pause seconds")
    c_btn.add_argument(
        "--coords-mode",
        choices=["auto", "pcf", "device"],
        default="auto",
        help="interpret map coords as auto/pcf(1080x810)/device pixels",
    )

    return p


def main() -> None:
    args = build_parser().parse_args()
    nav = DosboxNavigator(out_dir=args.out_dir, serial=args.serial, dry_run=args.dry_run)

    if args.cmd == "configure":
        nav.configure_navigation(mouse_mode=args.mouse_mode)
        print(f"OK: configured navigation (mouse={args.mouse_mode}, gestures: doubletap+longpress=Left).")
        return

    if args.cmd == "start-magic":
        nav.start_magic()
        print("OK: Magic DOSBox opened.")
        return

    if args.cmd == "screenshot":
        path = nav.screenshot(args.name)
        print(path)
        return

    if args.cmd == "dump-ui":
        path, _ = nav.dump_ui(args.name)
        print(path)
        return

    if args.cmd == "run-seq":
        nav.run_sequence(args.file, screenshot_prefix=args.screenshot_prefix)
        print("OK: sequence executed.")
        return

    if args.cmd == "calibrate":
        t = nav.detect_coord_transform(refresh=args.refresh)
        print(json.dumps(t.as_dict(), ensure_ascii=False, indent=2))
        return

    if args.cmd == "click-btn":
        nav.click_button(
            screen=args.screen,
            button=args.button,
            button_map_path=args.map_file,
            pause=args.pause,
            coords_mode=args.coords_mode,
        )
        print(f"OK: clicked {args.screen}.{args.button}")
        return

    if args.cmd == "act":
        nums = [int(x) for x in args.coords]
        if args.action == "tap":
            if len(nums) != 2:
                raise SystemExit("act tap expects: x y")
            nav.tap(nums[0], nums[1], pause=args.pause)
        elif args.action == "double_tap":
            if len(nums) != 2:
                raise SystemExit("act double_tap expects: x y")
            nav.double_tap(nums[0], nums[1], pause=args.pause)
        elif args.action == "long_press":
            if len(nums) != 2:
                raise SystemExit("act long_press expects: x y")
            nav.long_press(nums[0], nums[1], duration_ms=args.duration_ms, pause=args.pause)
        elif args.action == "swipe":
            if len(nums) != 4:
                raise SystemExit("act swipe expects: x1 y1 x2 y2")
            nav.swipe(nums[0], nums[1], nums[2], nums[3], duration_ms=args.duration_ms, pause=args.pause)
        elif args.action == "keyevent":
            if len(nums) != 1:
                raise SystemExit("act keyevent expects: keycode")
            nav.keyevent(nums[0], pause=args.pause)
        print("OK")
        return

    raise SystemExit(f"unknown command: {args.cmd}")


if __name__ == "__main__":
    main()
