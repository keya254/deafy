# Caption accuracy evaluation

This page is the protocol for measuring caption accuracy. It is phase 1 of [HOSTED-CAPTIONS.md](HOSTED-CAPTIONS.md), and it also measures the on-device model. Nothing here has been run yet.

Status: **draft.** The pass criteria are proposals. They must be agreed with Deaf and hard-of-hearing users before any test is run, and then recorded in this file with the date.

## Principles

1. **Consent first.** Only record people who have agreed in writing (see the consent section). Do not use recordings from public sources.
2. **Audio stays out of git.** Recordings live in `eval/audio/`, which is git-ignored. Only the manifest, the transcripts, and the results are committed, and only if the consent allows it.
3. **Same test for every engine.** Each engine gets the same clips, in the same order, with the same settings.
4. **Measure what users feel.** Report word error rate (WER) by language and environment, plus latency. An average alone hides the cases that matter.

## Open test sets

Your own recordings are the most important test, because they match real use. Open data can run alongside them, to give a baseline and to test mixed speech without waiting for consent.

| Set | Languages and content | Size | Licence | Use in this project |
|-----|----------------------|------|---------|---------------------|
| **Mozilla Common Voice, Scripted Speech 27.0, Swahili** | Kiswahili read sentences, clean studio-like recordings, from 1,525 speakers | About 392 h validated | CC0. Mozilla forbids re-hosting or mirroring, and access is through the Mozilla Data Collective | **Baseline. Adopted now** (see below) |
| **Google FLEURS, `sw_ke` and `en_us`** | Read sentences in Kenyan Swahili and English. The same sentences are in both languages, so it compares them directly | Not checked | CC-BY-4.0. Give credit | Recommended second test set for clean Swahili and English |
| **AfriSwitch (intronhealth)** | Swahili and English code-switching from YouTube and podcasts, with English spans tagged. Test split only | 650 utterances, 3.89 h | CC BY-NC-SA 4.0. Non-commercial, share-alike. Do not redistribute | Best match for mixed speech. Use for internal evaluation only, and only if the owner accepts the licence |
| **AfriSpeech-200 (Kenyan English subset)** | Accented English from 137 Kenyan speakers, clinical and general speech | 8,304 clips, 20.89 h | CC BY-NC-SA 4.0. Gated on Hugging Face. Non-commercial | Kenyan-accented English. Same licence caveat as AfriSwitch |
| **Liva AI Swahili-ASR** | Conversational Swahili, with Sheng and 5 to 10% English code-switching | About 1,400 h, train split only | CC-BY-4.0 | Would need a held-out part carved out. Good for conversational speech |

The licence rules matter for this project, which is MIT-licensed and free. The non-commercial sets can still be used to measure accuracy, as long as their data is not redistributed. Decide with the project owner before any results are published.

### Baseline: Common Voice Swahili

Run `eval/prepare_common_voice.py` on a Swahili Scripted Speech folder you have downloaded from the Mozilla Data Collective. It samples validated clips from the test split and writes them to `eval/cv/` (git-ignored), with references and a manifest. Score any engine against it with:

```sh
python3 eval/prepare_common_voice.py --dir /path/to/cv-swahili --n 200
python3 eval/wer.py --manifest eval/cv/manifest.csv --references eval/cv/references results.csv
```

Read speech is cleaner than real use, so the baseline shows how well an engine does on clear Swahili. It does not show how it does in a matatu. Keep both, and report them separately.

## What to record

Recordings should match real use: a phone held at a normal distance, in the rooms people actually use.

| Dimension | Levels |
|-----------|--------|
| Language | English (Kenya), Kiswahili (Kenya), mixed Swahili and English in one sentence |
| Environment | Quiet room; noisy (street, market, matatu, or a room with a fan or TV) |
| Distance | Phone on a table, about 1 m; phone in a bag or pocket, about 1 m away |
| Speaker | At least 6 speakers, mixed in gender and age. Include speakers who are Deaf and use speech, if they wish to take part |
| Speech style | Read sentences; and free conversation, which is closer to real use |

Minimum size for a first pass: 30 minutes of audio, in clips of 5 to 20 seconds, spread across the table above. Aim for at least 10 minutes in each language. A larger set can follow once the first pass is useful.

Record at 16 kHz mono WAV, the format the models expect. Name each file with an ID: `clip-0001.wav`.

## Consent

Each speaker signs (or, if preferred, gives recorded verbal consent) before recording. The consent covers:

- what is recorded and why (to test caption accuracy for this app),
- that the recording may be used to measure the on-device and hosted models,
- whether the audio may be shared publicly. Each speaker chooses separately:
  - audio shared publicly, or
  - only transcripts and metrics shared, or
- that they can withdraw, and the clips will be deleted within a fixed period (for example 30 days),
- contact details of the project owner.

Keep consent records separate from the manifest. The manifest refers to speakers by a random `speaker_id` only.

## Transcripts (references)

- Transcribe each clip by hand, word for word, in the language that was spoken. Mixed speech is written as spoken; do not translate.
- Use lowercase, no punctuation. Numbers are written as words as spoken.
- A second person checks 20% of the clips. Disagreements are resolved by discussion, and the rule is written down.
- Mark unclear words as `[unclear]`. The marker is removed from the reference, so any words an engine produces there count as insertions. Keep `[unclear]` rare, and only use it when a human genuinely cannot tell.
- Non-speech sounds are not part of the reference, because the caption engine does not transcribe them as words.

## Engines to compare

1. On-device Whisper (the model in [MODELS.md](MODELS.md), as installed).
2. Each hosted candidate, once the service exists. Before that, use the candidates' public demos or local runs, and record the model name and version.
3. Android's built-in recognizer, as a baseline for the current fallback.

Record the exact model names and versions, the decode settings, and the device used.

## Metrics

- **WER** per engine, per language, and per environment. WER = (substitutions + deletions + insertions) / words in the reference. Computed by `eval/wer.py`.
- **Latency:** time from the end of a clip's speech to the first final text, reported as p50 and p95. Measured on the phone, not on the server alone.
- **Fallback:** for hosted mode, the share of clips that were answered by the on-device model because the network failed. Tested by turning the network off during a clip.
- **Data use** per hour of captions, for the hosted model only.

## Pass criteria (proposed, to be agreed)

These are starting points, not decisions. They are agreed with users and then fixed here before any test is run.

| Measure | Proposed threshold | Agreed? |
|---------|--------------------|---------|
| WER, quiet room, English | Lower than on-device by at least 5 points | No |
| WER, quiet room, Kiswahili | Lower than on-device by at least 5 points | No |
| WER, noisy room, mixed | Lower than on-device by at least 5 points | No |
| Latency p95, hosted, Wi-Fi | Under 3 s from end of speech to final text | No |
| Latency p95, hosted, mobile data | Under 5 s | No |
| Fallback | Every clip gets text when the network drops mid-clip | No |

## Process

1. Agree the pass criteria with users. Record the decision and date in this file.
2. Collect consent and record the clips. Keep the manifest up to date.
3. Transcribe and check the references.
4. Run each engine on the same clips, and save the hypotheses to a results file.
5. Run `python3 eval/wer.py` to compute the metrics.
6. Write the results table below, with the date, the engine versions, and the test device.
7. Decide which model goes into phase 2, and say why.

## Results

_Not run yet._

## Files

| File | Purpose | Committed? |
|------|---------|------------|
| `eval/manifest.csv` | One row per clip: ID, speaker, language, environment, duration, consent reference | Yes, without personal details |
| `eval/references/*.txt` | Hand transcripts, one per clip | Only if consent allows |
| `eval/results/<engine>.csv` | Hypothesis text per clip, per engine | Yes |
| `eval/audio/` | The recordings | **No** (git-ignored) |
| `eval/wer.py` | Computes WER and the breakdowns | Yes |
| `eval/prepare_common_voice.py` | Samples Common Voice clips into `eval/cv/` | Yes |
| `eval/cv/` | Common Voice audio, references, and manifest | **No** (git-ignored, Mozilla terms) |
