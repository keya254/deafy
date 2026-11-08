#!/usr/bin/env python3
"""Fine-tune Whisper on Swahili speech, then score it on held-out test sets.

Designed to run on a Kaggle GPU notebook. See docs/TRAINING.md for the steps.

Data folders use the same layout as eval/prepare_common_voice.py and eval/prepare_fleurs.py:
  manifest.csv          clip_id, language, ...
  audio/<clip_id>.wav   16 kHz mono WAV
  references/<clip_id>.txt

Rules built into this script:
  - Training folders must not contain FLEURS clips. FLEURS is the held-out test set.
  - Evaluation runs before training (the base model) and after it, on the same clips,
    so the change is visible. Scoring uses eval/wer.py, the same metric as the baseline.

Example (Kaggle, after the data is in place):
  python3 training/finetune_whisper.py \
      --train /kaggle/working/cv-sw-train \
      --eval /kaggle/working/cv-sw-test /kaggle/working/fleurs-sw_ke \
      --max-train-clips 20000 --steps 1500 --out /kaggle/working/run1
"""

import argparse
import csv
import random
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf
import torch
from transformers import (
    Seq2SeqTrainer,
    Seq2SeqTrainingArguments,
    WhisperForConditionalGeneration,
    WhisperProcessor,
)

ROOT = Path(__file__).resolve().parent.parent
WER_SCRIPT = ROOT / "eval" / "wer.py"
HELD_OUT_PREFIX = "fleurs-"
LANGUAGE = "swahili"  # Whisper's name for Swahili; the tokenizer uses the same language token


def read_manifest(folder: Path) -> list[dict[str, str]]:
    with (folder / "manifest.csv").open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def check_training_folder(folder: Path) -> None:
    for row in read_manifest(folder):
        if row["clip_id"].startswith(HELD_OUT_PREFIX):
            raise SystemExit(
                f"{folder} contains {row['clip_id']}. FLEURS is the held-out test set and must not be used for training."
            )


class SpeechDataset(torch.utils.data.Dataset):
    def __init__(self, folders: list[Path], processor: WhisperProcessor, max_clips: int, seed: int):
        self.processor = processor
        items = []
        for folder in folders:
            check_training_folder(folder)
            for row in read_manifest(folder):
                items.append((folder, row["clip_id"]))
        random.Random(seed).shuffle(items)
        if max_clips:
            items = items[:max_clips]
        self.items = items

    def __len__(self) -> int:
        return len(self.items)

    def __getitem__(self, i: int) -> dict[str, Any]:
        folder, clip_id = self.items[i]
        audio, rate = sf.read(folder / "audio" / f"{clip_id}.wav", dtype="float32")
        if rate != 16000:
            raise ValueError(f"{clip_id} is {rate} Hz; expected 16000")
        text = (folder / "references" / f"{clip_id}.txt").read_text(encoding="utf-8").strip()
        features = self.processor.feature_extractor(audio, sampling_rate=16000).input_features[0]
        labels = self.processor.tokenizer(text).input_ids
        return {"input_features": features, "labels": labels}


@dataclass
class SpeechCollator:
    processor: WhisperProcessor
    decoder_start_token_id: int

    def __call__(self, features: list[dict[str, Any]]) -> dict[str, torch.Tensor]:
        inputs = [{"input_features": f["input_features"]} for f in features]
        batch = self.processor.feature_extractor.pad(inputs, return_tensors="pt")
        label_features = [{"input_ids": f["labels"]} for f in features]
        label_batch = self.processor.tokenizer.pad(label_features, return_tensors="pt")
        labels = label_batch["input_ids"].masked_fill(label_batch.attention_mask.ne(1), -100)
        # The model adds the start token itself, so remove it from the labels if present.
        if (labels[:, 0] == self.decoder_start_token_id).all():
            labels = labels[:, 1:]
        batch["labels"] = labels
        return batch


def transcribe(model, processor, folder: Path, device: str, batch_size: int, out_csv: Path) -> Path:
    """Write clip_id,hypothesis for every clip in folder to out_csv."""
    rows = read_manifest(folder)
    model.eval()
    results = []
    with torch.no_grad():
        for start in range(0, len(rows), batch_size):
            chunk = rows[start:start + batch_size]
            audio = [sf.read(folder / "audio" / f"{r['clip_id']}.wav", dtype="float32")[0] for r in chunk]
            inputs = processor.feature_extractor(audio, sampling_rate=16000, return_tensors="pt")
            features = inputs.input_features.to(device, dtype=model.dtype)
            ids = model.generate(features, language=LANGUAGE, task="transcribe", max_new_tokens=225)
            texts = processor.batch_decode(ids, skip_special_tokens=True)
            results.extend((r["clip_id"], t.strip()) for r, t in zip(chunk, texts))
            print(f"  {min(start + batch_size, len(rows))}/{len(rows)}", flush=True)
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["clip_id", "hypothesis"])
        writer.writerows(results)
    return out_csv


def score(name: str, folder: Path, hyp_csv: Path) -> None:
    print(f"\n== {name}")
    subprocess.run(
        [sys.executable, str(WER_SCRIPT), "--manifest", str(folder / "manifest.csv"),
         "--references", str(folder / "references"), str(hyp_csv)],
        check=True,
    )


def evaluate(label: str, model_id_or_path: str, processor: WhisperProcessor, eval_folders: list[Path],
             device: str, batch_size: int, out: Path) -> None:
    model = WhisperForConditionalGeneration.from_pretrained(model_id_or_path, torch_dtype=torch.float32)
    model.to(device)
    if device == "cuda":
        model.half()
    print(f"\n### Evaluating {label}")
    for folder in eval_folders:
        hyp = transcribe(model, processor, folder, device, batch_size,
                         out / "results" / f"{label.replace(' ', '-')}-{folder.name}.csv")
        score(f"{label}: {folder.name}", folder, hyp)
    del model
    if device == "cuda":
        torch.cuda.empty_cache()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", default="openai/whisper-small", help="base model (Hugging Face id)")
    parser.add_argument("--train", nargs="+", required=True, type=Path, help="training data folders")
    parser.add_argument("--eval", nargs="+", required=True, type=Path, help="held-out test folders")
    parser.add_argument("--out", required=True, type=Path, help="output folder for the model and results")
    parser.add_argument("--max-train-clips", type=int, default=0, help="use a random subset of N clips (0 = all)")
    parser.add_argument("--steps", type=int, default=1000, help="training steps")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--grad-accum", type=int, default=2)
    parser.add_argument("--lr", type=float, default=1e-5)
    parser.add_argument("--warmup", type=int, default=100)
    parser.add_argument("--save-steps", type=int, default=500)
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--skip-baseline", action="store_true", help="do not score the base model before training")
    args = parser.parse_args()

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Device: {device}")
    args.out.mkdir(parents=True, exist_ok=True)
    processor = WhisperProcessor.from_pretrained(args.model, language=LANGUAGE, task="transcribe")

    if not args.skip_baseline:
        evaluate("base model", args.model, processor, args.eval, device, args.batch_size, args.out)

    model = WhisperForConditionalGeneration.from_pretrained(args.model)
    model.generation_config.language = LANGUAGE
    model.generation_config.task = "transcribe"
    model.generation_config.forced_decoder_ids = None
    model.config.forced_decoder_ids = None
    model.config.use_cache = False  # required with gradient checkpointing; harmless otherwise

    dataset = SpeechDataset(args.train, processor, args.max_train_clips, args.seed)
    print(f"Training clips: {len(dataset)}")
    collator = SpeechCollator(processor=processor, decoder_start_token_id=model.config.decoder_start_token_id)

    training_args = Seq2SeqTrainingArguments(
        output_dir=str(args.out / "checkpoints"),
        max_steps=args.steps,
        per_device_train_batch_size=args.batch_size,
        gradient_accumulation_steps=args.grad_accum,
        learning_rate=args.lr,
        warmup_steps=args.warmup,
        fp16=device == "cuda",
        logging_steps=25,
        save_strategy="steps",
        save_steps=args.save_steps,
        save_total_limit=2,
        dataloader_num_workers=2,
        remove_unused_columns=False,
        label_names=["labels"],
        report_to=[],
        seed=args.seed,
    )
    trainer = Seq2SeqTrainer(model=model, args=training_args, train_dataset=dataset, data_collator=collator)
    trainer.train()

    model.config.use_cache = True  # faster generation when the model is loaded for evaluation
    final = args.out / "final"
    trainer.save_model(str(final))
    processor.save_pretrained(str(final))
    print(f"Saved fine-tuned model to {final}")

    evaluate("fine-tuned", str(final), processor, args.eval, device, args.batch_size, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
