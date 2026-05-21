#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
RUNTIME_DIR="${BASE_DIR}/.runtime"
SOCKET_FILE="${RUNTIME_DIR}/ssh-control.sock"
TUNNEL_PORT=6391

mkdir -p "${RUNTIME_DIR}"
rm -f "${SOCKET_FILE}"

cleanup() {
  if [ -S "${SOCKET_FILE}" ]; then
    ssh -S "${SOCKET_FILE}" -O exit -p 2222 tunnel@localhost >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

cd "${BASE_DIR}"

echo '1/4 Verifying TLS certificate hostname on localhost...'
openssl s_client \
  -connect localhost:6380 \
  -servername localhost \
  -verify_hostname localhost \
  -CAfile "${BASE_DIR}/certs/ca.crt" \
  </dev/null 2>/dev/null | grep -q 'Verify return code: 0 (ok)'

echo '2/4 Testing Redis TLS via local CA...'
docker run --rm \
  -v "${BASE_DIR}/certs:/certs:ro" \
  redis:7.2-alpine \
  redis-cli -h host.docker.internal -p 6380 --tls --cacert /certs/ca.crt -a redis-tls-pass PING

echo '3/4 Opening SSH tunnel with the generated key...'
ssh \
  -M \
  -S "${SOCKET_FILE}" \
  -f \
  -N \
  -L "${TUNNEL_PORT}:redis-private:6379" \
  -o ExitOnForwardFailure=yes \
  -o StrictHostKeyChecking=no \
  -o UserKnownHostsFile=/dev/null \
  -i "${BASE_DIR}/ssh/client/id_ed25519" \
  -p 2222 \
  tunnel@localhost

sleep 1

echo '4/4 Testing Redis through the SSH tunnel...'
docker run --rm \
  redis:7.2-alpine \
  redis-cli -h host.docker.internal -p "${TUNNEL_PORT}" -a redis-private-pass PING

echo 'Smoke test passed.'

