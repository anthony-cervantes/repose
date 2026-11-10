#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Verify an already-built Repose image against a real HTTP backend using Docker."""
import base64
import http.client
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def backend():
    class Handler(BaseHTTPRequestHandler):
        def handle_request(self):
            if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
                chunks = []
                while True:
                    size = int(self.rfile.readline().split(b";", 1)[0], 16)
                    if size == 0:
                        while self.rfile.readline() != b"\r\n":
                            pass
                        break
                    chunks.append(self.rfile.read(size))
                    assert self.rfile.read(2) == b"\r\n"
                body = b"".join(chunks)
            else:
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            data = json.dumps({"method": self.command,
                               "body": base64.b64encode(body).decode(),
                               "headers": {key.lower(): value for key, value in self.headers.items()}}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        do_GET = do_HEAD = do_POST = do_PUT = do_PATCH = do_DELETE = do_OPTIONS = handle_request

    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def send(port, method, body=b"", framing="missing"):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.putrequest(method, "/verify")
        connection.putheader("Content-Type", "text/plain")
        connection.putheader("X-Preserve", "unchanged")
        if framing == "chunked":
            connection.putheader("Transfer-Encoding", "chunked")
            payload = (f"{len(body):x}\r\n".encode() + body + b"\r\n" if body else b"") + b"0\r\n\r\n"
        else:
            if framing == "length":
                connection.putheader("Content-Length", str(len(body)))
            payload = body
        connection.endheaders(payload)
        response = connection.getresponse()
        data = response.read()
        assert response.status == 200, (method, response.status, data)
        return json.loads(data)
    finally:
        connection.close()


def verify(image):
    prefix = "repose-empty-" + uuid.uuid4().hex[:10]
    network, origin, proxy = prefix + "-net", prefix + "-backend", prefix + "-proxy"
    checks = 0
    try:
        docker("network", "create", network)
        docker("run", "--detach", "--name", origin, "--network", network,
               "--network-alias", "backend", "--mount",
               f"type=bind,src={Path(__file__).resolve()},dst=/backend.py,readonly",
               "python:3.14-alpine", "python", "/backend.py", "--backend")
        with tempfile.TemporaryDirectory(prefix=prefix) as directory:
            directory = Path(directory)
            directory.chmod(0o755)
            legacy = directory / "empty-request-filter.cfg.xml"
            legacy.write_text('<empty-request-filter xmlns="http://docs.example.com/rba/empty-request-filter/v1.0"/>')
            legacy.chmod(0o644)
            for enabled, encoding in ((False, "auto"), (True, "auto"), (True, "true"), (True, "false")):
                model = directory / "system-model.cfg.xml"
                filters = '<filter name="empty-request-filter"/>' if enabled else ""
                model.write_text(f'''<system-model xmlns="http://docs.openrepose.org/repose/system-model/v2.0">
  <nodes><node id="verify" hostname="localhost" http-port="8080"/></nodes>
  <filters>{filters}</filters>
  <destinations><endpoint id="backend" default="true" protocol="http" hostname="backend" port="8080" chunked-encoding="{encoding}"/></destinations>
</system-model>''')
                model.chmod(0o644)
                docker("run", "--detach", "--name", proxy, "--network", network,
                       "--publish", "127.0.0.1::8080", "--mount",
                       f"type=bind,src={model},dst=/etc/repose/system-model.cfg.xml,readonly",
                       "--mount", f"type=bind,src={legacy},dst=/etc/repose/empty-request-filter.cfg.xml,readonly", image)
                port = int(docker("port", proxy, "8080/tcp").rsplit(":", 1)[1])
                for attempt in range(60):
                    try:
                        send(port, "GET")
                        break
                    except (OSError, AssertionError, ValueError, http.client.HTTPException):
                        if attempt == 59:
                            raise RuntimeError("Repose did not become ready")
                        time.sleep(1)
                for method in ("POST", "PUT"):
                    for framing in ("missing", "length", "chunked"):
                        result = send(port, method, framing=framing)
                        assert result["method"] == method, result
                        assert base64.b64decode(result["body"]) == (b"{}" if enabled else b""), result
                        headers = result["headers"]
                        assert headers["content-type"] == ("application/json" if enabled else "text/plain"), result
                        assert headers["x-preserve"] == "unchanged", result
                        if enabled:
                            if encoding == "true":
                                assert headers["transfer-encoding"] == "chunked", result
                                assert "content-length" not in headers, result
                            else:
                                assert headers["content-length"] == "2", result
                                assert "transfer-encoding" not in headers, result
                        checks += 1
                if enabled:
                    for method in ("POST", "PUT"):
                        for body in (b" \t\r\n", bytes(range(256)) * 32, b"\x00", b'{"hello":"world"}'):
                            for framing in ("length", "chunked"):
                                result = send(port, method, body, framing)
                                assert base64.b64decode(result["body"]) == body, result
                                assert result["headers"]["content-type"] == "text/plain", result
                                checks += 1
                    for method in ("GET", "PATCH", "DELETE", "OPTIONS"):
                        result = send(port, method, framing="length")
                        assert result["method"] == method, result
                        assert base64.b64decode(result["body"]) == b"", result
                        assert result["headers"]["content-type"] == "text/plain", result
                        checks += 1
                docker("rm", "--force", "--volumes", proxy)
        print(f"Passed {checks} backend checks: disabled by default, empty POST/PUT, framing, byte preservation, other methods.")
    except Exception:
        subprocess.run(["docker", "logs", proxy], check=False)
        subprocess.run(["docker", "logs", origin], check=False)
        raise
    finally:
        subprocess.run(["docker", "rm", "--force", "--volumes", proxy, origin],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
        subprocess.run(["docker", "network", "rm", network],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


if __name__ == "__main__":
    if sys.argv[1:] == ["--backend"]:
        backend()
    elif len(sys.argv) == 2:
        verify(sys.argv[1])
    else:
        sys.exit("Usage: verify-empty-request-filter.py IMAGE")
