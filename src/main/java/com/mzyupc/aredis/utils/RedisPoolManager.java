package com.mzyupc.aredis.utils;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.Keyspace;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.Pool;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 */
@Slf4j
public class RedisPoolManager implements Disposable {

    private static final JedisPoolConfig JEDIS_POOL_CONFIG;

    static {
        JEDIS_POOL_CONFIG = new JedisPoolConfig();
        //连接耗尽时是否阻塞, false报异常,ture阻塞直到超时, 默认true
        JEDIS_POOL_CONFIG.setBlockWhenExhausted(false);
        //最大空闲连接数, 默认8个
        JEDIS_POOL_CONFIG.setMaxIdle(10);
        //最小空闲连接数, 默认0
        JEDIS_POOL_CONFIG.setMinIdle(0);
        //最大连接数, 默认8个
        JEDIS_POOL_CONFIG.setMaxTotal(100);
        //对象空闲多久后逐出, 当空闲时间>该值 且 空闲连接>最大空闲数 时直接逐出,不再根据MinEvictableIdleTimeMillis判断  (默认逐出策略)
        JEDIS_POOL_CONFIG.setSoftMinEvictableIdleTime(Duration.ofSeconds(60));
        //检查链接是否有效
        JEDIS_POOL_CONFIG.setTestOnBorrow(true);
    }

    private final String host;
    private final Integer port;
    private final String user;
    private final String password;
    private final Integer db;
    private JedisPool pool = null;

    public RedisPoolManager(ConnectionInfo connectionInfo) {
        this.host = connectionInfo.getUrl();
        this.port = Integer.parseInt(connectionInfo.getPort());
        this.user = connectionInfo.getUser();
        this.password = connectionInfo.getPassword();
        this.db = Protocol.DEFAULT_DATABASE;
    }

    public static TestConnectionResult getTestConnectionResult(String host, Integer port, String user, String password) {
        try (Pool<Jedis> pool = new JedisPool(JEDIS_POOL_CONFIG, host, port, Protocol.DEFAULT_TIMEOUT, user, password);
             Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return TestConnectionResult.builder()
                        .success(true)
                        .msg("Succeeded")
                        .build();
            }
            return TestConnectionResult.builder()
                    .success(false)
                    .msg(pong)
                    .build();
        } catch (Exception e) {
            return TestConnectionResult.builder()
                    .success(false)
                    .msg(e.getCause().getMessage())
                    .build();
        }
    }

    /**
     * 连接池是否实例化
     *
     * @return
     */
    public boolean isValidate() {
        return pool != null;
    }

    /**
     * 关闭连接池
     */
    public void invalidate() {
        if (isValidate()) {
            this.pool.close();
            this.pool = null;
        }
    }

    private synchronized JedisPool getJedisPool() {
        if (pool == null) {
            initPool();
        }
        return pool;
    }

    @Override
    public void dispose() {
        this.invalidate();
    }

    /**
     * 执行redis命令
     *
     * @param db
     * @param command
     * @param args
     * @return
     */
    public List<String> execRedisCommand(int db, String command, String... args) {
        try (Jedis jedis = getJedis(db)) {
            Protocol.Command cmd = Protocol.Command.valueOf(command.toUpperCase());
            if (jedis == null) {
                return Lists.newArrayList();
            }

            Connection client = jedis.getClient();
//            processArgs(cmd, args);
            client.sendCommand(cmd, args);
            try {
                List<String> respList = new ArrayList<>();
                Object response = client.getOne();
                if (response == null) {
                    return Collections.singletonList("null");
                }
                if (response instanceof List) {
                    for (Object itemResp : ((List) response)) {
                        if (itemResp == null) {
                            respList.add("null");
                        } else {
                            if (itemResp instanceof List) {
                                List<byte[]> itemList = (List<byte[]>) itemResp;
                                List<String> strings = itemList.stream().map(String::new).collect(Collectors.toList());
                                respList.add(String.join("\n", strings));
                            } else if (itemResp instanceof byte[]) {
                                respList.add(new String((byte[]) itemResp));
                            } else {
                                respList.add(JSON.toJSONString(itemResp));
                            }
                        }
                    }
                    return respList;
                }

                if (response instanceof Long) {
                    return Collections.singletonList(response + "");
                }

                if (cmd == Protocol.Command.DUMP) {
                   return Collections.singletonList(getPrintableString((byte[]) response));
                }

                return Collections.singletonList(new String((byte[]) response));

            } catch (JedisException e) {
                return Collections.singletonList(e.getMessage());
            }
        } catch (IllegalArgumentException e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    private String getPrintableString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            // printable ascii characters
            if (b > 31 && b < 127) {
                sb.append((char)b);
            } else {
                sb.append(String.format("\\x%02x", (int)b & 0xff));
            }
        }
        return sb.toString();
    }

    private void initPool() {
        try {
            pool = new JedisPool(JEDIS_POOL_CONFIG, host, port, Protocol.DEFAULT_TIMEOUT, user, password);
        } catch (Exception e) {
            log.error("初始化redis pool失败", e);
            ErrorDialog.show("Failed to initialize the Redis pool." + "\n" + e.getMessage());
        }
    }

    /**
     * 获取redis连接
     *
     * @return
     */
    @Nullable
    public Jedis getJedis(int db) {
        try {
            Jedis resource = getJedisPool().getResource();
            if (db != Protocol.DEFAULT_DATABASE) {
                resource.select(db);
            }
            return resource;
        } catch (Exception e) {
            log.warn("Failed to get resource from the pool", e);
            String message = Objects.requireNonNullElse(e.getCause(), e).getMessage();
            ErrorDialog.show(message);
        }
        return null;
    }

    /**
     * 查询有多少个db
     *
     * @return
     */
    public int getDbCount() {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return 0;
            }

            int count = 0;
            List<String> databases = jedis.configGet("databases");
            if (databases.size() == 2) {
                count = Integer.parseInt(databases.get(1));
            }
            // reset
            jedis.select(Protocol.DEFAULT_DATABASE);
            return count;
        } catch (NullPointerException e) {
            log.warn("", e);
            return 0;
        }
    }

    /**
     * 查询db有多少个key
     *
     * @param db
     * @return
     */
    @Nullable
    public Long dbSize(int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            return jedis.dbSize();
        }
    }

    public Map<Integer, Keyspace> infoKeyspace() {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            String keyspace = jedis.info("keyspace");
            return keyspace.lines().filter(e -> !e.startsWith("#")).map(e -> {

                //db1:keys=598143,expires=0,avg_ttl=0

                String[] split = e.split(":");
                String db = split[0].substring(2);

                Keyspace build = Keyspace.builder().db(Integer.valueOf(db)).build();
                Arrays.stream(split[1].split(",")).forEach(e1->{
                    String[] split1 = e1.split("=");
                    switch (split1[0]) {
                        case "keys":
                            build.setKeys(Long.valueOf(split1[1]));
                            break;
                        case "expires":
                            build.setExpires(Long.valueOf(split1[1]));
                            break;
                        case "avg_ttl":
                            build.setAvgTtl(Long.valueOf(split1[1]));
                            break;
                    }
                });
                return build;
            }).collect(Collectors.toMap(Keyspace::getDb, Function.identity()));
        }
    }

    /**
     * @param expire 0 表示永不过期
     */
    public void set(String key, String val, long expire, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return;
            }
            if (expire != 0) {
                jedis.setex(key, expire, val);
            } else {
                jedis.set(key, val);
            }
        }
    }

    /**
     * 删除指定key
     *
     * @param key
     */
    @Nullable
    public Long del(String key, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            return jedis.del(key);
        } catch (Exception e) {
            throw new IllegalArgumentException("删除失败", e);
        }
    }

    /**
     * 模糊匹配满足条件的key
     *
     * @param count   每次扫描多少条记录，值越大消耗的时间越短，但会影响redis性能。建议设为一千到一万
     * @param pattern key的正则表达式
     * @return 匹配的key集合
     * @since Redis 2.8
     * @since 使用scan 替代keys keys如果数据量过大，会直接使redis崩溃
     */
    public List<String> scan(String cursor, String pattern, int count, int db) {
        List<String> list = new ArrayList<>();
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            ScanParams scanParams = new ScanParams();
            scanParams.count(count);
            scanParams.match(pattern);
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                list.addAll(scanResult.getResult());
                cursor = scanResult.getCursor();
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return list;
    }

    public Long lpush(String key, String[] values, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return -1L;
            }
            return jedis.lpush(key, values);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Long hset(String key, String field, String value, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return -1L;
            }
            return jedis.hset(key, field, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Long sadd(String key, int db, String... value) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return -1L;
            }
            return jedis.sadd(key, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Builder
    @Getter
    public static class TestConnectionResult {
        private boolean success;
        private String msg;
    }
}
