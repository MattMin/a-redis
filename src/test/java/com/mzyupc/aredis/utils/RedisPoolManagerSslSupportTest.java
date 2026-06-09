package com.mzyupc.aredis.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RedisPoolManagerSslSupportTest {

    @Test
    void shouldAcceptSupportedClientKeystoreExtensions() {
        Assertions.assertTrue(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.jks"));
        Assertions.assertTrue(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.p12"));
        Assertions.assertTrue(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.PFX"));
    }

    @Test
    void shouldRejectCertificateFilesAsClientKeystore() {
        Assertions.assertFalse(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.crt"));
        Assertions.assertFalse(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.cer"));
        Assertions.assertFalse(RedisPoolManager.isSupportedClientKeystorePath("/tmp/client.pem"));
        Assertions.assertFalse(RedisPoolManager.isSupportedClientKeystorePath(null));
    }
}

