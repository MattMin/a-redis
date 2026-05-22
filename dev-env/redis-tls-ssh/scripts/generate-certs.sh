#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
CERT_DIR="${BASE_DIR}/certs"
SERVER_EXT_FILE="${CERT_DIR}/redis-server-ext.cnf"
CLIENT_EXT_FILE="${CERT_DIR}/client-ext.cnf"
CA_TRUSTSTORE_PASSWORD="aredis-ca-pass"
CLIENT_KEYSTORE_PASSWORD="aredis-client-pass"
FORCE="${1:-}"

mkdir -p "${CERT_DIR}"

if [ "${FORCE}" = "--force" ]; then
  rm -f \
    "${CERT_DIR}/ca.key" \
    "${CERT_DIR}/ca.crt" \
    "${CERT_DIR}/ca.srl" \
    "${CERT_DIR}/ca-truststore.p12" \
    "${CERT_DIR}/redis-server.key" \
    "${CERT_DIR}/redis-server.csr" \
    "${CERT_DIR}/redis-server.crt" \
    "${CERT_DIR}/client.key" \
    "${CERT_DIR}/client.csr" \
    "${CERT_DIR}/client.crt" \
    "${CERT_DIR}/client-keystore.p12"
fi

if [ ! -f "${CERT_DIR}/ca.key" ] || [ ! -f "${CERT_DIR}/ca.crt" ]; then
  openssl genrsa -out "${CERT_DIR}/ca.key" 4096
  openssl req -x509 -new -nodes \
    -key "${CERT_DIR}/ca.key" \
    -sha256 \
    -days 3650 \
    -out "${CERT_DIR}/ca.crt" \
    -subj "/C=CN/ST=Local/L=Local/O=A-Redis Dev/CN=A-Redis Local Dev CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -addext "subjectKeyIdentifier=hash"
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
    -extfile "${SERVER_EXT_FILE}" \
    -extensions v3_req
fi

if [ ! -f "${CERT_DIR}/client.key" ] || [ ! -f "${CERT_DIR}/client.crt" ]; then
  openssl genrsa -out "${CERT_DIR}/client.key" 2048
  openssl req -new \
    -key "${CERT_DIR}/client.key" \
    -out "${CERT_DIR}/client.csr" \
    -subj "/C=CN/ST=Local/L=Local/O=A-Redis Dev/CN=aredis-local-client"
  openssl x509 -req \
    -in "${CERT_DIR}/client.csr" \
    -CA "${CERT_DIR}/ca.crt" \
    -CAkey "${CERT_DIR}/ca.key" \
    -CAcreateserial \
    -out "${CERT_DIR}/client.crt" \
    -days 825 \
    -sha256 \
    -extfile "${CLIENT_EXT_FILE}" \
    -extensions v3_req
fi

if [ ! -f "${CERT_DIR}/ca-truststore.p12" ]; then
  keytool -importcert \
    -noprompt \
    -alias aredis-local-ca \
    -file "${CERT_DIR}/ca.crt" \
    -keystore "${CERT_DIR}/ca-truststore.p12" \
    -storetype PKCS12 \
    -storepass "${CA_TRUSTSTORE_PASSWORD}"
fi

if [ ! -f "${CERT_DIR}/client-keystore.p12" ]; then
  openssl pkcs12 -export \
    -in "${CERT_DIR}/client.crt" \
    -inkey "${CERT_DIR}/client.key" \
    -certfile "${CERT_DIR}/ca.crt" \
    -name aredis-local-client \
    -out "${CERT_DIR}/client-keystore.p12" \
    -passout pass:"${CLIENT_KEYSTORE_PASSWORD}"
fi

rm -f "${CERT_DIR}/redis-server.csr" "${CERT_DIR}/client.csr"
chmod 600 "${CERT_DIR}/ca.key" "${CERT_DIR}/redis-server.key" "${CERT_DIR}/client.key"
chmod 644 \
  "${CERT_DIR}/ca.crt" \
  "${CERT_DIR}/redis-server.crt" \
  "${CERT_DIR}/client.crt" \
  "${CERT_DIR}/ca-truststore.p12" \
  "${CERT_DIR}/client-keystore.p12"

printf 'Certificates are ready in %s\n' "${CERT_DIR}"

