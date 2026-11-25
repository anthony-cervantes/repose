#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Stage the Docker context from already-built Gradle archives."""
import re
import shutil
import sys
import tempfile
from pathlib import Path

BUNDLES = ("filter-bundle", "extensions-filter-bundle", "experimental-filter-bundle")


def stage(root: Path, version: str) -> Path:
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError(f"Invalid Repose version: {version}")

    artifacts = root / "repose-aggregator/artifacts"
    valve = artifacts / "valve"
    context = artifacts / "docker/build/docker/ghcr"
    context.parent.mkdir(parents=True, exist_ok=True)
    docs_home = f"http://www.openrepose.org/versions/{version}/index.html".encode()

    # Stage into a fresh directory; do not reuse stale files from prior builds.
    with tempfile.TemporaryDirectory(prefix="ghcr-", dir=context.parent) as directory:
        staged = Path(directory) / "context"
        filesystem = staged / "rootfs"

        def copy(source: Path, target: Path, substitute: bool = False) -> None:
            if not source.is_file():
                raise FileNotFoundError(f"Required container input missing: {source}")
            if target.exists():
                raise ValueError(f"Duplicate container input: {target.relative_to(staged)}")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            if substitute:
                target.write_bytes(target.read_bytes().replace(b"@docsHome@", docs_home))

        def configs(project: Path, target: Path, substitute: bool = False) -> None:
            source = project / "src/config/filters"
            if not source.is_dir():
                raise FileNotFoundError(f"Required config directory missing: {source}")
            for item in sorted(source.rglob("*")):
                if item.is_file():
                    copy(item, target / item.relative_to(source), substitute)

        def notices(project: Path, package: str) -> None:
            target = filesystem / "usr/share/doc" / package
            for source in (root / "LICENSE.txt", root / "CONTRIBUTORS.txt",
                           project / "src/config/docs/DEPENDENCIES.txt"):
                copy(source, target / source.name)

        copy(artifacts / "docker/src/docker/resources/ghcr/Dockerfile", staged / "Dockerfile")
        application = filesystem / "usr/share/repose"
        config = filesystem / "etc/repose"
        copy(valve / "build/libs/repose.jar", application / "repose.jar")
        copy(valve / "src/config/scripts/clean-repose-deploy", application / "clean-repose-deploy")
        configs(valve, config, substitute=True)
        configs(valve, config / "examples", substitute=True)
        notices(valve, "repose")

        for name in BUNDLES:
            project = artifacts / name
            archive = f"{name}-{version}.ear"
            copy(project / "build/distributions" / archive, application / "filters" / archive)
            configs(project, config / "examples")
            notices(project, f"repose-{name}")

        if context.exists():
            shutil.rmtree(context)
        staged.rename(context)

    return context


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: stage_container.py VERSION")
    try:
        print(f"Staged Docker context: {stage(Path(__file__).resolve().parents[1], sys.argv[1])}")
    except (OSError, ValueError) as error:
        sys.exit(str(error))
