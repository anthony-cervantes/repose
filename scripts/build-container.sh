#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

image="${1:?Usage: build-container.sh IMAGE [EXPECTED_VERSION]}"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
version=$(sed -n "s/.*def baseVersion = '\([^']*\)'.*/\1/p" build.gradle)
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo 'Invalid baseVersion in build.gradle' >&2
    exit 1
fi
if [[ $# -ge 2 && "$2" != "$version" ]]; then
    echo 'Release tag does not match build.gradle' >&2
    exit 1
fi

revision="$(git rev-parse HEAD)"
repository="${GITHUB_REPOSITORY:-anthony-cervantes/repose}"
context="repose-aggregator/artifacts/docker/build/docker/ghcr"

# Catch stale declarations even when the Docker build never resolves test configurations.
if git grep -n -E 'xerces:xerces-xsd11|xml-apis:xml-apis|org\.eclipse\.wst\.xml\.xpath2\.processor' -- '*.gradle' '*.gradle.kts' '*.toml'; then
    echo 'Retired XML dependency declared in Gradle build files' >&2
    exit 1
else
    grep_status=$?
    if [[ "$grep_status" != 1 ]]; then
        exit "$grep_status"
    fi
fi

python3 -B -m unittest discover -s scripts -p test_stage_container.py

# Gradle only compiles the application archives. Keep context staging outside
# the Gradle task graph to avoid coupling the container filesystem to its producers.
gradle_args=(--no-daemon --max-workers=2 --stacktrace -PcontainerRelease)
if [[ "${REPOSE_GRADLE_AUDIT:-0}" == 1 ]]; then
    gradle_args+=(--info --console=plain --no-build-cache --rerun-tasks)
fi
./gradlew "${gradle_args[@]}" \
    :repose-aggregator:artifacts:valve:shadowJar \
    :repose-aggregator:artifacts:filter-bundle:ear \
    :repose-aggregator:artifacts:extensions-filter-bundle:ear \
    :repose-aggregator:artifacts:experimental-filter-bundle:ear

python3 -B scripts/stage_container.py "$version"
python3 -B scripts/verify-xml-runtime.py "$context/rootfs/usr/share/repose"

docker build \
    --label "org.opencontainers.image.source=https://github.com/$repository" \
    --label "org.opencontainers.image.revision=$revision" \
    --label "org.opencontainers.image.version=$version" \
    --tag "$image" "$context"

scripts/verify-container.sh "$image" "$version"
python3 -B scripts/verify-wadl.py "$image"
python3 scripts/verify-legacy-monitor-host.py "$image"
python3 scripts/verify-empty-request-filter.py "$image"
