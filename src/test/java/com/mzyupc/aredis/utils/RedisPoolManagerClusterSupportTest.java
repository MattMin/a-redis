package com.mzyupc.aredis.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Protocol;

import java.util.List;

class RedisPoolManagerClusterSupportTest {

    @Test
    void shouldRemapUnreachableClusterEndpointToConfiguredHostWithSamePort() {
        HostAndPort remapped = RedisPoolManager.remapClusterNodeEndpoint(
                new HostAndPort("internal.cluster.local", 7002),
                "127.0.0.1",
                (host, port) -> "127.0.0.1".equals(host) && port == 7002
        );

        Assertions.assertEquals("127.0.0.1", remapped.getHost());
        Assertions.assertEquals(7002, remapped.getPort());
    }

    @Test
    void shouldKeepAnnouncedClusterEndpointWhenItIsReachable() {
        HostAndPort announced = new HostAndPort("redis.example.com", 6380);

        HostAndPort remapped = RedisPoolManager.remapClusterNodeEndpoint(
                announced,
                "127.0.0.1",
                (host, port) -> "redis.example.com".equals(host) && port == 6380
        );

        Assertions.assertEquals("redis.example.com", remapped.getHost());
        Assertions.assertEquals(6380, remapped.getPort());
    }

    @Test
    void shouldExtractMasterEndpointsFromClusterNodesResponse() {
        String clusterNodes = String.join("\n",
                "07c37dfeb2352e0b490f7d12d8fdf7f70e45b65f 192.168.107.3:6379@16379 myself,master - 0 0 1 connected 0-5460",
                "2a2c5f8f5f203f7e1bcb0c85e8f31f3d567dd111 192.168.107.4:6379@16379 master - 0 1716470400000 2 connected 5461-10922",
                "8a3d1d0a9e2df6cc98f9f1ff25ccdd2c2d75b222 192.168.107.5:6379@16379 slave 07c37dfeb2352e0b490f7d12d8fdf7f70e45b65f 0 1716470400000 3 connected"
        );

        List<HostAndPort> endpoints = RedisPoolManager.extractClusterMasterEndpoints(clusterNodes);

        Assertions.assertEquals(2, endpoints.size());
        Assertions.assertEquals("192.168.107.3", endpoints.get(0).getHost());
        Assertions.assertEquals(6379, endpoints.get(0).getPort());
        Assertions.assertEquals("192.168.107.4", endpoints.get(1).getHost());
        Assertions.assertEquals(6379, endpoints.get(1).getPort());
    }

    @Test
    void shouldIgnoreFailedOrNoAddressNodesWhenExtractingMasterEndpoints() {
        String clusterNodes = String.join("\n",
                "07c37dfeb2352e0b490f7d12d8fdf7f70e45b65f 192.168.107.3:6379@16379 master - 0 1716470400000 1 connected 0-5460",
                "2a2c5f8f5f203f7e1bcb0c85e8f31f3d567dd111 192.168.107.4:6379@16379 master,fail - 0 1716470400000 2 connected 5461-10922",
                "8a3d1d0a9e2df6cc98f9f1ff25ccdd2c2d75b222 :0@0 master,noaddr - 0 1716470400000 3 disconnected 10923-16383"
        );

        List<HostAndPort> endpoints = RedisPoolManager.extractClusterMasterEndpoints(clusterNodes);

        Assertions.assertEquals(1, endpoints.size());
        Assertions.assertEquals("192.168.107.3", endpoints.get(0).getHost());
        Assertions.assertEquals(6379, endpoints.get(0).getPort());
    }

    @Test
    void shouldUseFirstArgumentAsKeyForBasicClusterCommand() {
        RedisPoolManager.ClusterCommandRoute route = RedisPoolManager.resolveClusterCommandRoute(
                Protocol.Command.GET,
                "demo:key"
        );

        Assertions.assertFalse(route.isUnsupported());
        Assertions.assertFalse(route.isFirstMaster());
        Assertions.assertEquals(0, route.getKeyArgIndex());
    }

    @Test
    void shouldRouteEvalByFirstScriptKey() {
        RedisPoolManager.ClusterCommandRoute route = RedisPoolManager.resolveClusterCommandRoute(
                Protocol.Command.EVAL,
                "return redis.call('GET', KEYS[1])",
                "1",
                "demo:key"
        );

        Assertions.assertFalse(route.isUnsupported());
        Assertions.assertEquals(2, route.getKeyArgIndex());
    }

    @Test
    void shouldRouteXreadByFirstStreamKey() {
        RedisPoolManager.ClusterCommandRoute route = RedisPoolManager.resolveClusterCommandRoute(
                Protocol.Command.XREAD,
                "COUNT",
                "2",
                "STREAMS",
                "orders-stream",
                "0-0"
        );

        Assertions.assertFalse(route.isUnsupported());
        Assertions.assertEquals(3, route.getKeyArgIndex());
    }

    @Test
    void shouldTreatEvalWithoutKeysAsFirstMasterCommand() {
        RedisPoolManager.ClusterCommandRoute route = RedisPoolManager.resolveClusterCommandRoute(
                Protocol.Command.EVAL,
                "return 'ok'",
                "0"
        );

        Assertions.assertFalse(route.isUnsupported());
        Assertions.assertTrue(route.isFirstMaster());
    }

    @Test
    void shouldRejectUnsupportedClusterConsoleCommand() {
        RedisPoolManager.ClusterCommandRoute route = RedisPoolManager.resolveClusterCommandRoute(
                Protocol.Command.SCAN,
                "0"
        );

        Assertions.assertTrue(route.isUnsupported());
        Assertions.assertTrue(route.getErrorMessage().contains("SCAN"));
    }

    @Test
    void shouldDetectCrossSlotKeysForMultiKeyCommand() {
        Assertions.assertTrue(RedisPoolManager.hasCrossSlotKeys(
                Protocol.Command.MGET,
                "user:1",
                "order:1"
        ));
    }

    @Test
    void shouldAllowMultiKeyCommandWithSameHashTagSlot() {
        Assertions.assertFalse(RedisPoolManager.hasCrossSlotKeys(
                Protocol.Command.MGET,
                "user:{42}:name",
                "user:{42}:email"
        ));
    }

    @Test
    void shouldExtractXreadStreamKeysForCrossSlotCheck() {
        Assertions.assertTrue(RedisPoolManager.hasCrossSlotKeys(
                Protocol.Command.XREAD,
                "COUNT",
                "2",
                "STREAMS",
                "orders-stream",
                "payments-stream",
                "0-0",
                "0-0"
        ));
    }
}
