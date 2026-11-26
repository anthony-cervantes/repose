#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Report observed download URLs and inventory staged JVM archives."""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
import re
from urllib.parse import urlsplit
from zipfile import ZipFile

LEGACY = ('saxon-ee', 'saxon-he', 'xerces-xsd11', 'xercesimpl', 'deproxy',
          'http-delegation', 'scalac-scoverage')
MARKERS = ('net/sf/saxon/', 'com/saxonica/', 'org/apache/xerces/',
           'org/rackspace/deproxy/', 'com/rackspace/httpdelegation/')


def report(log: Path, root: Path) -> None:
    downloads = set()
    with log.open(encoding='utf-8', errors='replace') as stream:
        for line in stream:
            # Gradle --info prints "Downloading URL to ...". Do not report
            # failed metadata lookups as successful downloads or expose credentials.
            match = re.search(r'\bDownloading (https?://\S+)', line)
            if match:
                parsed = urlsplit(match.group(1))
                if parsed.hostname:
                    downloads.add((parsed.hostname, parsed.path))
    print('## Observed Gradle download URLs (not a complete resolution graph)')
    for host, count in sorted(Counter(host for host, _ in downloads).items()):
        print(f'{host}: {count} distinct paths')
    if not downloads:
        print('No download URLs captured; repository provenance is inconclusive.')
    print('\n### Legacy artifact download observations')
    for host, path in sorted(downloads):
        if any(name in path.lower() for name in LEGACY):
            print(f'{host}{path}')
    print('\n## Staged archive inventory')
    if not root.is_dir():
        print('Staged filesystem unavailable; archive inventory not produced.')
        return
    for archive in sorted(root.rglob('*')):
        if archive.suffix not in ('.jar', '.ear') or not archive.is_file():
            continue
        with archive.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        print(f'{archive.relative_to(root)} SHA-256 {digest}')
        with ZipFile(archive) as zipped:
            entries = zipped.namelist()
            nested = sorted(name for name in entries if name.lower().endswith('.jar'))
            print(f'  Embedded JARs: {len(nested)}')
            for name in nested:
                if any(legacy in name.lower() for legacy in LEGACY):
                    print(f'  {name}')
            for marker in MARKERS:
                if any(name.startswith(marker) for name in entries):
                    print(f'  Contains classes/resources: {marker}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('rootfs', type=Path)
    args = parser.parse_args()
    report(args.log, args.rootfs)
