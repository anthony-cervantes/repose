#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Separate user home: do not use restored dependency caches or global init scripts.
audit_dir=$(mktemp -d)
trap 'rm -rf "$audit_dir"' EXIT
export GRADLE_USER_HOME="$audit_dir/gradle"
export REPOSE_GRADLE_AUDIT=1
log="$audit_dir/gradle.log"
status=0

# The normal container validation is still required. Do not publish any image.
if bash scripts/build-container.sh repose:audit 2>&1 | tee "$log"; then
    # A small real Scala module exercises coverage; compile the test framework
    # separately to resolve Deproxy without running the entire integration suite.
    if ! ./gradlew --no-daemon --max-workers=2 --info --console=plain --stacktrace --continue \
        -PcontainerRelease \
        :repose-aggregator:external:http-delegation:reportTestScoverage \
        :repose-aggregator:tests:functional-test-framework:compileGroovy \
        2>&1 | tee -a "$log"; then
        status=1
    fi
else
    status=1
fi

rootfs="repose-aggregator/artifacts/docker/build/docker/ghcr/rootfs/usr/share/repose"
if ! grep -q "^Staged Docker context:" "$log"; then
    # Never report a stale context left by a previous local build.
    rootfs="$audit_dir/no-stage"
fi
python3 -B scripts/report_dependency_provenance.py "$log" "$rootfs"
# Fail this migration audit if any of the three retired coordinates is still downloaded.
if grep -E 'Downloading https?://[^ ]*/(xerces/xerces-xsd11/|xml-apis/xml-apis/2\.0\.2-rax/|org/eclipse/wst/xml/xpath2/org\.eclipse\.wst\.xml\.xpath2\.processor/)' "$log"; then
    echo "Retired XML dependency downloaded" >&2
    status=1
fi
exit "$status"
