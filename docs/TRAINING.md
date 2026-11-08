# Training: Swahili Whisper on Kaggle

Status: **script written, not yet run.** The training code has not been executed on a GPU. A local test was not possible because the PyTorch download stalled. The first Kaggle run is the real test. Expect to fix small issues.

This page is the plan and steps to fine-tune the speech model on Common Voice Swahili, then add your recordings later. The goal is to measure whether Swahili accuracy improves against the baseline in [EVALUATION.md](EVALUATION.md) (79.7% WER on FLEURS `sw_ke`).

## Rules before you start

1. **Do not upload Common Voice to Kaggle as a dataset.** Mozilla forbids re-hosting or mirroring it, and a private Kaggle dataset still counts. Download it inside the notebook session, with your own Mozilla Data Collective (MDC) account, and keep the key in Kaggle Secrets.
2. **FLEURS is never training data.** The script refuses to train on any clip whose ID starts with `fleurs-`. FLEURS is the held-out test set.
3. **Your recordings are training data only if the speakers agreed to training.** The consent draft in [EVALUATION.md](EVALUATION.md) covers testing only. A training clause is needed first.
4. **Check the licence before publishing weights.** Common Voice is CC0, but Mozilla's terms apply to the dataset. Check them before you share a model trained on it.

## Quotas

Kaggle's free GPU is about 30 hours a week on two T4s, with sessions of up to 12 hours and about 20 GB of files in `/kaggle/working`. The full Common Voice Swahili archive is about 21 GB compressed, so do not extract all of it. Use `--max-train-clips` to take a random subset that fits, and measure the time per step first.

## Steps

### 1. Create the notebook

- New notebook on Kaggle, accelerator **GPU T4 x2**, internet **on** (needed for model and data downloads).
- Add a Kaggle Secret named `MDC_API_KEY` with your Mozilla Data Collective key.
- Upload this repo's `eval/` and `training/` folders, or clone the repo in the notebook.

### 2. Install

```sh
pip install -q transformers accelerate soundfile
```

Kaggle already has PyTorch and ffmpeg. Check with `torch.cuda.is_available()` before training.

### 3. Get the Common Voice Swahili training split

Download the Scripted Speech 27.0 Swahili archive from MDC inside the session, extract it under `/tmp`, then prepare only the train split:

```sh
python3 eval/prepare_common_voice.py --dir /tmp/cv-sw --tsv train.tsv --n 0 --out /kaggle/working/cv-sw-train
```

`--n 0` writes every validated clip in the split. Trim it later with `--max-train-clips` in the training step, not here, so the choice is recorded in one place.

The MDC download call is not verified here. Check the MDC documentation for the current API or SDK before writing it into the notebook.

### 4. Get the test sets

```sh
python3 eval/prepare_common_voice.py --dir /tmp/cv-sw --tsv test.tsv --n 200 --out /kaggle/working/cv-sw-test
```

Download FLEURS `sw_ke` into `/kaggle/working/fleurs-sw_ke/` as described in [EVALUATION.md](EVALUATION.md), then prepare it:

```sh
python3 eval/prepare_fleurs.py --dir /kaggle/working/fleurs-sw_ke
```

### 5. Train and score

```sh
python3 training/finetune_whisper.py \
    --train /kaggle/working/cv-sw-train \
    --eval /kaggle/working/cv-sw-test /kaggle/working/fleurs-sw_ke \
    --max-train-clips 20000 --steps 1500 \
    --batch-size 8 --grad-accum 2 --lr 1e-5 \
    --out /kaggle/working/run1
```

The script scores the base model first, then trains, then scores the fine-tuned model on the same test sets. Results are in `/kaggle/working/run1/results/`, and the WER tables are printed in the log.

Start with a short run (`--steps 100 --max-train-clips 2000`) to check that every stage works and to measure time per step. Then size the full run to fit within one session.

### 6. Bring the results back

Copy the results folder (not the audio) back to this repo as `eval/results/run1-*.csv`, then score with `eval/wer.py`. Record the run in [EVALUATION.md](EVALUATION.md) with the settings and the date.

## Adding your recordings later

Prepare your own clips into the same folder layout (`manifest.csv`, `audio/`, `references/`), with `clip_id` values that do not start with `fleurs-`. Add that folder to `--train` in a later run, and keep it in a separate folder from the Common Voice data so the source stays clear. Only add it once the consent covers training.

## After training

- The fine-tuned model is in the `final/` folder, in Hugging Face format. The app needs a sherpa-onnx int8 export to run it on the phone. That conversion has not been set up yet.
- Compare the fine-tuned model with the base model on FLEURS `sw_ke` and on the Common Voice test split, using the same `wer.py` scoring.
- Before any model is shared outside this project, check the licence of the base model and the terms of the training data.
