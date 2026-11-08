#!/usr/bin/env python3
"""Run the on-device Whisper model (sherpa-onnx, int8) on a prepared test set.

This is the on-device engine from docs/MODELS.md, run on a Mac with the same
sherpa-onnx library version as the app (2.0.1). It writes a results CSV that
eval/wer.py can score.

Differences from the phone pipeline, so read the numbers with this in mind:
  - Each clip is decoded whole. The phone splits speech with Silero VAD first.
  - No noise removal (GTCRN) and no gain or normalisation is applied.
  - The language is fixed per run (sw or en), like the app's Swahili and English modes.
  - Decode times are from this Mac, not from the phone, so they are not comparable.

Usage:
  python3 eval/run_sherpa_whisper.py --dir eval/fleurs/sw_ke --language sw \
      --model eval/models --out eval/results/whisper-small-int8-sw_ke.csv
"""

import argparse
import csv
import statistics
import sys
import time
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx


def read_wav(path: Path) -> tuple[int, np.ndarray]:
    with wave.open(str(path), "rb") as w:
        rate = w.getframerate()
        channels = w.getnchannels()
        frames = w.readframes(w.getnframes())
    samples = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    if channels > 1:
        samples = samples.reshape(-1, channels).mean(axis=1)
    return rate, samples


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dir", required=True, type=Path, help="prepared test set folder (manifest.csv, audio/)")
    parser.add_argument("--language", required=True, choices=["sw", "en"], help="language to decode as")
    parser.add_argument("--model", required=True, type=Path, help="folder with encoder.int8.onnx, decoder.int8.onnx, tokens.txt")
    parser.add_argument("--out", required=True, type=Path, help="results CSV to write")
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()

    encoder = args.model / "encoder.int8.onnx"
    decoder = args.model / "decoder.int8.onnx"
    tokens = args.model / "tokens.txt"
    for p in (encoder, decoder, tokens):
        if not p.exists():
            print(f"Missing model file: {p}", file=sys.stderr)
            return 1

    recognizer = sherpa_onnx.OfflineRecognizer.from_whisper(
        encoder=str(encoder),
        decoder=str(decoder),
        tokens=str(tokens),
        language=args.language,
        task="transcribe",
        num_threads=args.threads,
        decoding_method="greedy_search",
    )

    manifest = list(csv.DictReader((args.dir / "manifest.csv").open(newline="", encoding="utf-8")))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    ratios = []
    with args.out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["clip_id", "hypothesis", "decode_s", "audio_s"])
        for i, row in enumerate(manifest, 1):
            clip_id = row["clip_id"]
            rate, samples = read_wav(args.dir / "audio" / f"{clip_id}.wav")
            audio_s = len(samples) / rate
            started = time.perf_counter()
            stream = recognizer.create_stream()
            stream.accept_waveform(rate, samples)
            recognizer.decode_stream(stream)
            decode_s = time.perf_counter() - started
            text = stream.result.text.strip()
            writer.writerow([clip_id, text, round(decode_s, 3), round(audio_s, 2)])
            ratios.append(decode_s / audio_s if audio_s else 0.0)
            if i % 50 == 0 or i == len(manifest):
                print(f"{i}/{len(manifest)} clips", file=sys.stderr)

    print(f"Wrote {len(manifest)} hypotheses to {args.out}")
    if ratios:
        print(f"Median real-time factor on this Mac: {statistics.median(ratios):.2f} "
              f"(decode time / audio time; below 1 is faster than real time)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
