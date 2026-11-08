#!/usr/bin/env python3
"""Prepare a Google FLEURS test split for docs/EVALUATION.md.

FLEURS (google/fleurs on Hugging Face) is licensed CC-BY-4.0. Give credit when you publish
results. The data is not committed to git: everything is written to eval/fleurs/<lang>/,
which is git-ignored.

Download the test split for a language first, from the Hugging Face repo, into eval/fleurs/<lang>/:
  data/<lang>/test.tsv
  data/<lang>/audio/test.tar.gz
(for example <lang> = sw_ke or en_us). Then run:

  python3 eval/prepare_fleurs.py --dir eval/fleurs/sw_ke

Output in the same folder:
  audio/fleurs-<file>.wav        16 kHz mono WAV, one per recording (<file> is the FLEURS file name)
  references/fleurs-<file>.txt   the raw transcription (scoring normalises case and punctuation)

The same sentence can be read by several speakers, so each recording is its own clip.
  manifest.csv                 same columns as eval/manifest.csv

FLEURS TSV files have no header. Columns are: id, file_name, raw_transcription,
transcription, words, gender.
"""

import argparse
import csv
import shutil
import subprocess
import sys
import tarfile
import tempfile
import wave
from pathlib import Path

MANIFEST_COLUMNS = ["clip_id", "speaker_id", "language", "environment", "distance", "duration_s", "consent_ref"]


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
    parser.add_argument("--dir", required=True, type=Path, help="folder with test.tsv and audio/test.tar.gz")
    parser.add_argument("--n", type=int, default=0, help="limit to the first N clips (default: all)")
    args = parser.parse_args()

    folder = args.dir
    tsv = folder / "test.tsv"
    archive = folder / "audio" / "test.tar.gz"
    if not tsv.exists() or not archive.exists():
        print(f"Expected {tsv} and {archive}. Download them first (see the script header).", file=sys.stderr)
        return 1

    language = folder.name
    out_audio = folder / "audio"
    refs_dir = folder / "references"
    refs_dir.mkdir(parents=True, exist_ok=True)

    with tsv.open(newline="", encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE) if len(r) >= 3]
    if args.n:
        rows = rows[: args.n]
    wanted = {r[1]: r for r in rows}  # file_name -> row

    manifest_rows = []
    skipped = 0
    with tempfile.TemporaryDirectory() as tmp, tarfile.open(archive, "r:gz") as tar:
        for member in tar.getmembers():
            name = Path(member.name).name
            if not member.isfile() or name not in wanted:
                continue
            row = wanted[name]
            # The sentence id repeats when several speakers read the same sentence, so use the file name.
            clip_id = f"fleurs-{Path(name).stem}"
            raw = Path(tmp) / name
            with tar.extractfile(member) as src, raw.open("wb") as dst:
                shutil.copyfileobj(src, dst)
            wav = out_audio / f"{clip_id}.wav"
            wav.parent.mkdir(parents=True, exist_ok=True)
            if not to_wav(raw, wav):
                skipped += 1
                continue
            (refs_dir / f"{clip_id}.txt").write_text(row[2].strip() + "\n", encoding="utf-8")
            manifest_rows.append({
                "clip_id": clip_id,
                "speaker_id": "unknown",
                "language": language,
                "environment": "read",
                "distance": "unknown",
                "duration_s": duration_seconds(wav),
                "consent_ref": "fleurs-cc-by-4.0",
            })

    missing = len(wanted) - len(manifest_rows) - skipped
    if not manifest_rows:
        print("No clips were written. Check the archive matches test.tsv.", file=sys.stderr)
        return 1

    with (folder / "manifest.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=MANIFEST_COLUMNS)
        writer.writeheader()
        writer.writerows(manifest_rows)

    total = sum(r["duration_s"] for r in manifest_rows)
    print(f"Wrote {len(manifest_rows)} clips ({total / 60:.1f} min) to {folder}. "
          f"Skipped {skipped}, not in archive {missing}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
