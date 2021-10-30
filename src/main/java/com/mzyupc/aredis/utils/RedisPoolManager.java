package com.mzyupc.aredis.utils;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.MethodUtils;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.Pool;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 */
@Slf4j
public class RedisPoolManager implements Disposable {

    private static final JedisPoolConfig jedisPoolConfig;

    static {
        jedisPoolConfig = new JedisPoolConfig();
        //连接耗尽时是否阻塞, false报异常,ture阻塞直到超时, 默认true
        jedisPoolConfig.setBlockWhenExhausted(false);
        //最大空闲连接数, 默认8个
        jedisPoolConfig.setMaxIdle(10);
        //最小空闲连接数, 默认0
        jedisPoolConfig.setMinIdle(0);
        //最大连接数, 默认8个
        jedisPoolConfig.setMaxTotal(100);
        //对象空闲多久后逐出, 当空闲时间>该值 且 空闲连接>最大空闲数 时直接逐出,不再根据MinEvictableIdleTimeMillis判断  (默认逐出策略)
        jedisPoolConfig.setSoftMinEvictableIdleTime(Duration.ofSeconds(60));
        //检查链接是否有效
        jedisPoolConfig.setTestOnBorrow(true);
    }

    private final String host;
    private final Integer port;
    private final String password;
    private final Integer db;
    private JedisPool pool = null;

    public RedisPoolManager(ConnectionInfo connectionInfo) {
        this.host = connectionInfo.getUrl();
        this.port = Integer.parseInt(connectionInfo.getPort());
        this.password = connectionInfo.getPassword();
        this.db = Protocol.DEFAULT_DATABASE;
    }

    public static TestConnectionResult getTestConnectionResult(String host, Integer port, String password) {
        try (Pool<Jedis> pool = new JedisPool(jedisPoolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password);
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

            Client client = jedis.getClient();
            Method method = MethodUtils.getMatchingMethod(Client.class, "sendCommand", Protocol.Command.class, String[].class);
            method.setAccessible(true);
//            processArgs(cmd, args);
            method.invoke(client, cmd, args);
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
        } catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
            return Collections.singletonList(e.getMessage());
        }
    }

    /**
     * 如果是restore命令, 需要处理传入参数
     * @param cmd
     * @param args
     */
    private void processArgs(Protocol.Command cmd, String[] args) {
        if (cmd == Protocol.Command.RESTORE) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("\\x")) {
                    byte[] processedArg = getRestoreBytes(arg);
                    args[i] = new String(processedArg);
                }
            }
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

    /**
     * todo 将restore命令字符串参数解析为byte[]
     * @param dump
     * @return
     */
    private byte[] getRestoreBytes(String dump) {
        List<Byte> result = com.google.common.collect.Lists.newArrayList();

        while (dump.length() >= 1) {
            if (dump.startsWith("\\x")) {
                result.add((byte)(0xff & Byte.parseByte(dump.substring(2, 4), 16)));
                dump = dump.substring(4);
            } else {
                result.add((byte)dump.charAt(0));
                if (dump.length() == 1) {
                    break;
                }
                dump = dump.substring(1);
            }
        }
        byte[] bytes = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            bytes[i] = result.get(i);
        }
        return bytes;
    }

    private void initPool() {
        try {
            pool = new JedisPool(jedisPoolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password);
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
            while(true) {
                try {
                    jedis.select(count++);
                } catch (JedisDataException jedisDataException) {
                    // reset
                    jedis.select(Protocol.DEFAULT_DATABASE);
                    return count - 1;
                }
            }
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
    public Long dbSize(int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return 0L;
            }
            return jedis.dbSize();
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
    public void del(String key, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return;
            }
            jedis.del(key);
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
