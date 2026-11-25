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

python3 -B -m unittest discover -s scripts -p test_stage_container.py

# Gradle only compiles the application archives. Keep context staging outside
# the Gradle task graph to avoid coupling the container filesystem to its producers.
./gradlew --no-daemon --max-workers=2 --stacktrace -PcontainerRelease \
    :repose-aggregator:artifacts:valve:shadowJar \
    :repose-aggregator:artifacts:filter-bundle:ear \
    :repose-aggregator:artifacts:extensions-filter-bundle:ear \
    :repose-aggregator:artifacts:experimental-filter-bundle:ear

python3 -B scripts/stage_container.py "$version"

docker build \
    --label "org.opencontainers.image.source=https://github.com/$repository" \
    --label "org.opencontainers.image.revision=$revision" \
    --label "org.opencontainers.image.version=$version" \
    --tag "$image" "$context"

scripts/verify-container.sh "$image" "$version"
python3 scripts/verify-legacy-monitor-host.py "$image"
python3 scripts/verify-empty-request-filter.py "$image"
