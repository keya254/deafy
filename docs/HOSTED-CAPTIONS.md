# Hosted captions: plan

Status: **planned, not built.** The app today is fully on-device. This page is the design for an optional hosted speech model. It is used when the phone has a network connection and the user has agreed to it. When the phone is offline, the on-device model is used, as it is now.

## Goal

Better captions when a connection is available, without giving up offline use.

- **Online (opt-in):** a larger speech model on a server gives better accuracy, especially for mixed Swahili and English and for noisy rooms.
- **Offline (default fallback):** the on-device Whisper model in [MODELS.md](MODELS.md) keeps working with no network at all.
- **Seamless:** the switch happens without the user doing anything, and captions keep appearing on the phone and the watch.

## Non-goals

- Replacing the on-device model. The offline path must always work on its own.
- Sending sound alerts to a server. Alerts (YAMNet) stay on the phone. Safety-relevant alerts must not depend on a connection.
- Storing audio or transcripts on the server beyond a short buffer.
- Accounts for basic use. A new user must be able to get captions with no sign-up.

## What changes for the user

| Situation | Captions come from | Shown as |
|-----------|-------------------|----------|
| No hosted mode (default) | On-device Whisper | "Offline captions" |
| Hosted mode on, network good | Hosted model | "Online captions" |
| Hosted mode on, network drops or server is slow | On-device Whisper, from the next phrase | "Offline captions" |

The watch shows the same label. Its message format does not change (see [PROTOCOL.md](PROTOCOL.md)), so the watch needs no changes for the switch. Only the phone decides.

## Privacy and consent

Today the app says audio is never sent over the network (see [PRIVACY.md](PRIVACY.md)). Hosted mode changes that, so the rules are strict:

1. **Off by default.** A setting, "Use online captions when available", turned on by the user. The first time, show a plain-language screen. It says what is sent, to whom, how long it is kept, and that alerts stay on the phone.
2. **Visible when active.** The phone notification and the caption screen show when audio is leaving the phone.
3. **Encrypted in transit.** TLS only. No plain HTTP.
4. **No audio storage.** Audio is decoded and discarded in memory. Logs must not contain audio, transcripts, or user identifiers. The retention policy is written down and tested.
5. **Minimal identity.** An anonymous install token, rotated, with rate limits. No names, phone numbers, or contacts.
6. **Clear provider terms.** If a third-party GPU host is used, check its data processing terms, data location, and whether it trains on inputs. Choose one that does not train on customer data.
7. **Data protection law.** The app's users are in Kenya. Check obligations under the Kenya Data Protection Act, 2019 (for example registration with the Office of the Data Protection Commissioner, a data processing agreement, and what counts as personal data for voice). Get legal advice before a public launch. This plan is not legal advice.
8. **Users can opt out at any time,** and turning it off must take effect without a restart.

## Architecture

```
Phone                                                       Hosted service (GPU)
Microphone ─► MicStream ─► (same audio) ─► HostedCaptionEngine ── TLS WebSocket ──► VAD ─► Whisper (large) ─► text
                 │                                 ▲                                    │
                 │                                 │ connection health                  │
                 │                                 │                                    │
                 └─► LocalSpeechEngine (standby) ◄─┘ fallback when hosted fails ◄───────┘
                 └─► SoundEngine (YAMNet, always on the phone)
```

### Phone

- **`HostedCaptionEngine : CaptionSource`.** A new engine next to `LocalSpeechEngine` and `CaptionEngine`. It uses the same `CaptionSource` interface, so `ListenService` can run it without other changes.
- **Shared audio.** `MicStream` already sends one stream to many listeners. The hosted engine is one more listener, so captions and sound alerts still share one microphone.
- **Audio format.** Send 16 kHz mono audio, compressed with Opus (about 20 to 30 kbit/s). Raw PCM at 16 kHz is about 256 kbit/s, which is too much on mobile data.
- **Router.** A small `CaptionRouter` decides which engine gets the phrase:
  - Start hosted if the mode is on, the network is validated, and the server answers a health check.
  - If the connection drops, or no result comes back within the timeout, send the buffered phrase to the on-device engine. Do not drop the phrase.
  - Try hosted again in the background with backoff (for example 5 s, then 30 s, then 2 min).
- **Standby model.** Keep the on-device model loaded while hosted mode is on. Loading it takes time, and the fallback must be instant.
- **Battery and data.** Stop the upload when captions are off. Measure data use per hour and show it in Settings.

### Service

- **Runtime:** Python, a streaming WebSocket endpoint, and faster-whisper (CTranslate2) with a Whisper model that is bigger than the on-device one. Candidates are large-v3 and large-v3-turbo. Choose by measured word error rate on the test set below, not by name. Community Swahili fine-tunes are worth testing too. Check each model's license before use.
- **Pipeline:** decode Opus, run voice activity detection (Silero VAD, the same as the phone), decode each phrase, and return `partial` and `final` text. Use the same phrase-based approach as the phone, so the watch sees the same kind of lines.
- **Endpoint:** `wss://<host>/v1/captions/stream`. The client sends a short `start` message (language mode, client version, install token), then audio frames. The server sends caption messages with `final`, `text`, and `ts`.
- **Health:** `GET /v1/health`, unauthenticated, returns the model name and version. The phone uses it to decide whether to start hosted mode.
- **Hosting:** a GPU instance that scales to zero (for example Cloud Run with a GPU, or a small GPU VM). Scale-to-zero is cheapest but adds a cold start of tens of seconds, so set a minimum of one warm instance during trials. Costs must be measured with real traffic before a public launch. Do not promise a number until then.
- **Region:** pick a region close to Kenya to keep latency low. Confirm the region meets the data location rules above.

### Authentication

- Use **bearer tokens**, the same kind of design the AI for Accessibility Toolkit server uses: an `aat_`-style token, stored only as a SHA-256 hash, with an expiry and a rate limit per token. See the toolkit's `server/README.md`.
- The app gets a token once, from `POST /v1/install` with a device-generated random ID, and stores it in encrypted preferences. Rotating it is a normal operation.
- The caption service is a separate service from the toolkit server. Speech inference has different scaling and privacy needs. Do not run it inside the toolkit's server.

## How the AI for Accessibility Toolkit fits

The toolkit is a research project in pre-alpha. Do not make the app depend on its server for captions. These parts are useful now:

| Toolkit part | Use in Open Caption |
|--------------|---------------------|
| `toolkit/surfaces/mobile.js` (ported as `AccessibilityProfile`) | Imports a person's stated needs (text size, captions on, and so on) and applies the ones the app supports. Done in this repo. |
| `tools/utils/ai.js` provider contract (`transcribeAudio`, return `null` when the model cannot answer) | The hosted engine can use the same "return nothing on failure, then fall back" rule. It also lets the same service serve the toolkit's browser features later. |
| `server/` bearer-token and deployment pattern | The model for the caption service's token scheme (see above). |
| `toolkit/core/ability.js` (AbilityModel) | The format of profile files users can import. |

Possible later step, only with explicit opt-in: store an imported profile on the toolkit server so it follows the user to other devices. Keep it out of this plan's first phases.

## Evaluation before any user sees it

1. **Build a test set.** Recorded, consented speech in Kenyan English, Kiswahili, and mixed speech, in quiet and noisy rooms. Label it by hand. Start small (for example 30 to 60 minutes) and publish the method, not the audio if consent does not allow it.
2. **Measure word error rate (WER)** for the on-device model and each hosted candidate, on the same set.
3. **Measure latency:** time from the end of a phrase to the final text, p50 and p95, on mobile data and on Wi-Fi.
4. **Measure fallback:** turn the network off mid-sentence and check that the phrase still appears, from the on-device model.
5. **Measure cost and data use** per hour of captions.

Pass criteria are set before the test, with the people who will use the app. Set them in this document before running the tests.

## Phases

| Phase | Work | Done when |
|-------|------|-----------|
| 0. Decide | Hosting provider, region, budget, owner for the service, legal review of the privacy rules | Written decisions added to this page |
| 1. Measure | Test set, WER and latency for the on-device model and two hosted candidates | A results table with numbers and the chosen model |
| 2. Service | Streaming endpoint, token auth, health check, logs with no audio, Docker image, deploy script | A staging endpoint passes the latency and privacy checks |
| 3. App client | `HostedCaptionEngine`, `CaptionRouter`, the consent screen, the status label, Settings toggle | Hosted mode works and falls back to offline on a simulated network drop |
| 4. Trial | Small group of Deaf users, with consent, for a few weeks | Feedback and incident review; decide on public release |
| 5. Launch | Published privacy policy, service terms, monitoring, cost limits | Public release with hosted mode off by default |

## Risks

- **Privacy trust.** One data leak would damage trust in the whole project. Keep audio out of logs, and keep the retention rule simple.
- **Cost.** A GPU service costs money every hour it is warm. Set a monthly spending cap and an alert before the trial.
- **Connection quality.** Mobile data in some areas is slow or unreliable. The fallback must be instant and tested.
- **Model licenses.** Some fine-tuned models have restrictive licenses. Check each one.
- **Single points of failure.** If the service is down, the app must still work fully offline. This is tested in phase 3.

## Open decisions for the project owner

1. Hosting provider and region.
2. Who pays for the service, and the monthly budget.
3. Whether the caption service is run by the project or by a partner organisation.
4. Whether trial users will be recruited through a Deaf organisation, and how consent is recorded.
5. Whether imported profiles should ever sync to a server (the toolkit's Librarian), or stay on the phone.
