#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise WADL validation in the exact image that would be published."""
import http.client
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def request(port, method, path):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.request(method, path)
        response = connection.getresponse()
        return response.status, response.read()
    finally:
        connection.close()


def verify(image):
    name = "repose-wadl-" + uuid.uuid4().hex[:10]
    try:
        with tempfile.TemporaryDirectory(prefix=name) as directory:
            directory = Path(directory)
            directory.chmod(0o755)
            configs = {
                "system-model.cfg.xml": '''<system-model xmlns="http://docs.openrepose.org/repose/system-model/v2.0">
  <nodes><node id="verify" hostname="localhost" http-port="8080"/></nodes>
  <filters><filter name="api-validator"/><filter name="echo"/></filters>
  <destinations><endpoint id="unused" default="true" protocol="http" hostname="localhost" port="9999"/></destinations>
</system-model>''',
                "validator.cfg.xml": '''<validators xmlns="http://docs.openrepose.org/repose/validator/v1.0">
  <validator role="default" default="true" wadl="audit.wadl"
             check-well-formed="true" check-grammars="true" check-elements="true" xsd-engine="Xerces"/>
</validators>''',
                "audit.wadl": '''<application xmlns="http://wadl.dev.java.net/2009/02">
  <grammars/>
  <resources base="http://localhost:8080/">
    <resource path="resource"><method name="GET" id="getResource"/></resource>
  </resources>
</application>''',
            }
            mounts = []
            for filename, content in configs.items():
                path = directory / filename
                path.write_text(content, encoding="utf-8")
                path.chmod(0o644)
                mounts.extend(["--mount", f"type=bind,src={path},dst=/etc/repose/{filename},readonly"])
            docker("run", "--detach", "--name", name, "--publish", "127.0.0.1::8080", *mounts, image)
            port = int(docker("port", name, "8080/tcp").rsplit(":", 1)[1])
            for attempt in range(60):
                try:
                    if request(port, "GET", "/resource")[0] == 200:
                        break
                except (OSError, http.client.HTTPException):
                    pass
                if attempt == 59:
                    raise RuntimeError("WADL-enabled Repose did not become ready")
                time.sleep(1)
            for method, path, expected in (("GET", "/resource", 200),
                                           ("GET", "/missing", 404),
                                           ("POST", "/resource", 405)):
                status, body = request(port, method, path)
                if status != expected:
                    raise AssertionError((method, path, expected, status, body))
            print("Passed packaged WADL routing checks: valid request, unknown resource, invalid method")
    except Exception:
        subprocess.run(["docker", "logs", name], check=False)
        raise
    finally:
        subprocess.run(["docker", "rm", "--force", "--volumes", name], check=False,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: verify-wadl.py IMAGE")
    verify(sys.argv[1])
