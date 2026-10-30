## What changed

<!-- One or two sentences. What behaviour is different now? -->

## Area

- [ ] mesh / routing
- [ ] transport (Bluetooth / Wi-Fi Direct)
- [ ] crypto
- [ ] data / storage
- [ ] UI
- [ ] AI assistant
- [ ] docs / demo
- [ ] build / tooling

## Shared files touched

<!-- Packet.kt, Transport.kt, Message.kt, ServiceLocator.kt, AndroidManifest.xml, build.gradle.kts
     Leave empty if none. Anything here needs a heads-up in the group chat. -->

- none

## Checks

- [ ] `./gradlew :app:assembleDebug` passes
- [ ] `./gradlew :app:testDebugUnitTest` passes
- [ ] No `local.properties`, `build/`, `.idea/`, `*.apk`, keys, or tokens in the diff
- [ ] Rebased on latest `main`
- [ ] Tested on a device or emulator (say what: <!-- Pixel 7 / emulator API 37 -->)

## Multi-hop impact

<!-- Does this change the packet format, hop limit, dedup, or ACK behaviour?
     If yes: old builds will not interoperate. Say so here. -->

- [ ] No change to the wire protocol
- [ ] Wire protocol changed — peers must update together

## Screenshots / logs

<!-- For UI changes: before and after. For routing: the logcat lines. -->
