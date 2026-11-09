#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

image="${1:?Usage: verify-container.sh IMAGE EXPECTED_VERSION}"
expected_version="${2:?Expected application version is required}"
smoke_dir="$(mktemp -d)"
smoke_container="repose-image-smoke-$$"
cleanup() {
    docker rm --force --volumes "$smoke_container" >/dev/null 2>&1 || true
    rm -rf "$smoke_dir"
}
trap cleanup EXIT

docker run --rm --entrypoint java "$image" -version >"$smoke_dir/java.txt" 2>&1
cat "$smoke_dir/java.txt"
grep -Eq 'version "27([."]|$)' "$smoke_dir/java.txt"
# The current Valve launcher returns 1 after printing informational options.
set +e
docker run --rm --entrypoint java "$image" -jar /usr/share/repose/repose.jar --version >"$smoke_dir/version.txt" 2>&1
version_status=$?
set -e
cat "$smoke_dir/version.txt"
if [[ "$version_status" != 0 && "$version_status" != 1 ]]; then
    exit "$version_status"
fi
grep -F "$expected_version" "$smoke_dir/version.txt"

cat >"$smoke_dir/system-model.cfg.xml" <<'XML'
<system-model xmlns="http://docs.openrepose.org/repose/system-model/v2.0">
    <nodes>
        <node id="smoke-node" hostname="localhost" http-port="8080"/>
    </nodes>
    <filters>
        <filter name="echo"/>
    </filters>
    <destinations>
        <endpoint id="unused" default="true" protocol="http" hostname="localhost" port="9999"/>
    </destinations>
</system-model>
XML
chmod 755 "$smoke_dir"
chmod 644 "$smoke_dir/system-model.cfg.xml"
docker run --detach --name "$smoke_container" \
    --mount "type=bind,src=$smoke_dir/system-model.cfg.xml,dst=/etc/repose/system-model.cfg.xml,readonly" \
    "$image" >/dev/null

for attempt in {1..45}; do
    if docker exec "$smoke_container" wget --server-response \
        --header='X-Repose-Smoke: ready' -O /dev/null http://127.0.0.1:8080/ \
        >"$smoke_dir/http.txt" 2>&1 && \
        grep -q 'HTTP/1.1 200' "$smoke_dir/http.txt" && \
        grep -qi 'X-Repose-Smoke: ready' "$smoke_dir/http.txt"; then
        cat "$smoke_dir/http.txt"
        printf 'Java 27, Repose %s, and packaged Echo filter passed.\n' "$expected_version"
        exit 0
    fi
    if [[ "$(docker inspect --format '{{.State.Running}}' "$smoke_container")" != true ]]; then
        break
    fi
    sleep 1
done
cat "$smoke_dir/http.txt" || true
docker logs "$smoke_container"
exit 1
