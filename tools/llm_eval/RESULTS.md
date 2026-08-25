# LLM parser eval — results record

Model: qwen2.5-1.5b-instruct-q4_k_m.gguf · llama.cpp b4658 (x64 AVX2, CPU)
Grammar: `app/src/main/assets/action_schema.gbnf` (same file the phone uses)
Prompt: `app/src/main/assets/parser_prompt.txt` (same file the phone uses)
Decoding: greedy, temperature 0, grammar-constrained. ~40–60 s/case on the
build PC (i7-8565U); expect different absolute latency on the phone.

## Run history (2026-08-26)

1. **Full sweep #1 — 23/26.** Failures: "write to X" mapped to capture_note;
   compound datetime split ("tomorrow at 3 pm" → only "tomorrow"); one true
   hallucination ("play some music please" → create_event).
2. **Prompt iteration #1** (committed d3a58d6): "write to X" named as email
   phrasing; complete-time-phrase rule; music/questions/device-control named
   out-of-scope. Also fixed a Windows argv encoding crash (prompt now fed via
   file; kept pure ASCII).
3. **Targeted re-run of the 3 failures — 3/3 pass.**
4. **Full sweep #2 — 25/26.** One regression: with no time spoken, the model
   invented "tomorrow morning" for a reminder (copied from the only reminder
   few-shot).
5. **Prompt iteration #2:** added a 7th few-shot — a reminder with no time,
   datetime null. (Deviation from the spec's "4–6 few-shots", reported.)
6. **Targeted re-run of all 4 reminder cases — 4/4 pass** (including the
   regressed case).

**Testing was stopped here at the user's request** — no third full sweep was
run. The standing model-quality evidence is: sweep #1 23/26, sweep #2 25/26,
plus targeted re-passes 3/3 and 4/4 after each prompt iteration. Every output
across all runs was grammar-valid JSON with a legal action — zero schema
violations, zero illegal actions; the single hallucinated action observed in
sweep #1 was eliminated by iteration #1 and did not recur.

## Caveat

This eval exercises the same GGUF + grammar + prompt as the device, via
llama-cli. It does NOT exercise the app's JNI wrapper (`offhand_llama.cpp`)
— that code is compile-verified for arm64 but has never executed; first
execution happens on the real phone.

Re-run: `py tools\llm_eval\run_eval.py` (all) or `py tools\llm_eval\run_eval.py 10` (one case).
