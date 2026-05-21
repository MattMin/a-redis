#!/bin/sh
set -eu

SSH_USER="${SSH_USER:-tunnel}"
SSH_PASSWORD="${SSH_PASSWORD:-tunnel-pass}"
SSH_HOME="/home/${SSH_USER}"
SSH_DIR="${SSH_HOME}/.ssh"
AUTHORIZED_KEYS_FILE="${SSH_DIR}/authorized_keys"

if ! id "${SSH_USER}" >/dev/null 2>&1; then
  adduser -D -s /bin/sh "${SSH_USER}"
fi

echo "${SSH_USER}:${SSH_PASSWORD}" | chpasswd
mkdir -p "${SSH_DIR}"
chmod 700 "${SSH_DIR}"
: > "${AUTHORIZED_KEYS_FILE}"

if [ -d /authorized_keys ]; then
  find /authorized_keys -maxdepth 1 -type f -name '*.pub' -print | sort | while read -r pubkey; do
    cat "${pubkey}" >> "${AUTHORIZED_KEYS_FILE}"
    printf '\n' >> "${AUTHORIZED_KEYS_FILE}"
  done
fi

chmod 600 "${AUTHORIZED_KEYS_FILE}"
chown -R "${SSH_USER}:${SSH_USER}" "${SSH_DIR}"

exec /usr/sbin/sshd -D -e

