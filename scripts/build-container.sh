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

./gradlew --no-daemon --max-workers=2 -PcontainerRelease \
    :repose-aggregator:artifacts:docker:stageGhcrContext

docker build \
    --label "org.opencontainers.image.source=https://github.com/$repository" \
    --label "org.opencontainers.image.revision=$revision" \
    --label "org.opencontainers.image.version=$version" \
    --tag "$image" "$context"

scripts/verify-container.sh "$image" "$version"
python3 scripts/verify-legacy-monitor-host.py "$image"
python3 scripts/verify-empty-request-filter.py "$image"
