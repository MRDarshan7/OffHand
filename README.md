# OFFHAND

An offline-first, on-device voice assistant for Android.

```
push-to-talk voice → on-device ASR (Vosk)
  → on-device LLM parse (llama.cpp · Qwen2.5-1.5B · GBNF grammar-constrained JSON)
  → independent validation → editable confirmation card
  → durable outbox (Room) → execute now if online / queue if offline
  → auto-dispatch when connectivity returns (WorkManager)
```

Offline is a first-class state, never an error. Nothing irreversible executes
without a human tap. The reasoning path (ASR → parse → validate → draft →
queue) makes zero network calls; only delivery (SMTP, laptop bridge) touches
a network.

## The six actions

`send_email` · `create_event` · `set_reminder` · `fetch_laptop_file` ·
`get_laptop_clipboard` · `capture_note`

Anything out of scope becomes a `capture_note` with the transcript as body —
the safe default. Unresolvable fields become amber "check this" flags on the
confirmation card, never guesses.

## Product decision log

- **2026-08-26 — The LLM is the product (reversal of 2026-08-25).** This is
  an on-device-AI project; M1 is implemented as originally locked: llama.cpp
  via JNI (pinned tag b4658, vendored at `third_party/llama.cpp`, not
  committed), Qwen2.5-1.5B-Instruct Q4_K_M GGUF loaded from the app's
  external files dir, GBNF grammar (`app/src/main/assets/action_schema.gbnf`)
  forcing schema-valid JSON, behind the `ActionParser` interface. The
  deterministic parser remains as the runtime fallback ONLY (native lib or
  model missing / invalid output) and every fallback is logged and shown in
  the UI status line — never silent. Native build targets arm64-v8a only,
  so the APK no longer installs on x86 emulators.
- **2026-08-25 — No on-device LLM.** *(Superseded above.)*
- **2026-08-26 — Device verification PENDING.** No physical phone was
  available and the machine cannot run an emulator (no hypervisor; 8 GB RAM).
  All on-device milestone acceptance waits for real hardware — see the
  checklist below. Off-device: 61 JVM tests, PC-side ASR eval, and the LLM
  parser eval (same GGUF + grammar + prompt as the phone, x64 llama-cli).
- **Email delivery is verified against a local SMTP sink** (no credentials
  were available). Gmail is a config swap in `local.properties`
  (see below) and remains UNVERIFIED against a live account.

## Setup (10 steps)

1. Install JDK 17 and the Android SDK (this machine: `E:\jdk-17\...`,
   `E:\Android\Sdk` — see `local.properties`).
2. Clone this repo; `local.properties` needs `sdk.dir=<your sdk>`.
3. Download models to `E:\offhand-models\`: `vosk-model-small-en-us-0.15`
   (alphacephei.com/vosk/models, unzip) and
   `qwen2.5-1.5b-instruct-q4_k_m.gguf`
   (huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF). Vendor llama.cpp:
   `git clone --depth 1 --branch b4658 https://github.com/ggml-org/llama.cpp third_party/llama.cpp`
   (NDK 27.2 + CMake 3.22 must be installed in the SDK).
4. Build: `.\gradlew.bat assembleDebug`
   (with `JAVA_HOME` set to the JDK 17 path).
5. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
6. Launch the app once (creates its storage dirs), then push both models:
   `adb push E:\offhand-models\vosk-model-small-en-us-0.15 /sdcard/Android/data/com.offhand/files/vosk-model-small-en-us-0.15`
   `adb push E:\offhand-models\qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Android/data/com.offhand/files/qwen2.5-1.5b-instruct-q4_k_m.gguf`
7. Start the SMTP sink on the PC: `py tools\smtp_sink\smtp_sink.py 2525`
   (or point `local.properties` at a real SMTP account — see below).
8. Start the laptop bridge: `py bridge-daemon\bridge_daemon.py E:\offhand-shared 8787`
9. On a real phone, set `offhand.bridge.host` / `offhand.smtp.host` in
   `local.properties` to the laptop's LAN IP and rebuild. On the emulator the
   defaults (`10.0.2.2`) already point at the host.
10. Hold the button and speak. Long-press the OFFHAND title to reset demo data.

### local.properties keys (all optional, git-ignored)

```
offhand.smtp.host=smtp.gmail.com      # default 10.0.2.2 (dev sink)
offhand.smtp.port=587                 # default 2525
offhand.smtp.user=you@gmail.com       # default empty (no auth)
offhand.smtp.pass=<app password>      # NEVER commit; default empty
offhand.smtp.from=you@gmail.com
offhand.smtp.starttls=true            # default false
offhand.bridge.host=192.168.x.x       # default 10.0.2.2
offhand.bridge.port=8787
```

## Manual verification checklist

- [ ] **M0:** Online/"Working offline" banner flips when airplane mode toggles.
- [ ] **M2:** voice (or `DEBUG_WAV`) → correct editable draft in < 4 s.
- [ ] **M3:** queue an email offline, force-stop the app, reboot — item is
      still QUEUED with correct content.
- [ ] **M4:** airplane on → queue two emails → airplane off → both arrive
      within 60 s, exactly once each; three consecutive runs.
- [ ] **M6:** five consecutive end-to-end runs without failure; network audit
      (airplane mode + full reasoning path = zero connection attempts).

## Testing

- `.\gradlew.bat :app:testDebugUnitTest` — 61 JVM tests: parser eval harness
  (26 transcripts incl. real ASR degradations), DateResolver, ContactResolver,
  Validator, state machine.
- `tools\asr_eval` — PC-side Vosk check over Windows-TTS WAVs.
- Debug-only injection hooks (absent in release):
  `adb shell am broadcast -a com.offhand.DEBUG_TRANSCRIPT --es text "..." com.offhand`
  (also `DEBUG_WAV`, `DEBUG_OCR` with `--es path`).

## Security & privacy

Secrets only in `local.properties` → BuildConfig. Logs carry action types,
states and timings — never bodies, recipients, transcripts or audio. The
bridge daemon is a GET-only dev daemon serving one whitelisted folder with
traversal rejected. No analytics, no crash reporters.

## Repository layout

```
app/src/main/java/com/offhand/
  audio/     Vosk ASR engine (init once, streaming partials, WAV debug path)
  parse/     ActionParser · DeterministicActionParser · DateResolver ·
             ContactResolver · Validator  (pure Kotlin, fully JVM-tested)
  action/    ActionRepository (state machine) · executors · coordinator
  data/      Room: actions, contacts (seeded, editable), notes
  dispatch/  ConnectivityObserver · Dispatcher · DispatchWorker
  bridge/    LaptopBridge interface + HTTP client (Office Kit stays UNKNOWN)
  ocr/       ML Kit text recognition (bundled, offline)
  ui/        Home · Confirmation · Outbox · contacts dialog · OCR capture
  demo/      DemoSeeder (long-press reset)
bridge-daemon/    Python dev daemon (GET-only, whitelisted folder)
tools/parser_eval tools/asr_eval tools/smtp_sink tools/verify
```
