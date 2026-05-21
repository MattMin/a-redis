#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CERT_DIR="${BASE_DIR}/certs"
EXT_FILE="${CERT_DIR}/redis-server-ext.cnf"
FORCE="${1:-}"

mkdir -p "${CERT_DIR}"

if [ "${FORCE}" = "--force" ]; then
  rm -f \
    "${CERT_DIR}/ca.key" \
    "${CERT_DIR}/ca.crt" \
    "${CERT_DIR}/ca.srl" \
    "${CERT_DIR}/redis-server.key" \
    "${CERT_DIR}/redis-server.csr" \
    "${CERT_DIR}/redis-server.crt"
fi

if [ ! -f "${CERT_DIR}/ca.key" ] || [ ! -f "${CERT_DIR}/ca.crt" ]; then
  openssl genrsa -out "${CERT_DIR}/ca.key" 4096
  openssl req -x509 -new -nodes \
    -key "${CERT_DIR}/ca.key" \
    -sha256 \
    -days 3650 \
    -out "${CERT_DIR}/ca.crt" \
    -subj "/C=CN/ST=Local/L=Local/O=A-Redis Dev/CN=A-Redis Local Dev CA"
fi

if [ ! -f "${CERT_DIR}/redis-server.key" ] || [ ! -f "${CERT_DIR}/redis-server.crt" ]; then
  openssl genrsa -out "${CERT_DIR}/redis-server.key" 2048
  openssl req -new \
    -key "${CERT_DIR}/redis-server.key" \
    -out "${CERT_DIR}/redis-server.csr" \
    -subj "/C=CN/ST=Local/L=Local/O=A-Redis Dev/CN=localhost"
  openssl x509 -req \
    -in "${CERT_DIR}/redis-server.csr" \
    -CA "${CERT_DIR}/ca.crt" \
    -CAkey "${CERT_DIR}/ca.key" \
    -CAcreateserial \
    -out "${CERT_DIR}/redis-server.crt" \
    -days 825 \
    -sha256 \
    -extfile "${EXT_FILE}" \
    -extensions v3_req
fi

rm -f "${CERT_DIR}/redis-server.csr"
chmod 600 "${CERT_DIR}/ca.key" "${CERT_DIR}/redis-server.key"
chmod 644 "${CERT_DIR}/ca.crt" "${CERT_DIR}/redis-server.crt"

printf 'Certificates are ready in %s\n' "${CERT_DIR}"

