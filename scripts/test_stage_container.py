# SPDX-License-Identifier: Apache-2.0
import tempfile
import unittest
from pathlib import Path

from stage_container import BUNDLES, stage


class StageContainerTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.artifacts = self.root / "repose-aggregator/artifacts"
        self.version = "10.1.2"
        self.write(self.root / "LICENSE.txt", b"license")
        self.write(self.root / "CONTRIBUTORS.txt", b"contributors")
        self.write(self.artifacts / "docker/src/docker/resources/ghcr/Dockerfile", b"FROM scratch")
        valve = self.artifacts / "valve"
        self.write(valve / "build/libs/repose.jar", b"jar")
        self.write(valve / "src/config/scripts/clean-repose-deploy", b"helper")
        self.write(valve / "src/config/filters/container.cfg.xml", b"@docsHome@\n")
        self.write(valve / "src/config/docs/DEPENDENCIES.txt", b"dependencies")
        for name in BUNDLES:
            bundle = self.artifacts / name
            self.write(bundle / f"build/distributions/{name}-{self.version}.ear", name.encode())
            self.write(bundle / f"src/config/filters/{name}.cfg.xml", b"example")
            self.write(bundle / "src/config/docs/DEPENDENCIES.txt", b"dependencies")

    @staticmethod
    def write(path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    def test_runtime_layout_and_clean_restage(self):
        context = stage(self.root, self.version)
        rootfs = context / "rootfs"
        self.assertEqual((rootfs / "usr/share/repose/repose.jar").read_bytes(), b"jar")
        for name in BUNDLES:
            self.assertTrue((rootfs / f"usr/share/repose/filters/{name}-{self.version}.ear").is_file())
            self.assertTrue((rootfs / f"etc/repose/examples/{name}.cfg.xml").is_file())
            self.assertTrue((rootfs / f"usr/share/doc/repose-{name}/LICENSE.txt").is_file())
        expected = b"http://www.openrepose.org/versions/10.1.2/index.html\n"
        self.assertEqual((rootfs / "etc/repose/container.cfg.xml").read_bytes(), expected)
        self.assertEqual((rootfs / "etc/repose/examples/container.cfg.xml").read_bytes(), expected)
        self.write(context / "stale.deb", b"old")
        stage(self.root, self.version)
        self.assertFalse((context / "stale.deb").exists())

    def test_missing_artifact_is_rejected(self):
        (self.artifacts / "valve/build/libs/repose.jar").unlink()
        with self.assertRaisesRegex(FileNotFoundError, "repose.jar"):
            stage(self.root, self.version)

    def test_duplicate_examples_are_rejected(self):
        self.write(self.artifacts / "filter-bundle/src/config/filters/container.cfg.xml", b"duplicate")
        with self.assertRaisesRegex(ValueError, "Duplicate container input"):
            stage(self.root, self.version)

    def test_invalid_version_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid Repose version"):
            stage(self.root, "../invalid")


if __name__ == "__main__":
    unittest.main()
