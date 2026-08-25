# Parser eval harness

The parser is deterministic pure Kotlin, so the eval harness lives as a JVM
test suite executed on every build — no device needed:

```
app/src/test/java/com/offhand/parse/ParserEvalHarnessTest.kt   (transcript table)
app/src/test/java/com/offhand/parse/DateResolverTest.kt
app/src/test/java/com/offhand/parse/ContactResolverTest.kt
app/src/test/java/com/offhand/parse/ValidatorTest.kt
```

Run it:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Every parser or validator tweak must keep this table green. The invariants:
every output is one of the six actions; anything unresolvable is a draft with
NEEDS_INPUT flags, never a guess; out-of-scope input becomes `capture_note`
with the transcript as body.
