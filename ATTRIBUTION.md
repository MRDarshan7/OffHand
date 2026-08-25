# Attribution

Every third-party library and model used by OFFHAND. No secrets or model
weights are committed to this repo.

| Dependency | Version | License | Used for |
|---|---|---|---|
| Kotlin stdlib + coroutines | 2.1.10 | Apache-2.0 | Language/runtime |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | Android runtime glue |
| AndroidX Lifecycle (runtime, viewmodel, compose) | 2.8.7 | Apache-2.0 | MVVM-lite state |
| AndroidX Activity Compose | 1.10.0 | Apache-2.0 | Compose host activity |
| Jetpack Compose (BOM 2025.02.00: ui, material3) | BOM 2025.02.00 | Apache-2.0 | UI |
| AndroidX Room (runtime, ktx, compiler) | 2.6.1 | Apache-2.0 | Durable outbox, contacts, notes |
| AndroidX WorkManager | 2.10.0 | Apache-2.0 | Offline→online dispatch |
| kotlinx.serialization-json | 1.8.0 | Apache-2.0 | Strict slot persistence |
| Vosk (vosk-android) | 0.3.47 | Apache-2.0 | On-device streaming ASR |
| vosk-model-small-en-us-0.15 | 0.15 | Apache-2.0 | English ASR model (staged at E:\offhand-models, pushed via adb, never committed) |
| JNA | 5.13.0 | Apache-2.0 / LGPL-2.1 (dual, Apache elected) | Vosk native binding |
| JavaMail for Android (android-mail, android-activation) | 1.6.7 | EPL-2.0 / GPL-2.0-with-classpath-exception (EPL elected) | SMTP delivery |
| OkHttp | 4.12.0 | Apache-2.0 | Laptop bridge HTTP client |
| AndroidX CameraX (core, camera2, lifecycle, view) | 1.4.1 | Apache-2.0 | OCR photo capture |
| ML Kit Text Recognition (bundled Latin model) | 16.0.1 | Google ML Kit ToS (proprietary, on-device) | Offline OCR |
| JUnit | 4.13.2 | EPL-1.0 | Unit tests |
| Python `vosk` pip package (dev tool only) | latest | Apache-2.0 | PC-side ASR eval (tools/asr_eval) |

Dev-only tools (not shipped): `tools/smtp_sink` (stdlib Python SMTP sink),
`bridge-daemon` (stdlib Python HTTP daemon), Windows TTS for eval WAVs.
