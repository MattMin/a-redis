#!/bin/sh
set -eu

echo '1/2 Verifying SSH password authentication...'
echo '2/2 Verifying the jump host can reach redis-private over password-authenticated SSH...'
docker run --rm \
  alpine:3.20 sh -lc "apk add --no-cache openssh-client sshpass >/dev/null && sshpass -p tunnel-pass ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -p 2222 tunnel@host.docker.internal 'sh -lc \"nc -z redis-private 6379 && echo redis-private reachable\"'"

echo 'SSH password authentication test passed.'

