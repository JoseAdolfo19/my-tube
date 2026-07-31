#!/usr/bin/env python3
"""Audio proxy local para MiAppVideos.

Resuelve la URL de audio con po_token via yt-dlp (que solo puede generarse
desde esta IP) y hace passthrough de los rangos HTTP que pide ExoPlayer,
evitando el limite de 1 MB de googlevideo para URLs sin potoken.

Uso: python audio_proxy.py [puerto]  (default 8080)
"""
import http.server
import json
import os
import re
import socketserver
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
URL_TTL_SECONDS = 4 * 3600
PROBE_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
PROXY_KEY = os.environ.get("PROXY_KEY", "")

url_cache = {}
url_locks = {}


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def resolve_url(video_id):
    lock = url_locks.setdefault(video_id, threading.Lock())
    with lock:
        cached = url_cache.get(video_id)
        if cached and cached[1] > time.time():
            return cached[0]
        cmd = [
            sys.executable, "-m", "yt_dlp",
            "-g", "-f", "best[height<=720][ext=mp4]/best[ext=mp4]/bestaudio/best",
            "--no-playlist",
            "--no-warnings",
            "--extractor-args", "youtube:player_client=android_music,android,tv,web_embedded,web_safari",
            f"https://www.youtube.com/watch?v={video_id}",
        ]
        log(f"resolviendo {video_id} ...")
        try:
            result = subprocess.run(cmd, capture_output=True, text=True,
                                    timeout=180)
        except subprocess.TimeoutExpired:
            log(f"timeout resolviendo {video_id}")
            return None
        url = result.stdout.strip().splitlines()[-1] if result.stdout.strip() else None
        if result.returncode != 0 or not url:
            log(f"yt-dlp fallo {video_id}: {result.stderr[-2000:]}")
            return None
        url_cache[video_id] = (url, time.time() + URL_TTL_SECONDS)
        log(f"ok {video_id} (len={len(url)})")
        return url


def do_passthrough(handler, video_id, range_header):
    url = resolve_url(video_id)
    if not url:
        handler.send_error_json(502, "no se pudo resolver el stream")
        return
    for attempt in range(2):
        try:
            req = urllib.request.Request(url)
            if range_header:
                req.add_header("Range", range_header)
            with urllib.request.urlopen(req, timeout=60) as resp:
                status = resp.status
                if status == 403:
                    url_cache.pop(video_id, None)
                    if attempt == 0:
                        url = resolve_url(video_id)
                        if not url:
                            handler.send_error_json(502, "stream re-resuelto sin exito")
                            return
                        continue
                    handler.send_error_json(502, "googlevideo 403")
                    return
                handler.send_response(status)
                for header in ("Content-Type", "Content-Range",
                               "Content-Length", "Accept-Ranges"):
                    value = resp.headers.get(header)
                    if value:
                        handler.send_header(header, value)
                handler.send_header("Cache-Control", "public, max-age=3600")
                handler.end_headers()
                try:
                    while True:
                        chunk = resp.read(262144)
                        if not chunk:
                            break
                        handler.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                    return
                return
        except urllib.error.HTTPError as e:
            if e.code == 403 and attempt == 0:
                url_cache.pop(video_id, None)
                url = resolve_url(video_id)
                if url:
                    continue
            log(f"passthrough error {video_id}: {e}")
            handler.send_error_json(502, f"error {e.code}")
            return
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            return
        except Exception as e:
            log(f"error {video_id}: {e}")
            try:
                handler.send_error_json(502, str(e))
            except Exception:
                pass
            return


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        log(f"{self.address_string()} {fmt % args}")

    def send_error_json(self, code, msg):
        try:
            body = json.dumps({"error": msg}).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            pass

    def parse_video_id(self):
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path != "/audio":
            return None
        if PROXY_KEY and (query.get("key") or [""])[0] != PROXY_KEY:
            self.send_error_json(401, "key invalida")
            return None
        video_id = (query.get("v") or [""])[0]
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
            return None
        return video_id

    def do_HEAD(self):
        if self.path == "/":
            self.send_response(200)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if self.parse_video_id() is None:
            self.send_error_json(404, "solo /audio?v=<videoId>")
            return
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self):
        if self.path == "/":
            body = json.dumps({"status": "ok"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        if parsed.path == "/diag":
            if PROXY_KEY and (query.get("key") or [""])[0] != PROXY_KEY:
                self.send_error_json(401, "key invalida")
                return
            video_id = (query.get("v") or [""])[0]
            if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
                self.send_error_json(400, "v invalido")
                return
            cmd = [
                sys.executable, "-m", "yt_dlp", "-v",
                "-f", "bestaudio", "--no-playlist", "--no-warnings", "-g",
                "--extractor-args", "youtube:player_client=android_music,web_safari",
                f"https://www.youtube.com/watch?v={video_id}",
            ]
            try:
                result = subprocess.run(cmd, capture_output=True, text=True,
                                        timeout=120)
                diag = f"rc={result.returncode}\n--- STDOUT ---\n{result.stdout[-4000:]}\n--- STDERR ---\n{result.stderr[-4000:]}"
            except subprocess.TimeoutExpired:
                diag = "TIMEOUT 120s"
            body = diag.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        video_id = self.parse_video_id()
        if video_id is None:
            self.send_error_json(404, "solo /audio?v=<videoId>")
            return
        do_passthrough(self, video_id, self.headers.get("Range"))


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    port = int(os.environ.get("PORT") or (sys.argv[1] if len(sys.argv) > 1 else 8080))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    log(f"audio proxy en 0.0.0.0:{port}")
    server.serve_forever()
