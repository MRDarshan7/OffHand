#!/usr/bin/env python3
"""OFFHAND dev SMTP sink.

A zero-dependency SMTP server that accepts every message and writes it to
./inbox as an .eml file. Used to verify the M4 acceptance criterion
(queued emails arrive exactly once) without real mail credentials.

DEVELOPMENT TOOL ONLY: no auth, no TLS, accepts anything sent to it.
Run:  py tools/smtp_sink/smtp_sink.py [port]     (default port 2525)
"""
import asyncio
import datetime
import os
import sys

INBOX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "inbox")
counter = 0


async def handle(reader, writer):
    global counter

    async def send(line):
        writer.write((line + "\r\n").encode())
        await writer.drain()

    await send("220 offhand-sink ESMTP")
    mail_from, rcpt, data_lines, in_data = None, [], [], False

    while True:
        line = await reader.readline()
        if not line:
            break
        text = line.decode("utf-8", "replace").rstrip("\r\n")

        if in_data:
            if text == ".":
                in_data = False
                counter += 1
                ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
                path = os.path.join(INBOX, f"{ts}-{counter:03d}.eml")
                with open(path, "w", encoding="utf-8") as f:
                    f.write(f"X-Sink-From: {mail_from}\n")
                    f.write(f"X-Sink-To: {','.join(rcpt)}\n")
                    f.write("\n".join(data_lines) + "\n")
                print(f"stored {path}", flush=True)
                mail_from, rcpt, data_lines = None, [], []
                await send("250 OK stored")
            else:
                data_lines.append(text[1:] if text.startswith("..") else text)
            continue

        verb = text.upper()
        if verb.startswith("EHLO") or verb.startswith("HELO"):
            await send("250 offhand-sink")
        elif verb.startswith("MAIL FROM"):
            mail_from = text.split(":", 1)[1].strip() if ":" in text else text
            await send("250 OK")
        elif verb.startswith("RCPT TO"):
            rcpt.append(text.split(":", 1)[1].strip() if ":" in text else text)
            await send("250 OK")
        elif verb.startswith("DATA"):
            in_data = True
            await send("354 End data with <CR><LF>.<CR><LF>")
        elif verb.startswith("QUIT"):
            await send("221 bye")
            break
        elif verb.startswith("RSET"):
            mail_from, rcpt, data_lines = None, [], []
            await send("250 OK")
        elif verb.startswith("NOOP"):
            await send("250 OK")
        else:
            await send("502 command not implemented")

    try:
        writer.close()
        await writer.wait_closed()
    except Exception:
        pass


async def main():
    os.makedirs(INBOX, exist_ok=True)
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 2525
    server = await asyncio.start_server(handle, "0.0.0.0", port)
    print(f"offhand smtp sink on port {port}, inbox: {INBOX}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
