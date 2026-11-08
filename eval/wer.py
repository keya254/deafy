#!/usr/bin/env python3
"""Word error rate (WER) for caption accuracy tests. See docs/EVALUATION.md.

Inputs (paths are relative to this file's folder by default):
  manifest.csv           columns: clip_id, language, environment, duration_s
  references/<id>.txt    hand transcript, one line
  results/<engine>.csv   columns: clip_id, hypothesis

Usage:
  python3 eval/wer.py                     # all engines in results/
  python3 eval/wer.py results/whisper.csv # one engine

Scoring rules (from docs/EVALUATION.md):
  - Text is lowercased and punctuation is removed before scoring.
  - [unclear] markers are removed from the reference. Any words an engine
    produces at that spot count as insertions, so keep [unclear] rare.
"""

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
UNCLEAR = "[unclear]"


def normalise(text: str) -> list[str]:
    text = text.lower().replace(UNCLEAR, " ")
    text = re.sub(r"[^\w\s'-]", " ", text)
    return text.split()


def edit_counts(ref: list[str], hyp: list[str]) -> tuple[int, int, int]:
    """Return (substitutions, deletions, insertions) for the minimum-edit alignment."""
    n, m = len(ref), len(hyp)
    # dp[i][j] = (cost, subs, dels, ins) to turn ref[:i] into hyp[:j]
    dp = [[(0, 0, 0, 0)] * (m + 1) for _ in range(n + 1)]
    for i in range(1, n + 1):
        dp[i][0] = (i, 0, i, 0)
    for j in range(1, m + 1):
        dp[0][j] = (j, 0, 0, j)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            if ref[i - 1] == hyp[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]
                continue
            sub = dp[i - 1][j - 1]
            dele = dp[i - 1][j]
            ins = dp[i][j - 1]
            candidates = [
                (sub[0] + 1, sub[1] + 1, sub[2], sub[3]),
                (dele[0] + 1, dele[1], dele[2] + 1, dele[3]),
                (ins[0] + 1, ins[1], ins[2], ins[3] + 1),
            ]
            dp[i][j] = min(candidates, key=lambda c: c[0])
    _, s, d, i_ = dp[n][m]
    return s, d, i_


def load_manifest(path: Path) -> dict[str, dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as f:
        return {row["clip_id"]: row for row in csv.DictReader(f)}


def load_results(path: Path) -> dict[str, str]:
    with path.open(newline="", encoding="utf-8") as f:
        return {row["clip_id"]: row["hypothesis"] for row in csv.DictReader(f)}


def score(clip_ids, manifest, hyps, refs_dir: Path):
    """Return a dict with totals over the given clips."""
    totals = {"words": 0, "subs": 0, "dels": 0, "ins": 0, "clips": 0, "missing": 0}
    for clip_id in clip_ids:
        ref_path = refs_dir / f"{clip_id}.txt"
        if not ref_path.exists() or clip_id not in hyps:
            totals["missing"] += 1
            continue
        ref = normalise(ref_path.read_text(encoding="utf-8"))
        hyp = normalise(hyps[clip_id])
        s, d, i = edit_counts(ref, hyp)
        totals["words"] += len(ref)
        totals["subs"] += s
        totals["dels"] += d
        totals["ins"] += i
        totals["clips"] += 1
    if totals["words"]:
        totals["wer"] = (totals["subs"] + totals["dels"] + totals["ins"]) / totals["words"]
    else:
        totals["wer"] = float("nan")
    return totals


def report(engine: str, manifest, hyps, refs_dir: Path) -> None:
    print(f"\n== {engine}")
    overall = score(manifest.keys(), manifest, hyps, refs_dir)
    print(f"overall  WER {overall['wer']*100:6.1f}%  clips {overall['clips']}  words {overall['words']}  missing {overall['missing']}")

    for field in ("language", "environment"):
        groups = defaultdict(list)
        for clip_id, row in manifest.items():
            groups[row.get(field, "?")].append(clip_id)
        for name in sorted(groups):
            t = score(groups[name], manifest, hyps, refs_dir)
            print(f"{field:<9}{name:<10} WER {t['wer']*100:6.1f}%  clips {t['clips']}  words {t['words']}")


def main(argv: list[str]) -> int:
    manifest_path = HERE / "manifest.csv"
    refs_dir = HERE / "references"
    if not manifest_path.exists():
        print(f"No manifest at {manifest_path}", file=sys.stderr)
        return 1
    manifest = load_manifest(manifest_path)

    result_files = [Path(a) for a in argv] or sorted((HERE / "results").glob("*.csv"))
    if not result_files:
        print("No results files found in eval/results/", file=sys.stderr)
        return 1
    for path in result_files:
        report(path.stem, manifest, load_results(path), refs_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
