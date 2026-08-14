#!/usr/bin/env python3
"""RingLearn N1 词库整理脚本：校验 tools/n1_seed_source.json 并写出 assets/jlpt_n1_words.json。

数据来源与许可：
- 词条内容（词/假名/中文释义/日文例句/中文译文）来自 egg rolls 的 JLPT 一万词牌组
  https://github.com/5mdld/anki-jlpt-decks ，遵循 CC BY-NC 4.0（本项目仅自用、非商业）。
  署名文件见 licenses/eggrolls-JLPT10k-CC-BY-NC-4.0.txt 。
- 选择口径：N1 高频 + 中频全部，低频按牌组顺序补齐至 2000 条；与 N2 资产按 (词, 假名) 去重。

用法：
    python tools/curate_vocab.py

校验规则（任一违反即非零退出）：
1. 每条恰好 5 个字段且非空；
2. 假名仅含平/片假名、长音符与中点；
3. 词表内 (word, kana) 无重复，且不与 N2 资产重复；
4. 词条数 >= 1900（高频+中频下限），供维护者确认未意外截断。
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools" / "n1_seed_source.json"
N2_ASSET = ROOT / "app" / "src" / "main" / "assets" / "jlpt_n2_words.json"
OUT = ROOT / "app" / "src" / "main" / "assets" / "jlpt_n1_words.json"

KANA_RE = re.compile(r"^[\u3040-\u309F\u30A0-\u30FF\u30FC・]+$")


def fail(msg: str) -> None:
    print(f"[FAIL] {msg}")
    sys.exit(1)


def main() -> int:
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    if not isinstance(source, list) or len(source) < 1900:
        fail(f"词条数异常：{len(source)}（应 >= 1900）")

    seen: set[tuple[str, str]] = set()
    for i, item in enumerate(source):
        if not isinstance(item, list) or len(item) != 5:
            fail(f"第 {i} 条不是 5 元素数组：{item!r}")
        word, kana, meaning, example, example_meaning = item
        if not all(isinstance(x, str) and x.strip() for x in item):
            fail(f"第 {i} 条存在空字段：{item!r}")
        if not KANA_RE.fullmatch(kana):
            fail(f"第 {i} 条假名含非法字符：{word!r} / {kana!r}")
        key = (word.strip(), kana.strip())
        if key in seen:
            fail(f"第 {i} 条与词表内重复：{word} / {kana}")
        seen.add(key)

    n2 = json.loads(N2_ASSET.read_text(encoding="utf-8"))
    n2_keys = {(w[0].strip(), w[1].strip()) for w in n2}
    overlap = seen & n2_keys
    if overlap:
        fail(f"与 N2 资产重复 {len(overlap)} 条：{sorted(overlap)[:5]}…")

    OUT.write_text(
        json.dumps(source, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"[OK] entries={len(source)} bytes={OUT.stat().st_size} -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
