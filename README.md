# OFFHAND

An offline-first, on-device voice assistant for Android. Push-to-talk voice →
on-device speech recognition (Vosk) → **deterministic parser** → independent
validation → editable confirmation card → durable outbox (Room) → execute now
if online / queue if offline → auto-dispatch when connectivity returns
(WorkManager).

## Product decision log

- **2026-08-25 — No on-device LLM.** The target phone cannot run local LLMs,
  so the deterministic keyword/regex parser (the locked fallback in the build
  spec) *is* the product. The `ActionParser` interface is kept so an LLM
  implementation could be slotted in later. The product claim is
  "rule-parsed", not "LLM-parsed". Consequence: no llama.cpp, no NDK, no GGUF.

## The six actions

`send_email` · `create_event` · `set_reminder` · `fetch_laptop_file` ·
`get_laptop_clipboard` · `capture_note`

Anything else becomes a `capture_note` with the transcript as the body — the
safe default. Nothing irreversible executes without a human tap on the
confirmation card. Offline is a first-class state, never an error.

## Toolchain (this machine)

Everything lives on `E:` — the C: drive is deliberately untouched:

| Thing | Path |
|---|---|
| JDK 17 (Temurin) | `E:\jdk-17\jdk-17.0.20.1+1` |
| Android SDK (cmdline-tools, platform 35, build-tools 35) | `E:\Android\Sdk` |
| Gradle 8.11.1 distribution | `E:\gradle\gradle-8.11.1` |
| Gradle caches (`GRADLE_USER_HOME`) | `E:\gradle-home` |
| Vosk model (small en-us 0.15) | `E:\offhand-models` |

## Build & install

```powershell
# from the repo root
$env:JAVA_HOME = "E:\jdk-17\jdk-17.0.20.1+1"
$env:GRADLE_USER_HOME = "E:\gradle-home"
.\gradlew.bat assembleDebug
E:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

## Manual verification checklist

- [ ] **M0:** Online/Offline banner flips live when airplane mode toggles.
- [ ] **M3:** queue an action, force-stop the app, reboot — item still QUEUED.
- [ ] **M4:** airplane mode on → queue two emails → airplane mode off → both
      arrive within 60 s, exactly once each. Three consecutive runs.
- [ ] **M6:** full end-to-end flow five times consecutively without failure;
      network audit — zero connection attempts on the reasoning path.

## Milestones

M0 skeleton · M1 deterministic parser + validator + eval harness (JVM) ·
M2 voice → validated draft · M3 confirmation + durable queue ·
M4 offline → online dispatch · M5 laptop bridge / OCR / outbox polish ·
M6 hardening.
