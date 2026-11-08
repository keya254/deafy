# Phone-watch protocol

The phone and watch talk over the **Wearable Data Layer**, which uses Bluetooth when the devices are paired and in range. Both sides use the `MessageClient` API (small messages) and the `CapabilityClient` API (finding each other).

Every payload is a small UTF-8 JSON object. Classes that encode and decode them live in [`shared/.../Protocol.kt`](../shared/src/main/java/tech/briankeya/opencaption/shared/Protocol.kt). The unit tests in `shared/src/test` check that every message round-trips.

No audio is ever sent. Only text, sound names, and display settings cross the link.

## Discovery

| Capability | Advertised by | Used by |
|------------|---------------|---------|
| `opencaption_watch_app` | Watch (`wear/res/values/wear.xml`) | Phone, to find the watch |
| `opencaption_phone_app` | Phone (`phone/res/values/wear.xml`) | Watch, to find the phone |

The phone calls `CapabilityClient.getCapability(..., FILTER_REACHABLE)` and listens for changes. When a watch appears, the phone pushes its current text style.

## Message paths

| Path | Direction | Payload | Purpose |
|------|-----------|---------|---------|
| `/opencaption/caption` | phone → watch | Caption | A partial or final caption line, or a `[Sound]` tag |
| `/opencaption/alert` | phone → watch | Alert | A sound alert. The watch vibrates with `pattern`. |
| `/opencaption/status` | phone → watch | Status | Whether captions and sound alerts are on, and a short note |
| `/opencaption/style` | phone → watch | Style | Text size, fade time, and right-to-left flag |
| `/opencaption/history` | phone → watch | HistoryPage | One page of older captions, for scroll-back |
| `/opencaption/command` | watch → phone | Command | Start or stop a feature, or ask for captions |

## Payloads

### Caption

```json
{ "text": "habari yako", "final": true, "ts": 1700000000000 }
```

- `final: false` is a partial hypothesis that may change. The watch shows it in italics. (Partials are only produced by the Android fallback engine today.)
- `final: true` is a settled line. The watch adds it to the list.
- `ts` is the time in epoch milliseconds, used as the line's identity for history and catch-up.

### Alert

```json
{ "label": "Car horn", "confidence": 0.73, "ts": 1700000000000, "pattern": "URGENT" }
```

`pattern` is the name of a `VibePattern`: `GENTLE`, `SHORT`, `LONG`, `URGENT`, or `HEARTBEAT`. An unknown name falls back to `SHORT`.

### Status

```json
{ "captions": true, "sound": false, "note": "Listening" }
```

### Style

```json
{ "textScale": 1.2, "holdSeconds": 12, "rtl": false }
```

`holdSeconds` of 0 means "never fade". The phone sends style on connect and whenever the user changes it.

### History page

```json
{ "lines": [ { "t": "good morning", "ts": 1700000000000 } ], "hasMore": true }
```

Lines are oldest first. `hasMore` tells the watch whether to request an older page.

### Command

Watch → phone. The `command` field selects the action.

| Command | Extra fields | Effect on phone |
|---------|--------------|-----------------|
| `start_captions` | | Turn captions on |
| `stop_captions` | | Turn captions off |
| `start_sound` | | Turn sound alerts on |
| `stop_sound` | | Turn sound alerts off |
| `stop_all` | | Turn everything off |
| `sync` | `since` (epoch ms) | Re-send captions newer than `since` (catch-up after a gap) |
| `history` | `before` (epoch ms), `limit` | Send a `history` page of lines older than `before` |

## Behaviour to keep in mind

- **Delivery is best effort.** `MessageClient` does not guarantee order or delivery. The watch fills gaps with `sync` when it reconnects. Lines are de-duplicated by `ts`.
- **Keep payloads small.** Large messages can fail on Bluetooth. Send pages of at most 60 lines.
- **Optimistic toggles.** The watch flips its toggle at once and waits for a `status` message to confirm. If the phone reports the wrong state, the toggle snaps back. This is intended.
- **No version field.** Phone and watch must be built from the same commit. If you change a payload, update both apps in the same change, and consider adding a `v` field.

## Adding a message

1. Add a data class with `toBytes()` and `from(bytes)` to `Protocol.kt`, and a path constant.
2. Add a round-trip test to `shared/src/test/.../ProtocolTest.kt`.
3. Send it from the phone (`WatchLink.send`) and handle it in `PhoneDataService`, or the reverse for commands (`PhoneLink.sendCommand`, handled in `PhoneCommandService`).
4. Document it here.
