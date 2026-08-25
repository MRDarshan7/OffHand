# OFFHAND

**An offline-first, on-device AI voice assistant for Android.** Speak a
command; a local LLM turns it into a structured action; you confirm it on an
editable card; it executes now if possible and queues durably if not. No
cloud, no account, no analytics — the AI runs entirely on the phone.

```
push-to-talk voice
  → on-device ASR            (Vosk, streaming, 16 kHz)
  → on-device LLM parse      (llama.cpp · Qwen2.5-1.5B-Instruct Q4_K_M ·
                              GBNF grammar-constrained JSON — output is
                              schema-valid by construction)
  → independent validation   (contacts, dates, business rules — in code)
  → editable confirmation    (nothing irreversible without a human tap)
  → durable outbox           (Room + state machine)
  → execute / queue          (offline actions run instantly; network
                              actions queue and auto-send on reconnect
                              via WorkManager)
```

**Offline is a first-class state, never an error.** The reasoning path
(ASR → parse → validate → draft → queue) makes zero network calls; only
delivery (SMTP, laptop bridge) touches a network. While offline the app says
*"Queued — will send when connected"* and means it.

## The six actions

| Action | What it does | Key slots |
|---|---|---|
| `send_email` | Email a contact via SMTP | recipient, subject, body |
| `create_event` | Insert into the device calendar (offline) | title, datetime |
| `set_reminder` | Exact alarm + local notification (offline) | body, datetime |
| `fetch_laptop_file` | Pull a file from the laptop's shared folder | path hint |
| `get_laptop_clipboard` | Grab the laptop clipboard into a note | — |
| `capture_note` | Save a local note (offline) | body |

There is no seventh action. Anything out of scope becomes a `capture_note`
with the transcript as body — the safe default. The LLM's output is a
*draft*, never an execution; an independent validator re-resolves contacts
(fuzzy match against the in-app editable contact list) and dates (natural
language → real timestamps, **in code** — the model's text is a hint, the
code's output is the truth). Unresolvable fields get amber "check this"
flags on the confirmation card, never guesses. If the LLM is unavailable, a
deterministic rule parser takes over — visibly (status line on the Home
screen), never silently.

## Tech stack

| Concern | Choice |
|---|---|
| Platform | Native Android, Kotlin + Jetpack Compose, single module, minSdk 28 |
| LLM runtime | llama.cpp via JNI (pinned tag **b4658**), CPU, grammar-constrained greedy decoding |
| Model | Qwen2.5-1.5B-Instruct, Q4_K_M GGUF (~1.0 GB, pushed via adb, never bundled) |
| ASR | Vosk `vosk-model-small-en-us-0.15` (~40 MB) |
| OCR | CameraX + ML Kit text recognition (bundled model, offline) |
| Persistence | Room + kotlinx.serialization (strict) |
| Dispatch | WorkManager (unique work per action UUID, network constraint, backoff) + connectivity-callback immediate drain |
| Email | SMTP (JavaMail Android). Dev default: a local Python SMTP sink. Gmail = config change |
| Laptop bridge | Own Python daemon (GET-only, one whitelisted folder) + OkHttp client behind a `LaptopBridge` interface |
| DI / architecture | Plain constructor injection (`AppContainer`), MVVM-lite: ViewModel + StateFlow |

## Project status

**Everything below is built, committed, and verified off-device.** On-device
verification is **pending** (no physical phone was available during the
build; the dev machine cannot run an emulator).

Verified off-device:
- 61 JVM unit tests green: parser eval harness (26 transcripts covering all
  six actions, ambiguity, junk, and real ASR mishearings), DateResolver,
  ContactResolver, Validator, action state machine.
- LLM parser eval against the **real model** (same GGUF + grammar + prompt
  as the phone, via x64 llama-cli at the same pinned tag): sweep #1 23/26 →
  prompt iteration → sweep #2 25/26 → iteration → targeted re-passes 3/3 and
  4/4. **Zero schema violations and zero illegal actions across every run**
  — the grammar held unconditionally. Full history: `tools/llm_eval/RESULTS.md`.
- Vosk ASR accuracy measured on PC over synthesized speech; mishearings
  provably degrade to the safe note fallback, never a wrong action.
- The arm64 native library (`liboffhand_llama.so`) compiles and packages
  into the APK. **Caveat:** the JNI wrapper itself has never executed — its
  first run happens on a real phone.

## Requirements

- **Phone:** arm64 Android 9+ (minSdk 28) with ~2 GB free RAM for the LLM.
  The APK is arm64-only — it will not install on x86 emulators.
- **Dev machine:** JDK 17, Android SDK (platform 35, build-tools 35,
  NDK 27.2, CMake 3.22), Python 3 for the dev daemons, ~8 GB disk for
  toolchain + models.

## Setup

1. Clone this repo, then vendor llama.cpp at the pinned tag (not committed):
   ```
   git clone --depth 1 --branch b4658 https://github.com/ggml-org/llama.cpp third_party/llama.cpp
   ```
2. Point `local.properties` at your SDK: `sdk.dir=<path-to-Android-Sdk>`.
3. Download the two models (kept outside the repo; on this machine they live
   in `E:\offhand-models\`):
   - `vosk-model-small-en-us-0.15` from alphacephei.com/vosk/models (unzip)
   - `qwen2.5-1.5b-instruct-q4_k_m.gguf` from
     huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
4. Build (JDK 17 on `JAVA_HOME`):
   ```
   .\gradlew.bat assembleDebug
   ```
5. Install on the phone (USB debugging enabled):
   ```
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```
6. Launch the app once (creates its storage dirs and shows permission
   prompts — grant mic, calendar, notifications, camera), then push both
   models and restart the app:
   ```
   adb push <models>\vosk-model-small-en-us-0.15 /sdcard/Android/data/com.offhand/files/vosk-model-small-en-us-0.15
   adb push <models>\qwen2.5-1.5b-instruct-q4_k_m.gguf /sdcard/Android/data/com.offhand/files/qwen2.5-1.5b-instruct-q4_k_m.gguf
   ```
7. On the PC, start the delivery endpoints (both are dev-only, LAN-only):
   ```
   py tools\smtp_sink\smtp_sink.py 2525
   py bridge-daemon\bridge_daemon.py <shared-folder> 8787
   ```
   Allow Python through Windows Firewall (private networks) when prompted.
8. Tell the phone where the laptop is: put your laptop's LAN IP into
   `local.properties` (see keys below) and rebuild + reinstall.
9. Phone and laptop must be on the same Wi-Fi network.
10. Hold the button and speak — e.g. *"remind me to submit the lab record
    tomorrow morning"*. Long-press the OFFHAND title to reset demo data.

### Configuration (`local.properties`, git-ignored)

```
sdk.dir=E\:\\Android\\Sdk               # your SDK path
offhand.smtp.host=192.168.x.x           # default 10.0.2.2; laptop LAN IP,
                                        # or smtp.gmail.com for real mail
offhand.smtp.port=587                   # default 2525 (dev sink)
offhand.smtp.user=you@gmail.com         # default empty (sink needs no auth)
offhand.smtp.pass=<app password>        # NEVER commit this
offhand.smtp.from=you@gmail.com
offhand.smtp.starttls=true              # default false
offhand.bridge.host=192.168.x.x         # default 10.0.2.2
offhand.bridge.port=8787
```

Switching from the dev sink to Gmail is exactly this config change — no code
changes. Gmail delivery has not yet been verified against a live account.

## Testing

- **Unit tests (61):** `.\gradlew.bat :app:testDebugUnitTest`
- **LLM parser eval** (real model, PC): `py tools\llm_eval\run_eval.py`
  — needs the GGUF plus llama-cli b4658 binaries; paths at the top of the
  script. Results record: `tools/llm_eval/RESULTS.md`.
- **ASR eval** (PC): `tools\asr_eval\` — synthesizes speech with Windows TTS
  and transcribes it with the same Vosk model the phone uses.
- **Debug injection hooks** (debug builds only, absent from release):
  ```
  adb shell am broadcast -a com.offhand.DEBUG_TRANSCRIPT --es text "note down hello" com.offhand
  adb shell am broadcast -a com.offhand.DEBUG_WAV --es path /sdcard/... com.offhand
  adb shell am broadcast -a com.offhand.DEBUG_OCR --es path /sdcard/... com.offhand
  ```
  They feed the production pipeline downstream of the microphone/camera.

## Security & privacy

- Logs carry action types, states, token counts and timings — never bodies,
  recipients, transcripts, or audio.
- The bridge daemon is a development daemon: plain HTTP, GET-only, serves
  exactly one whitelisted folder, rejects path traversal. Trusted LAN only.
- No analytics, no crash reporters, no cloud backend of any kind.

## Repository layout

```
app/src/main/java/com/offhand/
  llm/       LlamaEngine (JNI) · LlamaActionParser · CompositeActionParser
  audio/     Vosk ASR engine (init once, streaming partials, WAV debug path)
  parse/     ActionParser · deterministic fallback parser · DateResolver ·
             ContactResolver · Validator   (pure Kotlin, fully JVM-tested)
  action/    ActionRepository (state machine) · executors · coordinator
  data/      Room: actions, contacts (seeded, editable), notes
  dispatch/  ConnectivityObserver · Dispatcher · DispatchWorker
  bridge/    LaptopBridge interface + HTTP client
  ocr/       ML Kit text recognition (bundled, offline)
  ui/        Home · Confirmation · Outbox · contacts dialog · OCR capture
  demo/      DemoSeeder (long-press reset)
app/src/main/cpp/       JNI wrapper + CMake build (vendors third_party/llama.cpp)
app/src/main/assets/    action_schema.gbnf · parser_prompt.txt
bridge-daemon/          Python dev daemon (GET-only, whitelisted folder)
tools/                  parser_eval · llm_eval · asr_eval · smtp_sink · verify
```

Third-party licenses and versions: see [ATTRIBUTION.md](ATTRIBUTION.md).
