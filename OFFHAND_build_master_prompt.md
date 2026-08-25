# OFFHAND — BUILD PROMPT (Claude Code · Fable 5)

You are the lead engineer building **OFFHAND** end-to-end: an offline-first,
on-device agentic assistant for Android. Build it milestone by milestone, prove
each milestone on a real device before moving on, and prefer a narrow product
that never fails over a broad one that mostly works.

`OFFHAND_master.md` is attached and is the product specification. This prompt is
the engineering execution layer. Where they conflict, this prompt wins on
engineering decisions; the master doc wins on product intent.

---

## 1. WHAT YOU ARE BUILDING

```
push-to-talk voice
   → on-device ASR
   → local LLM parse (grammar-constrained JSON)
   → independent validation
   → editable confirmation card
   → durable outbox (Room)
   → execute now if online / queue if offline
   → auto-dispatch when connectivity returns (WorkManager)
```

Core principles:

1. The reasoning path (ASR → LLM → validation → draft → queue) makes **zero
   network calls**. Only external delivery (email send, laptop bridge) touches a
   network.
2. Offline is a first-class state, never an error state. The app never claims an
   email was delivered while offline — it says *"Queued — will send when
   connected."*
3. Anything irreversible is drafted and human-confirmed before execution. The
   parser layer returns drafts; it never executes.
4. Exactly six actions, no additions:

```
send_email · create_event · set_reminder ·
fetch_laptop_file · get_laptop_clipboard · capture_note
```

---

## 2. ENGINEERING RULES

1. **Verify, never assume.** Claim a thing works only after running it and seeing
   the output. Tag anything unverified as INFERRED or UNKNOWN. Do not invent
   Office Kit APIs, NPU capabilities, or library behaviour.
2. **The app must always build.** Never leave `main` broken. Risky experiments go
   on a branch, get proven, then merge. Commit small and often with meaningful
   messages.
3. **Milestones are strictly ordered.** Do not start milestone N+1 until N's
   acceptance criteria pass on a physical device.
4. **Timeboxes.** Where a step has one, and it expires, take the named fallback
   and move on. Escalate to the human when a fallback changes product claims.
5. **Ask the human only when genuinely blocked** — a credential, a physical
   device action (USB, permission dialog, mic test), or a product decision.
   Batch questions.
6. Maintain `ATTRIBUTION.md` from the first dependency: library/model, version,
   license, what it's used for. Never commit secrets or model weights.

---

## 3. LOCKED TECHNICAL DECISIONS

Do not relitigate without a demonstrated blocker.

| Concern | Decision | Rationale / fallback |
|---|---|---|
| Platform | Native Android, **Kotlin + Jetpack Compose**, single `app` module | Local inference bindings, mic, and camera are first-class native |
| minSdk / target | minSdk 28, target latest installed SDK | Target hardware is a current flagship |
| LLM runtime | **llama.cpp** via JNI (start from its official Android example) | Only mature option with **GBNF grammar-constrained decoding** — the core reliability mechanism. Fallback: MediaPipe LLM Inference with validator-side retry-on-invalid (max 2 retries); last resort: deterministic keyword/regex parser. All behind one `ActionParser` interface so the swap is a single binding |
| Model | **Qwen2.5-1.5B-Instruct, Q4_K_M GGUF**; try 3B only if 1.5B latency < 2 s leaves headroom | Weights are pre-staged on disk — never downloaded mid-build |
| NPU | CPU inference is the product. Attempt QNN/NPU only after Milestone 5, on a branch, timeboxed 90 min | NPU is a bonus, not a dependency. Never claim NPU support unless proven on the target device |
| ASR | **Vosk** (small-en streaming model) — pure JVM API, fastest integration. Upgrade to whisper.cpp small-int8 only if measured Vosk accuracy is unusable AND Milestones 1–4 are green | Push-to-talk only. No wake word |
| Persistence | **Room** + kotlinx.serialization | |
| Dispatch | **WorkManager** unique work per action UUID + NETWORK_CONNECTED constraint, plus a ConnectivityManager NetworkCallback to trigger immediate drains | Idempotency: state machine + unique work + state check before send |
| Email | **Jakarta Mail (SMTP) + Gmail app password** on a dedicated test account | No OAuth flow anywhere. Credentials in `local.properties` → BuildConfig, git-ignored. Gmail REST API is out of scope |
| create_event | Insert into the **device calendar via ContentResolver/CalendarContract** | Fully offline; the OS syncs later. No Google Calendar API |
| set_reminder | **AlarmManager + local notification** | Fully offline |
| Contacts | **In-app seeded contact list** (name → email), editable in the UI | Avoids READ_CONTACTS permission complexity. Device-contacts import is out of scope for now |
| OCR / camera | **CameraX + ML Kit on-device text recognition** | Milestone 5 only |
| Laptop bridge | Build our **own daemon**: Python + FastAPI (or stdlib http.server) on the laptop, OkHttp on the phone, JSON over local network. Endpoints: `/health`, `/clipboard`, `/files?dir=`, `/file?path=` (whitelisted to one shared folder) | Office Kit's programmable surface is UNKNOWN — if it turns out to expose a usable API, wrap it behind the same `LaptopBridge` interface; otherwise the daemon is the implementation. The interface isolates the bet |
| DI / architecture | No DI framework. Plain constructor injection via one `AppContainer`. MVVM-lite: ViewModel + StateFlow | No Hilt, no multi-module, no clean-architecture ceremony |

---

## 4. REPOSITORY LAYOUT

```
offhand/
  app/src/main/java/com/offhand/
    audio/        # PTT recorder, Vosk ASR service
    llm/          # JNI bindings, LlamaEngine, grammar assets, PromptBuilder
    parse/        # ActionParser, schema types, Validator, ContactResolver, DateResolver
    action/       # ActionRepository, state machine, executors (email/calendar/reminder/bridge/note)
    data/         # Room db, entities, DAOs
    dispatch/     # DispatchWorker, ConnectivityObserver
    bridge/       # LaptopBridge interface + HttpBridgeClient (+ OfficeKitBridge if a real API exists)
    ocr/          # CameraX capture + ML Kit
    ui/           # HomeScreen, ConfirmationCard, OutboxScreen, dark theme
    demo/         # DemoSeeder: seed contacts, sample states, reset action
    AppContainer.kt · MainActivity.kt
  app/src/main/assets/     # action_schema.gbnf, vosk model (or extraction logic)
  bridge-daemon/           # Python daemon + its README
  tools/parser_eval/       # transcript → expected-JSON test harness
  ATTRIBUTION.md · README.md
```

The GGUF is pushed to the app's files directory via `adb push` (document the
exact command in README) — never bundled in the APK.

---

## 5. ACTION CONTRACT

Single output schema (kotlinx.serialization, strict mode, unknown keys
rejected):

```json
{
  "action": "send_email | create_event | set_reminder | fetch_laptop_file | get_laptop_clipboard | capture_note",
  "slots": {
    "recipient": "string|null",
    "subject":   "string|null",
    "body":      "string|null",
    "datetime":  "string|null   // ISO8601 after resolution",
    "path_hint": "string|null"
  },
  "confidence": "high | low"
}
```

**Starting GBNF** (`app/src/main/assets/action_schema.gbnf`) — verify against
the llama.cpp version you vendor and against real model output; adjust as
needed:

```
root ::= "{" ws "\"action\"" ws ":" ws action ws "," ws "\"slots\"" ws ":" ws slots ws "," ws "\"confidence\"" ws ":" ws conf ws "}"
action ::= "\"send_email\"" | "\"create_event\"" | "\"set_reminder\"" | "\"fetch_laptop_file\"" | "\"get_laptop_clipboard\"" | "\"capture_note\""
slots ::= "{" ws "\"recipient\"" ws ":" ws sn ws "," ws "\"subject\"" ws ":" ws sn ws "," ws "\"body\"" ws ":" ws sn ws "," ws "\"datetime\"" ws ":" ws sn ws "," ws "\"path_hint\"" ws ":" ws sn ws "}"
conf ::= "\"high\"" | "\"low\""
sn ::= str | "null"
str ::= "\"" chr* "\""
chr ::= [^"\\\x00-\x1f] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
ws ::= [ \t\n]?
```

**Parser prompt** — iterate it inside `tools/parser_eval`, never by feel: a
short system instruction stating the six actions and the JSON contract, then
4–6 few-shot pairs covering a clean email, a reminder with relative time
("Thursday morning"), a laptop file fetch, a note, an ambiguous recipient
(→ `confidence: "low"`), and an out-of-scope request (→ `capture_note` with the
transcript as body — the safe default; never a guessed action).

**Validation is independent of the model.** After grammar-valid JSON:

1. Strict schema parse.
2. Business rules per action: required slots present; recipient resolves
   against the contact list via fuzzy match, else `NEEDS_INPUT`; datetime
   parsed from natural language **in code** (today/tomorrow/weekday names;
   morning = 09:00, afternoon = 14:00, evening = 18:00) — the model's text is a
   hint, the code's output is truth; `path_hint` sanitised, no traversal.
3. Anything unresolvable downgrades to a low-confidence draft with the field
   flagged in the UI.

The model's `confidence` is advisory; the validator's verdict is what the UI
trusts.

---

## 6. DATA MODEL & STATE MACHINE

`ActionEntity`: `id` (UUID pk) · `type` · `slotsJson` · `state` · `createdAt` ·
`updatedAt` · `attempts` · `lastError?` · `transcript` · `idempotencyKey` (=id).
Index on `state`. Database version 1 with destructive migration fallback.

```
DRAFT → CONFIRMED → QUEUED → SENDING → DONE
   DRAFT/CONFIRMED/QUEUED → CANCELLED
   SENDING → FAILED → QUEUED (retry with backoff, attempts + 1)
   FAILED (attempts ≥ 5) → terminal, surfaced as "Needs attention"
```

All transitions go through one repository function that rejects illegal moves.
Executors mark SENDING before side effects; a SENDING item found at startup
with no recorded result returns to QUEUED. Send-once is best-effort via unique
work + state check — document that honestly in code comments.

Offline-capable actions (`create_event`, `set_reminder`, `capture_note`)
execute immediately on confirm and never queue. Network actions (`send_email`,
bridge actions) queue when offline.

---

## 7. BUILD ORDER — MILESTONES

Strictly in order. Prove each on a physical device. Commit and tag at each
milestone (`m0`, `m1`, …) and report status to the human in one line.

**M0 — Skeleton.** Compose app builds and installs: Home screen with a
push-to-talk button and an Online/Offline banner driven by
ConnectivityObserver; empty Outbox screen; Room database, entity, and DAO
wired; dark theme. *Accept: the banner flips live when airplane mode toggles.*

**M1 — Local inference (timebox ~2 h of focused effort).** llama.cpp JNI
integrated; the GGUF loads from the files dir; a hardcoded prompt streams
tokens; then GBNF is enabled and output is grammar-valid JSON for 10 varied
hardcoded transcripts, 10/10. Log model load time and tokens/sec. *On timebox:
take the MediaPipe fallback, else the deterministic parser — and tell the human
immediately, since it changes what the product can claim.*

**M2 — Voice → validated draft.** Vosk streaming push-to-talk → transcript →
parse → validate → typed draft. Build the eval harness with ≥ 15 cases (all six
actions, ambiguity, junk input) and make it pass: every output is a safe draft
or `NEEDS_INPUT`; **zero** invalid or hallucinated actions. *Accept: voice to
correct draft on device in < 4 s.*

**M3 — Confirmation + durable queue.** ConfirmationCard with every slot
editable, low-confidence fields visually flagged, Edit / Confirm / Cancel.
On confirm: offline actions execute (event visible in the device calendar,
reminder notification fires, note saved); network actions land in the Outbox as
QUEUED. *Accept: queue an action, force-stop the app, reboot the phone — the
item is still QUEUED with correct content.*

**M4 — Offline → online dispatch.** DispatchWorker sends queued email via
SMTP; the connectivity callback triggers an immediate drain; retries back off;
states animate QUEUED → SENDING → DONE in the Outbox. *Accept, three
consecutive runs: airplane mode on → queue two emails → airplane mode off →
both arrive in the test inbox within 60 s, exactly once each.* **When M4
passes, OFFHAND exists. Everything after is expansion.**

**M5 — Expansion (each on a branch, in this order).**
(a) Laptop bridge: daemon + phone client; `fetch_laptop_file` pulls a file from
the whitelisted folder into the email-attachment path; `get_laptop_clipboard`
into a draft body. If a real Office Kit API is available, implement
`OfficeKitBridge` behind the same interface instead.
(b) CameraX + ML Kit: photo → recognised text → prefilled `capture_note` or
email-body draft.
(c) Outbox polish with per-state copy: *Working offline · Saved locally, will
send when connected · Sending… · Sent · Needs attention.*

**M6 — Hardening.** DemoSeeder (seed contacts, sample laptop folder, reset
behind a long-press) · run the full end-to-end flow — including the
airplane-mode transition and a bridge call — five times consecutively without
failure · fix only what breaks · network audit (see Security) · README with
setup in ≤ 10 steps including the adb push command · ATTRIBUTION.md complete.

**Optional, only after M6:** the NPU/QNN experiment — on a branch, 90 minutes
hard, merged only if output is 10/10 grammar-valid and measurably faster.

---

## 8. SUBSYSTEM NOTES

**Audio/ASR.** Press-and-hold to talk, release to finalise. 16 kHz mono PCM as
Vosk expects. Initialise the recognizer once at app start and log init time.
Show the live partial transcript so recognition failures are visible and
retryable; cancel by swiping away. If accuracy is poor in noisy conditions,
bring the human a measured Vosk-vs-whisper.cpp comparison, not a feeling.

**LLM.** Load the model once in a retained singleton or long-lived service,
never per request. `n_ctx` 1024. Temperature 0. Log per request: prompt tokens,
generated tokens, latency. If 1.5B slot-filling quality is poor on the eval
set, measure 3B before deciding.

**Executors.** One interface, five implementations. Email: Jakarta Mail over
SMTP with STARTTLS, test-account credentials from BuildConfig, subject fallback
"(no subject)", attachment support for bridge-fetched files. Calendar,
reminder, and note as locked above. Bridge executors call `LaptopBridge`.

**Networking truth.** The dispatcher decides "online" from the connectivity
callback plus a cheap reachability check at execution time — never from the UI
banner. The reasoning path's network-free claim is verified in M6, not assumed.

**UI.** Three screens only: Home, Confirmation, Outbox. The offline banner is
calm ("Working offline"), never styled as an error. Low-confidence fields get
an amber border and "check this" microcopy. Large touch targets throughout.

---

## 9. SECURITY & PRIVACY

- Secrets only in `local.properties` → BuildConfig; `.gitignore` in the first
  commit. If a secret ever lands in git: rotate it, rewrite history, tell the
  human.
- Log action **types and states** only. Bodies, recipients, transcripts, and
  audio are never logged at INFO; content logging sits behind a DEBUG flag
  that defaults off.
- The bridge daemon binds to LAN only, serves only the whitelisted directory,
  rejects path traversal, and is GET-only. Say plainly in its README that it is
  a development daemon.
- **Network audit (M6):** with airplane mode on, exercise the full reasoning
  path and confirm zero connection attempts — via the OS network log and by
  ensuring no swallowed network exceptions. This proves principle #1.
- No analytics, no crash reporters, no third-party SDKs beyond those in the
  locked table.

---

## 10. TESTING

- **Parser eval harness** (`tools/parser_eval`): a table of ≥ 15 transcript →
  expected-action cases, runnable on-device or via a small JVM shim around the
  same prompt + grammar. Every prompt or grammar tweak runs against it.
- **Unit tests:** DateResolver (relative dates, weekday math, day-part times),
  ContactResolver (exact / fuzzy / miss), state-machine transition legality,
  per-action slot validators.
- **Manual scripted checks:** the process-death test (M3), the triple
  airplane-mode cycle (M4), the five consecutive end-to-end runs (M6). Keep the
  checklist in the README.
- No UI test framework, no CI.

---

## 11. DO NOT BUILD

Wake word · open-ended chat mode · a seventh action · multi-step planning or
agent loops · OAuth flows · any cloud backend · device-contacts sync ·
Hilt / multi-module / clean-architecture scaffolding · animations beyond state
transitions · settings screens · onboarding · localisation · NPU before M6.
If a task is not on the milestone list and is not a bug in a milestone feature,
it does not exist.

---

## 12. FILL BEFORE RUNNING

- `[FILL]` Test Gmail address (app password goes in `local.properties`, never
  in git)
- `[FILL]` Seed contact names/emails (e.g., "Priya" → …)
- `[FILL]` Laptop shared-folder path for the bridge (e.g., `~/offhand-shared`)
- `[FILL]` Android phone model available for on-device verification
- `[FILL]` Anything known about Office Kit's API surface (paste notes verbatim;
  otherwise mark UNKNOWN)

---

## 13. START

1. Confirm the toolchain: Gradle, Android SDK + NDK present, `adb devices`
   shows a phone, and the GGUF + Vosk model files exist on disk at known paths.
   Report any gap before writing code.
2. Initialise the repo if empty: `.gitignore`, README stub, ATTRIBUTION.md
   stub, empty Compose app. First commit.
3. Execute M0, then proceed milestone by milestone. Verify on hardware, respect
   the timeboxes, keep `main` shippable, and when in doubt choose whatever
   makes the end-to-end flow more deterministic.
