#!/usr/bin/env python3
"""OFFHAND LLM parser eval.

Runs the eval transcripts through the REAL model (llama-cli x64, pinned tag
b4658 — the same tag vendored for the Android JNI build), with the SAME GGUF,
SAME grammar (app/src/main/assets/action_schema.gbnf) and SAME prompt template
(app/src/main/assets/parser_prompt.txt) the phone uses.

Checks per case: output parses as strict JSON, action is one of the six,
action matches the expectation, and required slot/confidence checks hold.
Exit code 0 only on full pass.
"""
import json
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
LLAMA_CLI = r"E:\offhand-setup\llama-win\llama-cli.exe"
GGUF = r"E:\offhand-models\qwen2.5-1.5b-instruct-q4_k_m.gguf"
GRAMMAR = os.path.join(REPO, "app", "src", "main", "assets", "action_schema.gbnf")
PROMPT_TEMPLATE = os.path.join(REPO, "app", "src", "main", "assets", "parser_prompt.txt")

ACTIONS = {
    "send_email", "create_event", "set_reminder",
    "fetch_laptop_file", "get_laptop_clipboard", "capture_note",
}

# action: str or set of acceptable actions
# eq: exact slot values · has: substring checks · null: must-be-null slots
# conf: required confidence
CASES = [
    ("email priya about the assignment deadline",
     {"action": "send_email", "eq": {"recipient": "priya"}, "has": {"subject": "assignment"}}),
    ("send an email to rahul subject project update saying the demo is ready",
     {"action": "send_email", "eq": {"recipient": "rahul"},
      "has": {"subject": "project update", "body": "demo is ready"}}),
    ("mail sneha about tomorrow's class",
     {"action": "send_email", "eq": {"recipient": "sneha"}}),
    ("write to rahul that the meeting moved",
     {"action": "send_email", "eq": {"recipient": "rahul"}}),
    ("email keran about the fees",
     {"action": "send_email", "eq": {"recipient": "keran"}, "conf": "low"}),
    ("email daniel about the trip",
     {"action": "send_email", "eq": {"recipient": "daniel"}}),
    ("send email to professor kumar about the seminar",
     {"action": "send_email", "has": {"recipient": "kumar"}}),
    ("remind me to submit the lab record tomorrow morning",
     {"action": "set_reminder", "has": {"body": "lab record", "datetime": "tomorrow morning"}}),
    ("remind me to call amma at 6 pm",
     {"action": "set_reminder", "has": {"body": "amma", "datetime": "6 pm"}}),
    ("set a reminder for the viva on thursday afternoon",
     {"action": "set_reminder", "has": {"datetime": "thursday"}}),
    ("remind me to water the plants",
     {"action": "set_reminder", "has": {"body": "water the plants"}, "null": ["datetime"]}),
    ("schedule a meeting with the project team tomorrow at 3 pm",
     {"action": "create_event", "has": {"subject": "meeting", "datetime": "3 pm"}}),
    ("add dentist appointment on friday morning",
     {"action": "create_event", "has": {"subject": "dentist", "datetime": "friday"}}),
    ("schedule sync meeting",
     {"action": "create_event", "null": ["datetime"]}),
    ("get the quarterly report file from my laptop",
     {"action": "fetch_laptop_file", "has": {"path_hint": "quarterly report"}}),
    ("fetch the presentation called demo day slides from the laptop",
     {"action": "fetch_laptop_file", "has": {"path_hint": "demo day"}}),
    ("get the clipboard from my laptop",
     {"action": "get_laptop_clipboard"}),
    ("copy whatever is on my clipboard",
     {"action": "get_laptop_clipboard"}),
    ("note down buy milk and eggs",
     {"action": "capture_note", "has": {"body": "milk"}}),
    ("take a note the wifi password is offhand123",
     {"action": "capture_note", "has": {"body": "offhand123"}}),
    ("what's the weather like today",
     {"action": "capture_note", "conf": "low"}),
    ("blah blah gibberish input",
     {"action": "capture_note"}),
    ("play some music please",
     {"action": "capture_note"}),
    ("asdf qwer zxcv",
     {"action": "capture_note"}),
    # Real Vosk mishearings: any SAFE interpretation is acceptable.
    ("copy whatever is unlikely board",
     {"action": {"capture_note", "get_laptop_clipboard"}}),
    ("no down by milk and eggs",
     {"action": "capture_note"}),
]


def run_model(transcript, template):
    prompt = template.replace("{transcript}", transcript)
    cmd = [
        LLAMA_CLI, "-m", GGUF,
        "--grammar-file", GRAMMAR,
        "-p", prompt,
        "-n", "200", "--temp", "0",
        "-no-cnv", "--no-display-prompt", "--simple-io",
        "-t", "4", "-c", "1024",
    ]
    t0 = time.time()
    proc = subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=600)
    return proc.stdout.strip(), time.time() - t0, proc.returncode


def check(case, out):
    exp = case[1]
    try:
        obj = json.loads(out)
    except json.JSONDecodeError:
        return "OUTPUT NOT JSON"
    if set(obj.keys()) != {"action", "slots", "confidence"}:
        return "BAD TOP-LEVEL KEYS"
    if set(obj["slots"].keys()) != {"recipient", "subject", "body", "datetime", "path_hint"}:
        return "BAD SLOT KEYS"
    if obj["action"] not in ACTIONS:
        return f"ILLEGAL ACTION {obj['action']}"
    want = exp["action"]
    want = want if isinstance(want, set) else {want}
    if obj["action"] not in want:
        return f"WRONG ACTION {obj['action']} (want {sorted(want)})"
    slots = obj["slots"]
    for k, v in exp.get("eq", {}).items():
        got = (slots.get(k) or "").lower()
        if got != v.lower():
            return f"SLOT {k}={slots.get(k)!r} != {v!r}"
    for k, v in exp.get("has", {}).items():
        got = (slots.get(k) or "").lower()
        if v.lower() not in got:
            return f"SLOT {k}={slots.get(k)!r} !~ {v!r}"
    for k in exp.get("null", []):
        if slots.get(k) is not None:
            return f"SLOT {k} should be null, got {slots.get(k)!r}"
    if "conf" in exp and obj["confidence"] != exp["conf"]:
        return f"CONFIDENCE {obj['confidence']} != {exp['conf']}"
    return None


def main():
    for p in (LLAMA_CLI, GGUF, GRAMMAR, PROMPT_TEMPLATE):
        if not os.path.exists(p):
            print(f"missing: {p}")
            sys.exit(2)
    with open(PROMPT_TEMPLATE, encoding="utf-8") as f:
        template = f.read()

    only = sys.argv[1:] and [int(a) for a in sys.argv[1:]]
    passed = failed = 0
    for i, case in enumerate(CASES):
        if only and i not in only:
            continue
        transcript = case[0]
        out, secs, rc = run_model(transcript, template)
        # keep only the last line that looks like JSON (defensive vs noise)
        m = re.search(r"\{.*\}", out, re.DOTALL)
        payload = m.group(0) if m else out
        err = check(case, payload) if rc == 0 else f"llama-cli rc={rc}"
        if err:
            failed += 1
            print(f"FAIL [{i:02d}] {transcript!r}: {err}\n     out={payload[:200]!r} ({secs:.0f}s)")
        else:
            passed += 1
            print(f"ok   [{i:02d}] {transcript!r} ({secs:.0f}s)")
    print(f"\n{passed} passed, {failed} failed of {passed + failed}")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
