#!/usr/bin/env python3
"""Prepare a Common Voice sample as the Swahili baseline for docs/EVALUATION.md.

Common Voice data is licensed CC0, but Mozilla forbids re-hosting or mirroring it, and
access must go through the Mozilla Data Collective. So this script works on a dataset
you have downloaded yourself. Everything it writes goes to eval/cv/, which is git-ignored.

Steps:
  1. Download the Swahili Scripted Speech archive from the Mozilla Data Collective
     (accept its terms in your own account) and extract it. You need the folder that
     contains test.tsv and clips/.
  2. Run:
       python3 eval/prepare_common_voice.py --dir /path/to/extracted/folder --n 200

Output (all under eval/cv/, git-ignored):
  audio/cv-0001.wav        16 kHz mono WAV, the format the models expect
  references/cv-0001.txt   the validated sentence
  manifest.csv             same columns as eval/manifest.csv, with no personal data

Only validated clips are used (more up votes than down votes, no down votes). The
test split is used so no speaker in the sample also appears in the training split.
Speaker IDs are a short hash of the client ID, so the manifest cannot be linked back.

Then score an engine with:
  python3 eval/wer.py --manifest eval/cv/manifest.csv --references eval/cv/references results.csv
"""

import argparse
import csv
import hashlib
import random
import subprocess
import sys
import wave
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "cv"
MANIFEST_COLUMNS = ["clip_id", "speaker_id", "language", "environment", "distance", "duration_s", "consent_ref"]


def read_tsv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def validated(row: dict[str, str]) -> bool:
    try:
        up = int(row.get("up_votes") or 0)
        down = int(row.get("down_votes") or 0)
    except ValueError:
        return False
    return bool((row.get("sentence") or "").strip()) and up >= 2 and down == 0


def to_wav(src: Path, dst: Path) -> bool:
    result = subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src), "-ar", "16000", "-ac", "1", str(dst)],
        check=False,
    )
    return result.returncode == 0 and dst.exists()


def duration_seconds(wav: Path) -> float:
    with wave.open(str(wav), "rb") as w:
        return round(w.getnframes() / w.getframerate(), 2)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", required=True, type=Path, help="extracted Common Voice folder (contains test.tsv and clips/)")
    parser.add_argument("--tsv", default="test.tsv", help="split file to sample from (default: test.tsv)")
    parser.add_argument("--n", type=int, default=200, help="number of clips to sample (default: 200)")
    parser.add_argument("--seed", type=int, default=1, help="random seed, so the sample is repeatable")
    args = parser.parse_args()

    tsv = args.dir / args.tsv
    clips_dir = args.dir / "clips"
    if not tsv.exists() or not clips_dir.is_dir():
        print(f"Expected {tsv} and {clips_dir}/. Check the --dir path.", file=sys.stderr)
        return 1

    rows = [r for r in read_tsv(tsv) if validated(r)]
    if not rows:
        print("No validated clips found in the split.", file=sys.stderr)
        return 1
    rng = random.Random(args.seed)
    rng.shuffle(rows)

    audio_dir = OUT / "audio"
    refs_dir = OUT / "references"
    audio_dir.mkdir(parents=True, exist_ok=True)
    refs_dir.mkdir(parents=True, exist_ok=True)

    manifest_rows = []
    skipped = 0
    for row in rows:
        if len(manifest_rows) >= args.n:
            break
        src = clips_dir / row["path"]
        if not src.exists():
            skipped += 1
            continue
        clip_id = f"cv-{len(manifest_rows) + 1:04d}"
        wav = audio_dir / f"{clip_id}.wav"
        if not to_wav(src, wav):
            skipped += 1
            continue
        (refs_dir / f"{clip_id}.txt").write_text(row["sentence"].strip() + "\n", encoding="utf-8")
        speaker = hashlib.sha256(row.get("client_id", "").encode("utf-8")).hexdigest()[:8]
        manifest_rows.append({
            "clip_id": clip_id,
            "speaker_id": speaker,
            "language": row.get("locale") or "sw",
            "environment": "read-studio",
            "distance": "unknown",
            "duration_s": duration_seconds(wav),
            "consent_ref": "common-voice-cc0",
        })

    if not manifest_rows:
        print("No clips could be converted. Is ffmpeg installed?", file=sys.stderr)
        return 1

    with (OUT / "manifest.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=MANIFEST_COLUMNS)
        writer.writeheader()
        writer.writerows(manifest_rows)

    total = sum(r["duration_s"] for r in manifest_rows)
    print(f"Wrote {len(manifest_rows)} clips ({total / 60:.1f} min) to {OUT}. Skipped {skipped}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
