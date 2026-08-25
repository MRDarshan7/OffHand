#!/usr/bin/env python3
"""OFFHAND laptop bridge daemon.

DEVELOPMENT DAEMON — plain HTTP, GET-only, LAN only, no auth. It serves
exactly one whitelisted directory and the system clipboard to the OFFHAND
phone app. Do not expose it beyond a trusted local network.

Endpoints:
  GET /health            -> {"ok": true}
  GET /clipboard         -> {"text": "<clipboard content>"}
  GET /files             -> {"files": ["a.txt", ...]}   (whitelisted dir only)
  GET /file?path=<name>  -> raw file bytes (name must be inside the dir)

Run:  py bridge-daemon/bridge_daemon.py [shared_dir] [port]
Defaults: shared dir E:\\offhand-shared, port 8787.
"""
import json
import os
import subprocess
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SHARED_DIR = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else r"E:\offhand-shared")
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8787


def read_clipboard() -> str:
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command", "Get-Clipboard"],
            capture_output=True, text=True, timeout=10,
        )
        return out.stdout.rstrip("\r\n")
    except Exception:
        return ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # quiet, but show the path
        print(f"{self.address_string()} {self.path}", flush=True)

    def _json(self, obj, status=200):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)

        if parsed.path == "/health":
            self._json({"ok": True})
        elif parsed.path == "/clipboard":
            self._json({"text": read_clipboard()})
        elif parsed.path == "/files":
            try:
                files = sorted(
                    f for f in os.listdir(SHARED_DIR)
                    if os.path.isfile(os.path.join(SHARED_DIR, f))
                )
            except OSError:
                files = []
            self._json({"files": files})
        elif parsed.path == "/file":
            name = (query.get("path") or [""])[0]
            # Whitelist enforcement: bare filename, resolved inside SHARED_DIR.
            if not name or "/" in name or "\\" in name or ".." in name:
                self._json({"error": "invalid path"}, 400)
                return
            full = os.path.abspath(os.path.join(SHARED_DIR, name))
            if not full.startswith(SHARED_DIR + os.sep) or not os.path.isfile(full):
                self._json({"error": "not found"}, 404)
                return
            with open(full, "rb") as f:
                data = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(data)))
            self.send_header(
                "Content-Disposition", f'attachment; filename="{name}"',
            )
            self.end_headers()
            self.wfile.write(data)
        else:
            self._json({"error": "unknown endpoint"}, 404)

    # Everything except GET is rejected.
    def do_POST(self):
        self._json({"error": "GET only"}, 405)

    do_PUT = do_POST
    do_DELETE = do_POST


def main():
    os.makedirs(SHARED_DIR, exist_ok=True)
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"offhand bridge daemon on port {PORT}, sharing: {SHARED_DIR}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
