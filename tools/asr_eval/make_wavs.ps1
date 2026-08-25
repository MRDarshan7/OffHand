# Generates 16 kHz mono PCM16 WAVs of the eval phrases via Windows TTS.
# These drive both the JVM-side Vosk check and the on-device DEBUG_WAV path.
Add-Type -AssemblyName System.Speech

$outDir = "E:\offhand-models\tts"
New-Item -ItemType Directory -Force $outDir | Out-Null

$phrases = [ordered]@{
    "email_priya"    = "email priya about the assignment deadline"
    "remind_lab"     = "remind me to submit the lab record tomorrow morning"
    "meeting_team"   = "schedule a meeting with the project team tomorrow at 3 pm"
    "fetch_report"   = "get the quarterly report file from my laptop"
    "clipboard"      = "copy whatever is on my clipboard"
    "note_milk"      = "note down buy milk and eggs"
}

$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    16000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.Rate = -1
foreach ($key in $phrases.Keys) {
    $path = Join-Path $outDir "$key.wav"
    $synth.SetOutputToWaveFile($path, $fmt)
    $synth.Speak($phrases[$key])
    $synth.SetOutputToNull()
    Write-Output "wrote $path"
}
$synth.Dispose()
