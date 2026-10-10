#!/usr/bin/env python3
"""A tiny stand-in for Home Assistant for the emulator smoke test (standard library only).

Speaks just enough of the WebSocket API for Klangbild: auth, subscribe_entities (the
speaker, sun.sun and – in a second subscription – the other speakers of its group),
render_template (the number of speakers), call_service (music_assistant.get_queue and the
media keys), get_config (where it stands: Berlin, for the Fensterblick's sun and moon), a
weather entity (partly cloudy), unsubscribe_events and ping.
Serves the cover as a PNG. 40 seconds after the app subscribed, the next track starts, so
the smoke test also runs through a track change. The emulator reaches the host at 10.0.2.2.

    python3 ci/fake_ha.py [port]
"""
import asyncio
import base64
import datetime
import hashlib
import json
import struct
import sys
import time
import zlib

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8123
SPEAKER = "media_player.wohnzimmer"
OTHERS = ["media_player.kueche", "media_player.bad", "media_player.buero", "media_player.flur", "media_player.terrasse"]
NAMES = {SPEAKER: "Wohnzimmer", "media_player.kueche": "Küche", "media_player.bad": "Bad",
         "media_player.buero": "Büro", "media_player.flur": "Flur", "media_player.terrasse": "Terrasse"}
TRACKS = [
    ("Neonlicht über der A40", "Lumen Drive", "Nachtfahrt", 251, (40, 20, 90), (240, 90, 120)),
    ("Spiegel im Spiegel", "Arvo Pärt", "Alina", 562, (20, 40, 60), (200, 210, 220)),
]
started = time.time()
first_subscription = None
SWITCH_AFTER = 40


def png(size, top, bottom):
    """A vertical two-colour gradient as an uncompressed-ish PNG."""
    rows = bytearray()
    for y in range(size):
        t = y / (size - 1)
        r, g, b = (int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        rows += b"\x00" + bytes((r, g, b)) * size

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    head = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", head) + chunk(b"IDAT", zlib.compress(bytes(rows), 6)) + chunk(b"IEND", b"")


def current():
    if first_subscription is None:
        return 0
    return 0 if time.time() - first_subscription < SWITCH_AFTER else 1


def iso(t):
    return datetime.datetime.fromtimestamp(t, datetime.timezone.utc).isoformat()


def speaker_state():
    i = current()
    title, artist, album, duration, _, _ = TRACKS[i]
    base = first_subscription or started
    t0 = base if i == 0 else base + SWITCH_AFTER
    return {
        "s": "playing",
        "a": {
            "friendly_name": NAMES[SPEAKER],
            "media_title": title, "media_artist": artist, "media_album_name": album,
            "media_duration": duration, "media_position": 0, "media_position_updated_at": iso(t0),
            "entity_picture": f"/local/cover{i}.png",
            "app_id": "music_assistant", "mass_player_type": "player", "active_queue": "wohnzimmer",
            "group_members": [SPEAKER] + OTHERS, "volume_level": 0.35, "is_volume_muted": False,
        },
        "lc": t0, "lu": t0,
    }


def queue():
    i = current()
    title, artist, album, duration, _, _ = TRACKS[i]
    nxt = TRACKS[(i + 1) % len(TRACKS)]
    item = lambda tr: {"name": tr[0], "duration": tr[3], "media_item": {
        "media_type": "track", "name": tr[0], "artists": [{"name": tr[1]}], "album": {"name": tr[2]}}}
    return {SPEAKER: {"queue_id": "wohnzimmer", "active": True, "name": "Wohnzimmer", "items": 2,
                      "current_item": item(TRACKS[i]), "next_item": item(nxt)}}


async def send(writer, text):
    data = text.encode()
    n = len(data)
    if n < 126:
        header = struct.pack("!BB", 0x81, n)
    elif n < 65536:
        header = struct.pack("!BBH", 0x81, 126, n)
    else:
        header = struct.pack("!BBQ", 0x81, 127, n)
    writer.write(header + data)
    await writer.drain()


async def receive(reader):
    while True:
        b1, b2 = await reader.readexactly(2)
        opcode, n = b1 & 0x0F, b2 & 0x7F
        if n == 126:
            n = struct.unpack("!H", await reader.readexactly(2))[0]
        elif n == 127:
            n = struct.unpack("!Q", await reader.readexactly(8))[0]
        mask = await reader.readexactly(4) if b2 & 0x80 else b"\0\0\0\0"
        payload = bytes(c ^ mask[i % 4] for i, c in enumerate(await reader.readexactly(n)))
        if opcode == 8:
            return None
        if opcode == 1:
            return payload.decode()


async def session(reader, writer):
    await send(writer, json.dumps({"type": "auth_required", "ha_version": "2026.10.0"}))
    main_sub = None
    shown = current()

    async def changes():
        nonlocal shown
        while True:
            await asyncio.sleep(1)
            if main_sub is not None and current() != shown:
                shown = current()
                await send(writer, json.dumps({"id": main_sub, "type": "event", "event": {"a": {SPEAKER: speaker_state()}}}))

    ticker = asyncio.create_task(changes())
    try:
        while True:
            text = await receive(reader)
            if text is None:
                return
            msg = json.loads(text)
            kind, mid = msg.get("type"), msg.get("id")
            print(kind, msg.get("service") or msg.get("entity_ids") or "", flush=True)
            if kind == "auth":
                await send(writer, json.dumps({"type": "auth_ok", "ha_version": "2026.10.0"}))
            elif kind == "subscribe_entities":
                ids = msg.get("entity_ids", [])
                await send(writer, json.dumps({"id": mid, "type": "result", "success": True, "result": None}))
                if SPEAKER in ids:
                    global first_subscription
                    first_subscription = first_subscription or time.time()
                    main_sub = mid
                    states = {SPEAKER: speaker_state(), "sun.sun": {"s": "above_horizon", "a": {"elevation": 32.0}, "lc": started}}
                    if "weather.forecast_home" in ids:
                        states["weather.forecast_home"] = {"s": "partlycloudy", "a": {"cloud_coverage": 40.0, "friendly_name": "Forecast Home"}, "lc": started}
                else:
                    states = {e: {"s": "playing", "a": {"friendly_name": NAMES.get(e, e)}, "lc": started} for e in ids}
                await send(writer, json.dumps({"id": mid, "type": "event", "event": {"a": states}}))
            elif kind == "call_service":
                result = {"context": {"id": "smoke"}}
                if msg.get("service") == "get_queue":
                    result["response"] = queue()
                await send(writer, json.dumps({"id": mid, "type": "result", "success": True, "result": result}))
            elif kind == "render_template":
                # the number of Music Assistant speakers: all six play together
                await send(writer, json.dumps({"id": mid, "type": "result", "success": True, "result": None}))
                await send(writer, json.dumps({"id": mid, "type": "event", "event": {"result": 1 + len(OTHERS), "listeners": {}}}))
            elif kind == "get_config":
                await send(writer, json.dumps({"id": mid, "type": "result", "success": True,
                                               "result": {"latitude": 52.52, "longitude": 13.40, "time_zone": "Europe/Berlin"}}))
            elif kind == "ping":
                await send(writer, json.dumps({"id": mid, "type": "pong"}))
            elif mid is not None:
                await send(writer, json.dumps({"id": mid, "type": "result", "success": True, "result": None}))
    finally:
        ticker.cancel()


async def handle(reader, writer):
    try:
        request = (await reader.readuntil(b"\r\n\r\n")).decode("latin-1")
        line, *headers = request.split("\r\n")
        path = line.split(" ")[1] if " " in line else "/"
        fields = {h.split(":", 1)[0].strip().lower(): h.split(":", 1)[1].strip() for h in headers if ":" in h}
        if fields.get("upgrade", "").lower() == "websocket":
            key = fields["sec-websocket-key"]
            accept = base64.b64encode(hashlib.sha1((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encode()).digest()).decode()
            writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                          f"Sec-WebSocket-Accept: {accept}\r\n\r\n").encode())
            await writer.drain()
            await session(reader, writer)
        elif path.startswith("/local/cover"):
            i = 1 if "cover1" in path else 0
            body = png(640, TRACKS[i][4], TRACKS[i][5])
            writer.write(b"HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: %d\r\n\r\n" % len(body) + body)
            await writer.drain()
        else:
            writer.write(b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n")
            await writer.drain()
    except (asyncio.IncompleteReadError, ConnectionError):
        pass
    finally:
        writer.close()


async def main():
    server = await asyncio.start_server(handle, "0.0.0.0", PORT)
    print(f"fake Home Assistant on :{PORT}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
