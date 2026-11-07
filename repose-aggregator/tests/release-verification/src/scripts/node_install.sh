#!/bin/sh
set -eu

repose_node_version=${REPOSE_NODE_VERSION:-22.23.4}
case "$(uname -m)" in
    x86_64) repose_node_arch=x64 ;;
    aarch64|arm64) repose_node_arch=arm64 ;;
    *) echo "Unsupported Node.js architecture" >&2; exit 1 ;;
esac

repose_node_archive="node-v$repose_node_version-linux-$repose_node_arch"
repose_node_staging=$(mktemp -d)
trap 'rm -rf "$repose_node_staging"' 0
cd "$repose_node_staging"
wget -q "https://nodejs.org/dist/v$repose_node_version/$repose_node_archive.tar.gz"
wget -q "https://nodejs.org/dist/v$repose_node_version/SHASUMS256.txt"
sha256sum --check --ignore-missing SHASUMS256.txt

mkdir -p /usr/local/lib /usr/local/bin
tar -xzf "$repose_node_archive.tar.gz" -C /usr/local/lib
ln -sfn "/usr/local/lib/$repose_node_archive" /usr/local/lib/node-current
ln -sfn ../lib/node-current/bin/node /usr/local/bin/node
ln -sfn ../lib/node-current/bin/npm /usr/local/bin/npm
