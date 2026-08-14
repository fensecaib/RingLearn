#!/usr/bin/env python3
"""RingLearn AI 页字体子集化脚本（一次性资产准备，非构建步骤）。

依赖（开发机）: pip install fonttools
用法: python tools/subset_fonts.py

把 app/src/main/res/font/ 下的 4 个 Zen Maru Gothic 静态字重原地裁剪为：
  - ASCII + Latin-1、通用标点、CJK 标点、平假名/片假名、全角/半角形式
  - 常用漢字 2140 个（tools/joyo.txt，离线可复现）
  - assets/jlpt_n2_words.json 中出现的全部字符
简体中文专用字形不在 Zen Maru Gothic 字符集内，裁剪与否都回退系统字体，
行为与裁剪前一致（见项目内说明）。
"""

import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
JOYO = ROOT / "tools" / "joyo.txt"
WORD_BANK = ROOT / "app" / "src" / "main" / "assets" / "jlpt_n2_words.json"

RANGES = [
    (0x0020, 0x00FF),  # ASCII + Latin-1
    (0x2000, 0x206F),  # 通用标点
    (0x3000, 0x303F),  # CJK 标点
    (0x3040, 0x30FF),  # 平假名 + 片假名
    (0x31F0, 0x31FF),  # 片假名语音扩展
    (0xFF00, 0xFFEF),  # 全角/半角形式
]
WEIGHTS = ["regular", "medium", "bold", "black"]


def collect_chars() -> str:
    chars = set()
    for lo, hi in RANGES:
        chars.update(chr(cp) for cp in range(lo, hi + 1))
    if JOYO.exists():
        chars.update(JOYO.read_text(encoding="utf-8"))
    if WORD_BANK.exists():
        data = json.loads(WORD_BANK.read_text(encoding="utf-8"))

        def walk(node) -> None:
            if isinstance(node, str):
                chars.update(node)
            elif isinstance(node, list):
                for item in node:
                    walk(item)
            elif isinstance(node, dict):
                for value in node.values():
                    walk(value)

        walk(data)
    return "".join(sorted(chars))


def main() -> None:
    text_file = ROOT / "tools" / ".subset_chars.txt"
    text_file.write_text(collect_chars(), encoding="utf-8")
    for weight in WEIGHTS:
        src = FONT_DIR / f"zen_maru_gothic_{weight}.ttf"
        if not src.exists():
            print(f"missing source font: {src}", file=sys.stderr)
            sys.exit(1)
        tmp = src.with_suffix(".tmp.ttf")
        subprocess.run(
            [
                sys.executable,
                "-m",
                "fontTools.subset",
                str(src),
                "--text-file=" + str(text_file),
                "--layout-features=*",
                "--no-hinting",
                "--output-file=" + str(tmp),
            ],
            check=True,
        )
        tmp.replace(src)
        print(f"{weight}: {src.stat().st_size / 1e6:.2f} MB")
    text_file.unlink(missing_ok=True)
    print("done")


if __name__ == "__main__":
    main()
