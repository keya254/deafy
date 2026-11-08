# Models

Open Caption runs several machine-learning models on the phone. Two of them ship in the app. The speech models are too large for the APK and are copied to the phone separately.

## Summary

| Model | Job | Shipped in app? | File(s) on phone | Approx. size | License |
|-------|-----|-----------------|------------------|--------------|---------|
| Whisper (multilingual, int8 ONNX) | Speech to text | No | `whisper-encoder.int8.onnx`, `whisper-decoder.int8.onnx`, `whisper-tokens.txt` | ~375 MB total | MIT (OpenAI) |
| Silero VAD | Finds where speech starts and stops | No | `silero_vad.onnx` | ~0.6 MB | MIT |
| GTCRN | Removes background noise before speech recognition (optional) | No | `denoiser.onnx` | ~0.5 MB | Check the upstream repository |
| YAMNet | Recognises 521 sounds (horns, sirens, doorbells, and more) | **Yes**: `phone/src/main/assets/yamnet.tflite` | (inside the APK) | ~3.9 MB | Apache-2.0 (Google) |

All models run on the phone CPU. Nothing is sent to a server.

## Where the speech models live on the phone

```
/sdcard/Android/data/tech.briankeya.opencaption/files/models/
    whisper-encoder.int8.onnx
    whisper-decoder.int8.onnx
    whisper-tokens.txt
    silero_vad.onnx
    denoiser.onnx          (optional)
```

The app looks for these names exactly. `scripts/install-models.sh` copies them there.

## Where to get the speech models

The files are published as pre-converted ONNX models for [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Look for:

- **Whisper** models, multilingual, int8-quantised. The `.tar.bz2` archives on the sherpa-onnx speech-recognition releases page contain the encoder, decoder, and tokens files. Rename them to the names above.
- **Silero VAD** (`silero_vad.onnx`), from the sherpa-onnx VAD models or the [Silero VAD repository](https://github.com/snakers4/silero-vad).
- **Speech enhancement** (GTCRN), from the sherpa-onnx speech-enhancement models. Optional.

Model names and release locations change over time, so follow the sherpa-onnx documentation if a link is missing.

The phone copy used for development is a multilingual Whisper model whose file sizes match Whisper **small** (encoder about 112 MB, decoder about 262 MB, int8). Confirm the exact variant from the model card before you publish a build that names it.

## Choosing a Whisper size

Whisper is available in several sizes. Each step up is more accurate and slower.

| Size | Speed on a mid-range phone | Swahili accuracy | Notes |
|------|----------------------------|------------------|-------|
| tiny | Very fast | Poor | Only for testing the pipeline |
| base | Fast | Fair | Good choice for older phones |
| small | Moderate | Better | Current development choice |
| medium and larger | Slow | Best | Usually too slow to keep up with live speech on a phone |

To change size, replace the three Whisper files. No code change is needed. Then check the decode times in the log (see [ARCHITECTURE.md](ARCHITECTURE.md#captions)). If the decode time is longer than the length of the phrase being decoded, captions will fall further behind as you speak.

## YAMNet and the sound labels

YAMNet is bundled as `phone/src/main/assets/yamnet.tflite`. It scores 521 AudioSet classes every half second. The label names used in the app must match the names in the model exactly (for example `Vehicle horn, car horn, honking`). The label table is in [`SoundLabels`](../shared/src/main/java/tech/briankeya/opencaption/shared/Protocol.kt).

To check that a name exists, search the model file for it:

```sh
strings -n 3 phone/src/main/assets/yamnet.tflite | grep -x "Ambulance (siren)"
```

## Licenses and attribution

Each model keeps its own license. When you redistribute a build, include the notices from each upstream project. The README lists the projects. If you replace a model, add it to the table above and check its license before you ship it.
