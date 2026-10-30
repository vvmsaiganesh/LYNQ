# 3-minute demo script

Judges score what they can see, so the video is built around the **mesh graph and real hop
counts** — the part that is genuinely hard. Everything shown is live; nothing is mocked.

## Before recording (10 minutes)

1. Install the debug APK on three phones:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. On each phone: create an identity — **Asha**, **Bilal**, **Chen**.
3. Grant nearby permissions on all three. Turn **Bluetooth and Wi-Fi on**; there is no
   internet used (you may leave mobile data off to prove it).
4. Disable battery optimisation for Relay on all three (`Settings → Apps → Relay →
   Battery → Unrestricted`) so the relay service is not killed mid-shot.
5. Stage the phones: A and C at opposite ends of a table, B in the middle. Confirm on the
   radar that A sees B directly and C on the outer ring via B.
6. Start screen recording (built-in recorder or OBS). Lock auto-rotate and hide
   notifications.

## Shot list

| Time | Shot | Say |
|---|---|---|
| 0:00–0:15 | Home screen → app icon → onboarding | "When the network dies, this keeps talking. Relay is an offline mesh messenger built for Team FLUX." |
| 0:15–0:35 | Create identity, show node fingerprint + safety code | "Keys are generated on the phone and wrapped by the Android Keystore. The fingerprint is the identity; the safety code lets two people verify out of band." |
| 0:35–1:05 | Radar on A, cut to B and C | "Each phone advertises and discovers over Bluetooth and Wi-Fi Direct — no pairing dialogs, no server. A sees B directly; C is on the outer ring because B advertises it. That outer ring is the mesh." |
| 1:05–1:45 | A opens the thread with C, sends *"Water at the north gate"*; hold on the bubble as it changes | "Pending — no path yet. Sent to B. Relay receipt — B carried it. Delivered, two hops, and the path came from the ACK: you, B, C. Relays only ever saw ciphertext." |
| 1:45–2:10 | Cut to C, show the received bubble, then back to A | "C's device decrypted it with a key that never left the phone." |
| 2:10–2:40 | Hold-to-send SOS on A, show ring filling; cut to B and C receiving the alert with location | "Hold for three seconds. This floods to every node with a twelve-hop budget and jumps the queue. It is signed so it cannot be faked — and deliberately readable, because the audience is unknown and help matters more than secrecy." |
| 2:40–2:55 | Walk C out of range mid-send, send again, bring it back | "Out of range, it queues with its exact signed bytes. The moment C reappears, the stored message is replayed. Same after a reboot." |
| 2:55–3:00 | AI triage screen (optional insert) | "When there is internet, Featherless adds summaries, translation and an urgency score. The ranking you see works offline." |

## Lines to land

- "The radios are Google's; **the routing is ours**."
- "Delivery is not a checkmark — it is the receipt path the ACK travelled."
- "Prioritisation still works with the radio off, because the classifier is on the phone."
- "Airplane mode on, Wi-Fi off, SIM out: the mesh does not care. It needs the *radios*, not
  the network."

## Common failures and fixes

| Symptom | Fix |
|---|---|
| No peers appear | Bluetooth/Wi-Fi off, or the app was denied nearby permissions. Reinstall and re-grant. |
| Peers appear then vanish | Battery saver killed the service. Set Relay to Unrestricted; keep the app foreground during the shot. |
| Delivery stuck at SENT | The two phones were paired before the latest install. Toggle Bluetooth off/on and reopen the app. |
| Emulator shows nothing | Expected: Nearby Connections does not discover between emulators. Use physical phones. |
| SOS "queued" forever with no peers | Correct behaviour — it is stored and will flood when a node appears. |

## Editing notes

- Keep the hop-count change on screen for at least 3 seconds; it is the proof.
- Speed up (2×) the "walk out of range" segment; do not fake it.
- Add a lower-third with the requirement names as they are demonstrated
  (`#3 Mesh relaying`, `#6 Delivery status`, …) — helps rubric scoring.
