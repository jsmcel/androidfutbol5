#!/usr/bin/env python3
"""Draw quadrant/subquadrant overlays on screenshots for deterministic ADB tapping."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def parse_box(text: str) -> tuple[int, int, int, int]:
    parts = [int(x.strip()) for x in text.split(",")]
    if len(parts) != 4:
        raise ValueError("--frame must be x,y,w,h")
    return parts[0], parts[1], parts[2], parts[3]


def parse_quad(text: str) -> tuple[int, int]:
    parts = [int(x.strip()) for x in text.split(",")]
    if len(parts) != 2:
        raise ValueError("--focus-q must be col,row (0-based)")
    return parts[0], parts[1]


def draw_grid(
    draw: ImageDraw.ImageDraw,
    *,
    x0: int,
    y0: int,
    w: int,
    h: int,
    cols: int,
    rows: int,
    prefix: str,
    color: tuple[int, int, int],
    font: ImageFont.ImageFont,
) -> None:
    draw.rectangle([x0, y0, x0 + w, y0 + h], outline=color, width=3)

    for c in range(1, cols):
        x = x0 + round((w * c) / cols)
        draw.line([x, y0, x, y0 + h], fill=color, width=2)
    for r in range(1, rows):
        y = y0 + round((h * r) / rows)
        draw.line([x0, y, x0 + w, y], fill=color, width=2)

    for r in range(rows):
        for c in range(cols):
            cx1 = x0 + round((w * c) / cols)
            cx2 = x0 + round((w * (c + 1)) / cols)
            cy1 = y0 + round((h * r) / rows)
            cy2 = y0 + round((h * (r + 1)) / rows)
            cx = (cx1 + cx2) // 2
            cy = (cy1 + cy2) // 2
            label = f"{prefix}{r * cols + c + 1} ({cx},{cy})"
            draw.text((cx - 58, cy - 8), label, fill=color, font=font)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True, help="Input PNG/JPG screenshot.")
    parser.add_argument("--out", type=Path, required=True, help="Output PNG with overlays.")
    parser.add_argument("--frame", default="0,297,1080,508", help="Game frame as x,y,w,h.")
    parser.add_argument("--major-cols", type=int, default=4, help="Major grid columns.")
    parser.add_argument("--major-rows", type=int, default=2, help="Major grid rows.")
    parser.add_argument("--sub-cols", type=int, default=4, help="Subgrid columns.")
    parser.add_argument("--sub-rows", type=int, default=4, help="Subgrid rows.")
    parser.add_argument(
        "--focus-q",
        default=None,
        help="Focused major cell as col,row (0-based). If set, draw subgrid inside that major cell.",
    )
    args = parser.parse_args()

    fx, fy, fw, fh = parse_box(args.frame)

    img = Image.open(args.image).convert("RGB")
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default()

    draw_grid(
        draw,
        x0=fx,
        y0=fy,
        w=fw,
        h=fh,
        cols=args.major_cols,
        rows=args.major_rows,
        prefix="Q",
        color=(255, 200, 40),
        font=font,
    )

    if args.focus_q is not None:
        qcol, qrow = parse_quad(args.focus_q)
        if not (0 <= qcol < args.major_cols and 0 <= qrow < args.major_rows):
            raise ValueError("--focus-q outside major grid")

        qx1 = fx + round((fw * qcol) / args.major_cols)
        qx2 = fx + round((fw * (qcol + 1)) / args.major_cols)
        qy1 = fy + round((fh * qrow) / args.major_rows)
        qy2 = fy + round((fh * (qrow + 1)) / args.major_rows)

        draw_grid(
            draw,
            x0=qx1,
            y0=qy1,
            w=qx2 - qx1,
            h=qy2 - qy1,
            cols=args.sub_cols,
            rows=args.sub_rows,
            prefix="q",
            color=(40, 240, 255),
            font=font,
        )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    img.save(args.out)
    print(str(args.out))


if __name__ == "__main__":
    main()
