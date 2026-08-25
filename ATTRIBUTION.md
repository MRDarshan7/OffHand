# Attribution

Every third-party library and model used by OFFHAND, kept current from the
first dependency. No secrets or model weights are committed to this repo.

| Dependency | Version | License | Used for |
|---|---|---|---|
| Kotlin (stdlib, coroutines via AndroidX) | 2.1.10 | Apache-2.0 | Language/runtime |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | Android runtime glue |
| AndroidX Lifecycle (runtime, viewmodel, compose) | 2.8.7 | Apache-2.0 | MVVM-lite state |
| AndroidX Activity Compose | 1.10.0 | Apache-2.0 | Compose host activity |
| Jetpack Compose (BOM 2025.02.00: ui, material3) | BOM 2025.02.00 | Apache-2.0 | UI |
| AndroidX Room (runtime, ktx, compiler) | 2.6.1 | Apache-2.0 | Durable action outbox |
| Vosk (planned, M2) | TBD | Apache-2.0 | On-device speech recognition |
| vosk-model-small-en-us-0.15 (planned, M2) | 0.15 | Apache-2.0 | English ASR model (staged at E:\offhand-models, not committed) |
| JUnit | 4.13.2 | EPL-1.0 | Unit tests |

Planned entries are marked and confirmed when the dependency is actually added.
