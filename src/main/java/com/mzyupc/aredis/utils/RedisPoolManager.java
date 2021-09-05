package com.mzyupc.aredis.utils;

import com.intellij.openapi.Disposable;
import com.mzyupc.aredis.view.ARedisToolWindow;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.MethodUtils;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.util.Pool;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author mzyupc@163.com
 *
 * todo redis线程池回收
 */
@Slf4j
public class RedisPoolManager extends CloseTranscoder implements Disposable {

    private String host;
    private Integer port;
    private String password;
    private Integer db;
    private JedisPool pool = null;
    private static JedisPoolConfig jedisPoolConfig;

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
        //逐出连接的最小空闲时间 默认1800000毫秒(1分钟)
        jedisPoolConfig.setMinEvictableIdleTimeMillis(60000);
        //对象空闲多久后逐出, 当空闲时间>该值 且 空闲连接>最大空闲数 时直接逐出,不再根据MinEvictableIdleTimeMillis判断  (默认逐出策略)
        jedisPoolConfig.setSoftMinEvictableIdleTimeMillis(60000);
        //逐出扫描的时间间隔(毫秒) 如果为负数,则不运行逐出线程, 默认-1
        jedisPoolConfig.setTimeBetweenEvictionRunsMillis(-1);
        //检查链接是否有效
        jedisPoolConfig.setTestOnBorrow(true);
    }

    public RedisPoolManager(ConnectionInfo connectionInfo) {
        this.host = connectionInfo.getUrl();
        this.port = Integer.parseInt(connectionInfo.getPort());
        this.password = connectionInfo.getPassword();
        this.db = Protocol.DEFAULT_DATABASE;
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

    private JedisPool getJedisPool() {
        if (pool == null) {
            instancePool();
        }
        return pool;
    }

    public static TestConnectionResult getTestConnectionResult(String host, Integer port, String password) {
        try (Pool<Jedis> pool = new JedisPool(jedisPoolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password);
             Jedis jedis = pool.getResource()) {
            String pong = jedis.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return TestConnectionResult.builder()
                        .success(true)
                        // todo
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

    @Override
    public void dispose() {
        this.invalidate();
    }

    @Builder
    @Getter
    public static class TestConnectionResult {
        private boolean success;

        private String msg;
    }

    /**
     * 执行redis命令
     * @param db
     * @param command
     * @param args
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    public List<String> execRedisCommand(int db, String command, String... args) throws InvocationTargetException, IllegalAccessException {
        Protocol.Command cmd = Protocol.Command.valueOf(command.toUpperCase());
        try (Jedis jedis = getJedis(db)) {
            Client client = jedis.getClient();
            Method method = MethodUtils.getMatchingMethod(Client.class, "sendCommand", Protocol.Command.class, String[].class);
            method.setAccessible(true);
            method.invoke(client, cmd, args);
            try {
                List<String> respList = new ArrayList<>();
                Object response = client.getOne();
                if (response instanceof List) {
                    for (Object itemResp : ((List) response)) {
                        respList.add(new String((byte[]) itemResp));
                    }
                    return respList;
                } else {
                    return Collections.singletonList(new String((byte[]) response));
                }

            } catch (JedisException e) {
                return Collections.singletonList(e.getMessage());
            }
        }
    }

    private synchronized void instancePool() {
//            HashSet<String> sentinels = new HashSet<>(nodes);
//            pool = new JedisSentinelPool(masterName, sentinels, jedisPoolConfig, Protocol.DEFAULT_TIMEOUT, password);
        try {
            pool = new JedisPool(jedisPoolConfig, host, port, Protocol.DEFAULT_TIMEOUT, password);
        } catch (Exception e) {
            log.error("初始化redis pool失败", e);
            ErrorDialog.show("Failed to initialize the Redis pool." + "\n" + e.getMessage());
        }
    }


    public int getDb() {
        return this.db == null ? Protocol.DEFAULT_DATABASE : this.db;
    }

    /**
     * 序列化要缓存的值
     *
     * @param value
     * @return
     */
    public byte[] serialize(Object value) {
        if (value == null) {
            throw new NullPointerException("要序列化的对象不能为空");
        }
        byte[] result = null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream os = null;
        try {
            bos = new ByteArrayOutputStream();
            os = new ObjectOutputStream(bos);
            os.writeObject(value);

            result = bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("序列化失败", e);
        } finally {
            close(os);
            close(bos);
        }
        return result;
    }

    /**
     * 反序列化
     *
     * @param in
     * @return
     */
    public Object deserialize(byte[] in) {
        ByteArrayInputStream bis = null;
        ObjectInputStream is = null;
        try {
            if (in != null) {
                bis = new ByteArrayInputStream(in);
                is = new ObjectInputStream(bis);
                return is.readObject();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("反序列化失败", e);
        } finally {
            close(is);
            close(bis);
        }
        return null;
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
            ARedisToolWindow.isConnected = true;
            return resource;
        } catch (Exception e) {
            log.warn("Failed to get resource from the pool", e);
            ErrorDialog.show(e.getCause().getMessage());
        }
        return null;
    }

    /**
     * 如果key存在返回true，不存在返回false
     *
     * @param key
     * @return
     */
    public boolean exists(String key) {
        return exists(key, getDb());
    }

    public boolean exists(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.exists(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    public String get(String key) {
        return get(key, getDb());
    }


    public String get(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.get(key);
        } finally {
            close(jedis);
        }
    }

    /**
     * 查询有多少个db
     * @return
     */
    public int getDbCount() {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return Integer.parseInt(jedis.configGet("databases").get(1));
        } catch (NullPointerException e) {
            log.warn("", e);
            return 0;
        } finally {
            close(jedis);
        }
    }

    /**
     * 查询db有多少个key
     *
     * @param db
     * @return
     */
    public Long dbSize(int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.dbSize();
        } finally {
            close(jedis);
        }
    }


    /**
     * 获取对象
     *
     * @param key
     * @return
     * @since @Deprecated 使用 get
     */
    @Deprecated
    public Object getObj(String key) {
        return getObj(key, getDb());
    }

    /**
     * @since @Deprecated 使用get
     */
    @Deprecated
    public Object getObj(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return deserialize(jedis.get(key.getBytes(Charset.defaultCharset())));
        } catch (Exception e) {
            throw new IllegalArgumentException("获取失败", e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 设置object对象
     *
     * @param key
     * @param val
     * @since @Deprecated 使用set
     */
    @Deprecated
    public void setObj(String key, Object val) {
        setObj(key, val, getDb());
    }

    /**
     * @since @Deprecated 使用set
     */
    @Deprecated
    public void setObj(String key, Object val, int expire) {
        setObj(key, val, expire, getDb());
    }

    /**
     * @since @Deprecated 使用set
     */
    @Deprecated
    public void setObj(String key, Object val, int expire, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            if (expire != 0) {
                jedis.setex(key.getBytes(Charset.defaultCharset()), expire, serialize(val));
            } else {
                jedis.set(key.getBytes(Charset.defaultCharset()), serialize(val));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("获取失败", e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 设置值
     *
     * @param key
     * @param val
     */
    public void set(String key, String val) {
        set(key, val, 0);
    }

    /**
     * 设置临时缓存
     *
     * @param key
     * @param val
     * @param expire 过期时间（单位秒）
     */
    public void set(String key, String val, int expire) {
        set(key, val, expire, getDb());
    }

    /**
     * @param expire 0 表示永不过期
     */
    public void set(String key, String val, int expire, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            if (expire != 0) {
                jedis.setex(key, expire, val);
            } else {
                jedis.set(key, val);
            }
        } finally {
            close(jedis);
        }
    }


    /**
     * 改变缓存的时间
     *
     * @param seconds 单位秒
     */
    public void expire(String key, int seconds) {
        expire(key, seconds, getDb());
    }

    /**
     * 指定db 更改缓存时间
     *
     * @param key
     * @param seconds 单位秒
     * @param db
     */
    public void expire(String key, int seconds, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            jedis.expire(key, seconds);
        } finally {
            close(jedis);
        }

    }


    public void del(String key) {
        del(key, getDb());
    }

    public void del(String key, int db) {
        remove(key, db);
    }

    /**
     * 删除指定key
     *
     * @param key
     */
    public void remove(String key) {
        remove(key, getDb());
    }

    public void remove(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            jedis.del(key);
        } catch (Exception e) {
            throw new IllegalArgumentException("删除失败", e);
        } finally {
            close(jedis);
        }

    }


    /**
     * 加上指定值，如果 key 不存在，那么 key 的值会先被初始化为 0,在加 integer
     *
     * @param key
     * @param integer 增量值
     * @return
     */
    public Long incrBy(String key, long integer) {
        return incrBy(key, integer, getDb());
    }

    public Long incrBy(String key, long integer, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.incrBy(key, integer);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 操作map结构 加上指定值，如果 key 不存在，那么 key 的值会先被初始化为 0,在加 integer
     *
     * @param key
     * @param field
     * @param integer
     * @return
     */
    public Long hincrBy(String key, String field, long integer) {
        return hincrBy(key, field, integer, getDb());
    }

    public Long hincrBy(String key, String field, long integer, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hincrBy(key, field, integer);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 减去指定值，如果 key 不存在，那么 key 的值会先被初始化为 0,在减去 integer
     *
     * @param key
     * @param integer
     * @return
     */
    public Long decrBy(String key, long integer) {
        return decrBy(key, integer, getDb());
    }

    public Long decrBy(String key, long integer, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.decrBy(key, integer);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 将值value关联到key，并设置缓存时间为seconds
     *
     * @param key
     * @param seconds 单位秒
     * @param value
     * @return
     * @since 如果key已经存在将覆盖旧值
     */
    public void setex(String key, int seconds, String value) {
        setex(key, seconds, value, getDb());
    }

    public void setex(String key, int seconds, String value, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            jedis.setex(key, seconds, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 当且仅当 key 不存在时才会set，如果key已存在不会覆盖
     *
     * @param key
     * @param value
     * @return true设置成功，false设置失败
     */
    public boolean setnx(String key, String value) {
        return setnx(key, value, 0);
    }

    public boolean setnx(String key, String value, int seconds) {
        return setnx(key, value, seconds, getDb());
    }

    /**
     * @param seconds 0 不过期
     */
    public boolean setnx(String key, String value, int seconds, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            Long setnx = jedis.setnx(key, value);
            if (seconds != 0) {
                jedis.expire(key, seconds);
            }
            return setnx.intValue() == 1 ? true : false;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 模糊匹配满足条件的key
     *
     * @param pattern key的正则表达式
     * @param count   每次扫描多少条记录，值越大消耗的时间越短，但会影响redis性能。建议设为一千到一万
     * @return 匹配的key集合
     * @since Redis 2.8
     * @since 使用scan 替代keys keys如果数据量过大，会直接使redis崩溃
     */
    public List<String> scan(String pattern, int count) {
        return scan(ScanParams.SCAN_POINTER_START, pattern, count, getDb());
    }

    public List<String> scan(String cursor, String pattern, int count, int db) {
        List<String> list = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            ScanParams scanParams = new ScanParams();
            scanParams.count(count);
            scanParams.match(pattern);

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                list.addAll(scanResult.getResult());
                cursor = scanResult.getStringCursor();
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
        return list;
    }


    /**
     * 设置hash值
     * value=键值对形式
     *
     * @param key   key
     * @param field hash名
     * @param value 值
     * @return 0已存在 1.放入成功
     */
    public Long hset(String key, String field, String value) {
        return hset(key, field, value, getDb());
    }

    public Long hset(String key, String field, String value, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hset(key, field, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 一次性存储多个hash
     *
     * @param key
     * @param hash
     */
    public void hmset(String key, Map<String, String> hash) {
        hmset(key, hash, getDb());
    }

    public void hmset(String key, Map<String, String> hash, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            jedis.hmset(key, hash);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 一次性获取多个hashkey对应的value
     *
     * @param key
     * @param fields
     * @return
     */
    public List<String> hmget(String key, String... fields) {
        return hmget(key, fields, getDb());
    }

    public List<String> hmget(String key, String[] fields, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hmget(key, fields);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 获取hash表中总条目数
     *
     * @param key
     * @return
     */
    public Long hlen(String key) {
        return hlen(key, getDb());
    }

    public Long hlen(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hlen(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 获取hash值
     *
     * @param key   key
     * @param field 字段名
     * @return value
     */
    public String hget(String key, String field) {
        return hget(key, field, getDb());
    }

    public String hget(String key, String field, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            String res = jedis.hget(key, field);
            return "nil".equals(res) ? null : res;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 获取hash元素中所有的field
     *
     * @param key
     * @return
     */
    public Set<String> hkeys(String key) {
        return hkeys(key, getDb());
    }

    public Set<String> hkeys(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hkeys(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 获取hash元素中所有的value
     *
     * @param key
     * @return
     */
    public List<String> hvals(String key) {
        return hvals(key, getDb());
    }

    public List<String> hvals(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hvals(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 返回整个hash表元素,key和value
     *
     * @param key
     * @return 当key不存在时，map.isEmpty() == true
     */
    public Map<String, String> hgetAll(String key) {
        return hgetAll(key, getDb());
    }

    public Map<String, String> hgetAll(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hgetAll(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 删除hash值
     * value=键值对形式
     *
     * @param key    key
     * @param fields hash名
     * @return 0删除失败，已删除或不存在 1.删除成功
     */
    public Long hdel(String key, String... fields) {
        return hdel(key, fields, getDb());
    }

    public Long hdel(String key, String[] fields, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.hdel(key, fields);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 插入list，从队列<span style="color:red;font-size:18px;">前</span>插入
     *
     * @param key
     * @param values list value
     * @return 返回当前key中所有list的长度
     */
    public Long lpush(String key, String... values) {
        return lpush(key, values, getDb());
    }

    public Long lpush(String key, List<String> values) {
        return lpush(key, values.toArray(new String[values.size()]), getDb());
    }

    public Long lpush(String key, String[] values, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lpush(key, values);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 插入list，从队列 <span style="color:red;font-size:18px;">后</span> 插入
     *
     * @param key
     * @param values list value
     * @return 返回当前key中所有list的长度
     */
    public Long rpush(String key, String... values) {
        return rpush(key, values, getDb());
    }

    public Long rpush(String key, String[] values, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.rpush(key, values);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 返回当前key的长度
     *
     * @param key
     * @return
     */
    public Long llen(String key) {
        return llen(key, getDb());
    }

    public Long llen(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.llen(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 获取list中所有元素
     *
     * @param key
     * @return
     */
    public List<String> lgetAll(String key) {
        return lgetAll(key, getDb());
    }

    public List<String> lgetAll(String key, int db) {
        return lrange(key, 0, -1, db);
    }

    /**
     * 返回list中一个区间的元素,如果start=0，end<0 则等于获取所有元素
     *
     * @param key
     * @param start 开始下标
     * @param end   结束下标
     * @return
     */
    public List<String> lrange(String key, int start, int end) {
        return lrange(key, start, end, getDb());
    }

    public List<String> lrange(String key, int start, int end, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lrange(key, start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 获取指定下标的值
     *
     * @param key
     * @param index
     * @return
     */
    public String lindex(String key, int index) {
        return lindex(key, index, getDb());
    }

    public String lindex(String key, int index, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lindex(key, index);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 修改指定下标的值
     *
     * @param key
     * @param index
     * @param value
     * @return
     */
    public String lset(String key, int index, String value) {
        return lset(key, index, value, getDb());
    }

    public String lset(String key, int index, String value, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lset(key, index, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 根据count和value删除指定的值
     *
     * @param key
     * @param count
     * @param value
     * @return
     * @since <pre>
     * 	    1.移除等于value的元素，当count>0时，从表头开始查找，移除count个；<br/>
     * 	    2.当count=0时，从表头开始查找，移除所有等于value的；<br/>
     * 	    3.当count<0时，从表尾开始查找，移除|count| 个。<br/>
     * 	</pre>
     */
    public Long lrem(String key, int count, String value) {
        return lrem(key, count, value, getDb());
    }

    public Long lrem(String key, int count, String value, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lrem(key, count, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 对list元素进行修剪，保留指定区间的值
     *
     * @param key
     * @param start
     * @param end
     * @return
     * @see 0 第一个元素，1第二个元素，-1 表示最后一个，-2 表示倒数第二个
     */
    public String ltrim(String key, long start, long end) {
        return ltrim(key, start, end, getDb());
    }

    public String ltrim(String key, long start, long end, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.ltrim(key, start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 移除头部第一个元素并返回
     *
     * @param key
     * @return
     */
    public String lpop(String key) {
        return lpop(key, getDb());
    }

    public String lpop(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.lpop(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 移除最后一个元素并返回
     *
     * @param key
     * @return
     */
    public String rpop(String key) {
        return rpop(key, getDb());
    }

    public String rpop(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.rpop(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 添加set集合
     *
     * @param key
     * @param value
     * @return 1 添加成功 0 已存在
     */
    public Long sadd(String key, String... value) {
        return sadd(key, getDb(), value);
    }

    public Long sadd(String key, int db, String... value) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.sadd(key, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 获取set的成员数
     *
     * @param key
     * @return
     */
    public Long scard(String key) {
        return scard(key, getDb());
    }

    public Long scard(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.scard(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 获取指定key中的 集合的差集
     *
     * @param key
     * @return
     */
    public Set<String> sdiff(String[] key) {
        return sdiff(key, getDb());
    }

    public Set<String> sdiff(String[] key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.sdiff(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 判断指定的值是否在该集合中
     *
     * @param key
     * @param value
     * @return
     */
    public Boolean sismember(String key, String value) {
        return sismember(key, value, getDb());
    }

    public Boolean sismember(String key, String value, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.sismember(key, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 获取set中所有元素
     *
     * @param key
     * @return
     */
    public Set<String> smembers(String key) {
        return smembers(key, getDb());
    }

    public Set<String> smembers(String key, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.smembers(key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }


    /**
     * 删除指定值
     *
     * @param key
     * @param values
     * @return 1 删除成功 0失败或该值不存在
     */
    public Long srem(String key, String... values) {
        return srem(key, getDb(), values);
    }

    public Long srem(String key, int db, String... values) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.srem(key, values);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }

    /**
     * 合并几个key中的集合
     *
     * @param keys
     * @return 因为是set，所以会去重
     */
    public Set<String> sunion(String[] keys) {
        return sunion(keys, getDb());
    }

    public Set<String> sunion(String[] keys, int db) {
        Jedis jedis = null;
        try {
            jedis = getJedis(db);
            return jedis.sunion(keys);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        } finally {
            close(jedis);
        }
    }
}
