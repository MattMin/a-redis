#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SSH_DIR="${BASE_DIR}/ssh/client"
FORCE="${1:-}"

mkdir -p "${SSH_DIR}"

if [ "${FORCE}" = "--force" ]; then
  rm -f \
    "${SSH_DIR}/id_ed25519" \
    "${SSH_DIR}/id_ed25519.pub" \
    "${SSH_DIR}/id_ed25519_pp" \
    "${SSH_DIR}/id_ed25519_pp.pub"
fi

if [ ! -f "${SSH_DIR}/id_ed25519" ] || [ ! -f "${SSH_DIR}/id_ed25519.pub" ]; then
  ssh-keygen -t ed25519 -f "${SSH_DIR}/id_ed25519" -N '' -C 'aredis-local-tunnel' >/dev/null
fi

if [ ! -f "${SSH_DIR}/id_ed25519_pp" ] || [ ! -f "${SSH_DIR}/id_ed25519_pp.pub" ]; then
  ssh-keygen -t ed25519 -f "${SSH_DIR}/id_ed25519_pp" -N 'aredis-passphrase' -C 'aredis-local-tunnel-pp' >/dev/null
fi

chmod 600 "${SSH_DIR}/id_ed25519" "${SSH_DIR}/id_ed25519_pp"
chmod 644 "${SSH_DIR}/id_ed25519.pub" "${SSH_DIR}/id_ed25519_pp.pub"

printf 'SSH keys are ready in %s\n' "${SSH_DIR}"

