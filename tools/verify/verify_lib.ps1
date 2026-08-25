# Shared helpers for scripted emulator verification of OFFHAND milestones.
$script:ADB = "E:\Android\Sdk\platform-tools\adb.exe"
$script:EVIDENCE = Join-Path $PSScriptRoot "evidence"
New-Item -ItemType Directory -Force $script:EVIDENCE | Out-Null

function Adb { param([Parameter(ValueFromRemainingArguments)]$Args) & $script:ADB @Args }

function Wait-Boot {
    param([int]$TimeoutSec = 3600)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $out = & $script:ADB shell getprop sys.boot_completed 2>$null
        if ($out -match "1") { return $true }
        Start-Sleep -Seconds 15
    }
    return $false
}

function Screencap {
    param([string]$Name)
    & $script:ADB shell screencap -p /sdcard/cap.png | Out-Null
    & $script:ADB pull /sdcard/cap.png (Join-Path $script:EVIDENCE "$Name.png") | Out-Null
    Write-Output "evidence: $Name.png"
}

# Find a node by text in the current UI and tap its center.
function Tap-Text {
    param([string]$Text)
    & $script:ADB shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
    $xml = & $script:ADB shell cat /sdcard/ui.xml
    $pattern = 'text="' + [regex]::Escape($Text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($xml, $pattern)
    if (-not $m.Success) {
        # some dumps put bounds before text
        $pattern2 = 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="' + [regex]::Escape($Text) + '"'
        $m = [regex]::Match($xml, $pattern2)
    }
    if (-not $m.Success) { Write-Output "TAP MISS: $Text"; return $false }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    & $script:ADB shell input tap $x $y | Out-Null
    Start-Sleep -Seconds 2
    return $true
}

function UiContains {
    param([string]$Text)
    & $script:ADB shell uiautomator dump /sdcard/ui.xml 2>$null | Out-Null
    $xml = & $script:ADB shell cat /sdcard/ui.xml
    return $xml -match [regex]::Escape($Text)
}

function Inject-Transcript {
    param([string]$Transcript)
    & $script:ADB shell am broadcast -a com.offhand.DEBUG_TRANSCRIPT --es text "'$Transcript'" com.offhand | Out-Null
    Start-Sleep -Seconds 3
}

# API 30 has no `cmd connectivity airplane-mode`; svc covers wifi + data.
function Set-Airplane {
    param([bool]$On)
    $state = if ($On) { "disable" } else { "enable" }
    & $script:ADB shell svc wifi $state | Out-Null
    & $script:ADB shell svc data $state | Out-Null
    Start-Sleep -Seconds 8
}

function Query-Db {
    param([string]$Sql)
    & $script:ADB exec-out run-as com.offhand sqlite3 databases/offhand.db "$Sql" 2>$null
}
