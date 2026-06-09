#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

cd "${BASE_DIR}"

echo '1/4 Verifying the CA password protected PKCS12 truststore can be opened...'
keytool -list \
  -keystore "${BASE_DIR}/certs/ca-truststore.p12" \
  -storetype PKCS12 \
  -storepass aredis-ca-pass >/dev/null

echo '2/4 Verifying the client certificate PKCS12 keystore can be opened with the key password...'
keytool -list \
  -keystore "${BASE_DIR}/certs/client-keystore.p12" \
  -storetype PKCS12 \
  -storepass aredis-client-pass >/dev/null

echo '3/4 Verifying the Redis server certificate hostname entries for mTLS...'
openssl x509 -in "${BASE_DIR}/certs/redis-server.crt" -checkhost localhost -noout >/dev/null
openssl x509 -in "${BASE_DIR}/certs/redis-server.crt" -checkhost host.docker.internal -noout >/dev/null

echo '4/4 Testing Redis mTLS with client cert + key...'
docker run --rm \
  -v "${BASE_DIR}/certs:/certs:ro" \
  redis:7.2-alpine \
  redis-cli -h host.docker.internal -p 6381 --tls --cacert /certs/ca.crt --cert /certs/client.crt --key /certs/client.key -a redis-mtls-pass PING

echo 'mTLS smoke test passed.'

