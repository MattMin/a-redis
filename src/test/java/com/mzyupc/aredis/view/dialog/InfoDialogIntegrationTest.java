package com.mzyupc.aredis.view.dialog;

import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;

class InfoDialogIntegrationTest {

    private RedisPoolManager redisPoolManager;

    @AfterEach
    void tearDown() {
        if (redisPoolManager != null) {
            redisPoolManager.invalidate();
        }
    }

    @Test
    void shouldFetchAllInfoSectionsFromLocalRedisCluster() {
        Assumptions.assumeTrue(isPortOpen("127.0.0.1", 7001),
                "Local Redis Cluster is not running on 127.0.0.1:7001");

        redisPoolManager = new RedisPoolManager(ConnectionInfo.builder()
                .id(UUID.randomUUID().toString())
                .name("local-redis-cluster")
                .url("127.0.0.1")
                .port("7001")
                .clusterMode(true)
                .build());

        for (String section : InfoDialog.SECTIONS) {
            String info = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                try (Jedis jedis = redisPoolManager.getJedis(0)) {
                    Assertions.assertNotNull(jedis, "Failed to get Jedis for section: " + section);
                    return jedis.info(section);
                }
            }, "Fetching INFO section timed out: " + section);

            Assertions.assertNotNull(info, "INFO response should not be null for section: " + section);
            Assertions.assertFalse(info.trim().isEmpty(), "INFO response should not be empty for section: " + section);
            Assertions.assertTrue(info.startsWith("#") || info.contains(":"),
                    "INFO response format is unexpected for section: " + section + ", actual: " + info);
        }
    }

    @Test
    void shouldSupportRepeatedInfoRefreshesFromLocalRedisCluster() {
        Assumptions.assumeTrue(isPortOpen("127.0.0.1", 7001),
                "Local Redis Cluster is not running on 127.0.0.1:7001");

        redisPoolManager = new RedisPoolManager(ConnectionInfo.builder()
                .id(UUID.randomUUID().toString())
                .name("local-redis-cluster")
                .url("127.0.0.1")
                .port("7001")
                .clusterMode(true)
                .build());

        for (int round = 0; round < 2; round++) {
            final int currentRound = round;
            for (String section : InfoDialog.SECTIONS) {
                final String currentSection = section;
                Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                    try (Jedis jedis = redisPoolManager.getJedis(0)) {
                        Assertions.assertNotNull(jedis, "Failed to get Jedis for round=" + currentRound + ", section=" + currentSection);
                        String info = jedis.info(currentSection);
                        Assertions.assertNotNull(info, "INFO response should not be null for round=" + currentRound + ", section=" + currentSection);
                        Assertions.assertFalse(info.trim().isEmpty(), "INFO response should not be empty for round=" + currentRound + ", section=" + currentSection);
                    }
                }, "Repeated INFO fetch timed out for round=" + currentRound + ", section=" + currentSection);
            }
        }
    }

    private boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

