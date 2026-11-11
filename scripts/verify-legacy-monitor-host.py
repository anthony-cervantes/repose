#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Raw HTTP regression tests against an already-packaged image and local fixtures."""
import argparse
import base64
import http.client
import json
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

AUTHORITY = "stagea.cache.api.rba.rackspace.com"
LEGACY = AUTHORITY + "/lb_monitor"
TOKEN = "local-fixture-token"


def fixture():
    class Handler(BaseHTTPRequestHandler):
        origin_hits = 0

        def reply(self, status, data):
            body = json.dumps(data).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/fixture/stats":
                self.reply(200, {"origin_hits": Handler.origin_hits})
            elif self.path.startswith("/v2.0/tokens/"):
                if self.path.split("?", 1)[0] != "/v2.0/tokens/" + TOKEN or self.headers.get("X-Auth-Token") != TOKEN:
                    self.reply(404, {})
                else:
                    self.reply(200, {"access": {
                        "token": {"id": TOKEN, "expires": "2099-01-01T00:00:00Z"},
                        "user": {"id": "fixture-user", "name": "fixture-user", "roles": []}}})
            else:
                Handler.origin_hits += 1
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                self.reply(200, {"method": self.command, "target": self.path,
                                 "body": base64.b64encode(body).decode(),
                                 "headers": {k.lower(): v for k, v in self.headers.items()}})

    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def request(host=LEGACY, method="GET", target="/", token=None, extra="", close=True):
    # Intentionally no URL library: preserve the exact invalid Host on the wire.
    headers = "" if host is None else f"Host: {host}\r\n"
    headers += "Connection: Close\r\n" if close else ""
    if token is not None:
        headers += f"X-Auth-Token: {token}\r\n"
    return f"{method} {target} HTTP/1.1\r\n{headers}{extra}\r\n".encode("ascii")


def send(port, raw, fragmented=False):
    with socket.create_connection(("127.0.0.1", port), timeout=5) as stream:
        if fragmented:
            # Exercise reads split inside Host and the terminating blank line.
            for byte in raw:
                stream.sendall(bytes([byte]))
                time.sleep(0.001)
        else:
            stream.sendall(raw)
        response = http.client.HTTPResponse(stream)
        response.begin()
        return response.status, dict((k.lower(), v) for k, v in response.getheaders()), response.read()


def verify(image, baseline=False):
    prefix = "repose-monitor-" + uuid.uuid4().hex[:10]
    network, origin, proxy = prefix + "-net", prefix + "-fixture", prefix + "-proxy"
    checks = 0
    try:
        docker("network", "create", network)
        docker("run", "--detach", "--name", origin, "--network", network,
               "--network-alias", "fixture", "--mount",
               f"type=bind,src={Path(__file__).resolve()},dst=/fixture.py,readonly",
               "python:3.14-alpine", "python", "/fixture.py", "--fixture")

        def origin_hits():
            return int(docker("exec", origin, "python", "-c",
                       'import json,urllib.request; print(json.load(urllib.request.urlopen("http://127.0.0.1:8080/fixture/stats"))["origin_hits"])'))

        with tempfile.TemporaryDirectory(prefix=prefix) as directory:
            directory = Path(directory)
            directory.chmod(0o755)
            configs = {
                "system-model.cfg.xml": '''<system-model xmlns="http://docs.openrepose.org/repose/system-model/v2.0" rewrite-host-header="false">
  <nodes><node id="verify" hostname="localhost" http-port="8080"/></nodes>
  <filters><filter name="keystone-v2"/><filter name="empty-request-filter"/></filters>
  <destinations><endpoint id="fixture" default="true" protocol="http" hostname="fixture" port="8080"/></destinations>
</system-model>''',
                "keystone-v2.cfg.xml": '''<keystone-v2 xmlns="http://docs.openrepose.org/repose/keystone-v2/v1.0">
  <identity-service uri="http://fixture:8080" set-groups-in-header="false"/>
</keystone-v2>'''}
            mounts = []
            for name, contents in configs.items():
                path = directory / name
                path.write_text(contents)
                path.chmod(0o644)
                mounts += ["--mount", f"type=bind,src={path},dst=/etc/repose/{name},readonly"]
            for enabled in ((False,) if baseline else (False, True)):
                args = ["run", "--detach", "--name", proxy, "--network", network,
                        "--publish", "127.0.0.1::8080", *mounts]
                if enabled:
                    args += ["--env", "JAVA_OPTS=-Drepose.legacyMonitorHost.enabled=true"]
                docker(*args, image)
                port = int(docker("port", proxy, "8080/tcp").rsplit(":", 1)[1])
                for attempt in range(60):
                    try:
                        status, headers, _ = send(port, request(AUTHORITY))
                        if status == 401 and "www-authenticate" in headers:
                            break
                    except (OSError, http.client.HTTPException):
                        pass
                    if attempt == 59:
                        raise RuntimeError("Packaged Keystone filter did not become ready")
                    time.sleep(1)

                def check(raw, expected, fragmented=False):
                    nonlocal checks
                    status, headers, body = send(port, raw, fragmented)
                    assert status == expected, (enabled, raw, status, body)
                    if expected == 401:
                        assert "www-authenticate" in headers, headers
                    checks += 1
                    return json.loads(body) if expected == 200 else None

                before = origin_hits()
                # Exact captured probe: CRLF throughout, terminating blank line.
                check(request(), 401 if enabled else 400)
                check(request(token="invalid-local-token"), 401 if enabled else 400)
                for host in (AUTHORITY, "example.test:8080", "127.0.0.1", "[::1]:8080"):
                    check(request(host), 401)
                malformed = ("example.test/other", "example.test/lb_monitor/", "example.test/lb_monitor?x",
                             "example.test/lb_monitor#x", "example.test//lb_monitor", "user@example.test/lb_monitor",
                             "bad host/lb_monitor", "http://example.test/lb_monitor", "example.test:/lb_monitor", "example.test:bad/lb_monitor",
                             "example.test:65536/lb_monitor", "[::1/lb_monitor", "/lb_monitor",
                             "example.test:", "example.test:bad", "example.test:65536", "[::1", "", None)
                for host in malformed:
                    check(request(host, token=TOKEN), 400)
                for method, target in (("POST", "/"), ("PUT", "/"), ("HEAD", "/"), ("GET", "/lb_monitor"),
                                       ("GET", "/?query"), ("GET", "http://example.test/")):
                    check(request(method=method, target=target, token=TOKEN), 400)
                for extra in ("Host: other.test\r\n", " continuation\r\n",
                              "Content-Length: 0\r\nTransfer-Encoding: chunked\r\n"):
                    check(request(token=TOKEN, extra=extra), 400)
                assert origin_hits() == before, "An unauthenticated or malformed request reached the origin"

                for host in (AUTHORITY, "example.test:8080", "127.0.0.1", "[::1]:8080"):
                    result = check(request(host, target="/unchanged?key=value", token=TOKEN), 200)
                    assert result["target"] == "/unchanged?key=value", result
                    assert result["headers"]["host"] == host, result
                if enabled:
                    for authority in (AUTHORITY, "example.test:8080", "[::1]:8080"):
                        result = check(request(authority + "/lb_monitor", token=TOKEN), 200)
                        assert result["target"] == "/" and result["method"] == "GET", result
                        assert result["headers"]["host"] == authority, result
                        assert result["headers"]["x-authorization"] == "Proxy", result
                    result = check(request(token=TOKEN, extra="Content-Length: 4\r\n") + b"body", 200)
                    control = check(request(AUTHORITY, token=TOKEN, extra="Content-Length: 4\r\n") + b"body", 200)
                    # Repose's existing router may discard GET entities. The
                    # compatibility adapter must preserve that existing behavior.
                    assert result["body"] == control["body"], (result, control)
                    check(request(), 401, fragmented=True)
                    # Multiple requests on one connection exercise parser reset/cache.
                    with socket.create_connection(("127.0.0.1", port), timeout=5) as stream:
                        for host in (LEGACY, "example.test:8080/lb_monitor", AUTHORITY, "bad host"):
                            stream.sendall(request(host, close=False))
                            response = http.client.HTTPResponse(stream)
                            response.begin()
                            assert response.status == (400 if host == "bad host" else 401), (host, response.status)
                            response.read()
                            checks += 1
                print(f"Passed monitor matrix with compatibility {'enabled' if enabled else 'disabled'}", flush=True)
                docker("rm", "--force", "--volumes", proxy)
        print(f"Passed {checks} packaged HTTP checks; authentication and malformed-Host rejection retained.")
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
    if sys.argv[1:] == ["--fixture"]:
        fixture()
    else:
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument("image")
        parser.add_argument("--baseline", action="store_true", help="Only run the disabled compatibility matrix")
        args = parser.parse_args()
        verify(args.image, args.baseline)
