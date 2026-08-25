#!/usr/bin/env python3
"""Vosk ASR eval: transcribe the TTS-generated WAVs with the same model the
phone uses and score word overlap against the intended phrases."""
import json
import os
import sys
import wave

from vosk import Model, KaldiRecognizer, SetLogLevel

MODEL_DIR = r"E:\offhand-models\vosk-model-small-en-us-0.15"
WAV_DIR = r"E:\offhand-models\tts"

EXPECTED = {
    "email_priya": "email priya about the assignment deadline",
    "remind_lab": "remind me to submit the lab record tomorrow morning",
    "meeting_team": "schedule a meeting with the project team tomorrow at 3 pm",
    "fetch_report": "get the quarterly report file from my laptop",
    "clipboard": "copy whatever is on my clipboard",
    "note_milk": "note down buy milk and eggs",
}


def transcribe(model, path):
    wf = wave.open(path, "rb")
    assert wf.getframerate() == 16000 and wf.getnchannels() == 1, path
    rec = KaldiRecognizer(model, 16000)
    while True:
        data = wf.readframes(4000)
        if not data:
            break
        rec.AcceptWaveform(data)
    return json.loads(rec.FinalResult()).get("text", "")


def main():
    SetLogLevel(-1)
    model = Model(MODEL_DIR)
    total, ok = 0, 0
    for key, expected in EXPECTED.items():
        path = os.path.join(WAV_DIR, f"{key}.wav")
        if not os.path.exists(path):
            print(f"MISSING {path}")
            continue
        got = transcribe(model, path)
        exp_words = expected.replace("3 pm", "three p m").split()
        got_words = got.split()
        overlap = len([w for w in exp_words if w in got_words]) / len(exp_words)
        total += 1
        status = "OK " if overlap >= 0.7 else "LOW"
        if overlap >= 0.7:
            ok += 1
        print(f"{status} {key}: overlap={overlap:.2f}  got=\"{got}\"")
    print(f"\n{ok}/{total} phrases at >=70% word overlap")
    sys.exit(0 if ok == total and total > 0 else 1)


if __name__ == "__main__":
    main()
