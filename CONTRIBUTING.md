# Contributing to Open Caption

Thank you for helping. This project exists so that Deaf and hard-of-hearing people can follow conversations and hear important sounds. Changes are reviewed with that in mind.

## Ground rules

- **Be kind.** Assume good faith and write for people who may be new to the code.
- **Protect privacy.** Audio must never be written to disk, uploaded, or logged. Do not add analytics or a network call without discussing it first.
- **Do not weaken alerts quietly.** Changes that lower sensitivity, add cooldowns, or hide alerts must say so in the pull request and explain why.
- **Keep the phone and watch in step.** If you change a message, update both apps in the same pull request. See [docs/PROTOCOL.md](docs/PROTOCOL.md).
- **Mark defaults that change.** If a saved setting's default changes, version its key as described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#settings-and-migrations).

## Getting started

Follow [docs/SETUP.md](docs/SETUP.md). Then run the tests:

```sh
./gradlew :shared:testDebugUnitTest
```

## Making a change

1. Open an issue first for anything bigger than a small fix, so we can agree on the approach.
2. Create a branch from `main`, for example `fix/ambulance-threshold`.
3. Keep each pull request focused on one change.
4. Run the tests and build both apps:
   ```sh
   ./gradlew :shared:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug
   ```
5. If you change how captions or alerts behave, test on a real phone and watch, and say what you tested in the pull request. Include decode times or detection results where you can.
6. Update the docs that your change affects.

## Code style

- Kotlin, following the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html).
- Use Compose for UI, and keep engine code (`LocalSpeechEngine`, `SoundEngine`, `MicStream`) free of UI types.
- Comments explain *why*, not *what*. Public types and anything non-obvious about threading or timing need a short comment.
- Strings shown to users should be short, plain, and easy to understand. Avoid jargon.

## Accessibility expectations

- Text must remain readable at the largest system font size.
- Every control needs a label that a screen reader can announce.
- Do not rely on sound alone for information. Pair alerts with vibration and visible text, as the app already does.

## Reporting security issues

Please email the maintainer privately at brianosiemokeya@gmail.com rather than opening a public issue.

## Contact

Brian Keya: [briankeya.tech](https://briankeya.tech) · brianosiemokeya@gmail.com
