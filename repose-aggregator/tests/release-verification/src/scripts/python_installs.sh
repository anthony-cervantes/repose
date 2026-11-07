#!/bin/sh
set -eu

python3 -m venv /opt/repose-verification-venv
/opt/repose-verification-venv/bin/python -m pip install -r "$(dirname "$0")/requirements.txt"
