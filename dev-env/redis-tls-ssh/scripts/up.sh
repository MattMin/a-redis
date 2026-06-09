#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

"${BASE_DIR}/scripts/generate-certs.sh"
"${BASE_DIR}/scripts/generate-ssh-keys.sh"

cd "${BASE_DIR}"
docker compose up -d --build --force-recreate

echo 'Local Redis test environment is starting.'
echo 'Run scripts/smoke-test.sh and scripts/test-mtls.sh to verify SSH tunnel and TLS connectivity.'

