# OFFHAND bridge daemon

A **development daemon** — plain HTTP, GET-only, no auth, no TLS. It exposes
exactly one whitelisted folder and the system clipboard to the OFFHAND phone
app on a trusted local network. Do not run it anywhere else.

```powershell
py bridge-daemon\bridge_daemon.py            # shares E:\offhand-shared on port 8787
py bridge-daemon\bridge_daemon.py D:\stuff 9000
```

Endpoints: `/health`, `/clipboard`, `/files`, `/file?path=<name>`.
Path traversal is rejected (bare filenames only, resolved inside the shared
dir); every non-GET method returns 405.

The phone finds the daemon via `offhand.bridge.host` / `offhand.bridge.port`
in `local.properties` (defaults to `10.0.2.2:8787`, the emulator's host
loopback). Office Kit note: its programmable surface is UNKNOWN; if a real
API appears it becomes an `OfficeKitBridge` behind the same `LaptopBridge`
interface.
