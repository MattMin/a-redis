package com.mzyupc.aredis.utils;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.Keyspace;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 */
@Slf4j
public class RedisPoolManager implements Disposable {

    private static final String LOCALHOST = "127.0.0.1";

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
        JEDIS_POOL_CONFIG.setSoftMinEvictableIdleDuration(Duration.ofSeconds(60));
        //检查链接是否有效
        JEDIS_POOL_CONFIG.setTestOnBorrow(true);
    }

    private final ConnectionInfo connectionInfo;
    private final Integer db;
    private JedisPool pool = null;
    private Session tunnelSession = null;
    private Integer tunnelLocalPort = null;

    public RedisPoolManager(ConnectionInfo connectionInfo) {
        this.connectionInfo = connectionInfo;
        this.db = Protocol.DEFAULT_DATABASE;
    }

    public static TestConnectionResult getTestConnectionResult(String host, Integer port, String user, String password) {
        ConnectionInfo connectionInfo = ConnectionInfo.builder()
                .url(host)
                .port(String.valueOf(port))
                .user(user)
                .password(password)
                .build();
        return getTestConnectionResult(connectionInfo);
    }

    public static TestConnectionResult getTestConnectionResult(ConnectionInfo connectionInfo) {
        RedisPoolManager redisPoolManager = new RedisPoolManager(connectionInfo);
        try (Jedis jedis = redisPoolManager.getJedis(Protocol.DEFAULT_DATABASE)) {
            if (jedis == null) {
                return TestConnectionResult.builder()
                        .success(false)
                        .msg("Failed to get Redis connection")
                        .build();
            }
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
            String errorMsg = Objects.requireNonNullElse(e.getCause(), e).getMessage();
            return TestConnectionResult.builder()
                    .success(false)
                    .msg(errorMsg)
                    .build();
        } finally {
            redisPoolManager.invalidate();
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
        if (tunnelSession != null) {
            tunnelSession.disconnect();
            tunnelSession = null;
        }
        tunnelLocalPort = null;
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

    private synchronized ConnectionEndpoint getConnectionEndpoint() throws Exception {
        if (!Boolean.TRUE.equals(connectionInfo.getSshTunnel())) {
            return new ConnectionEndpoint(connectionInfo.getUrl(), Integer.parseInt(connectionInfo.getPort()));
        }
        if (tunnelSession != null && tunnelSession.isConnected() && tunnelLocalPort != null) {
            return new ConnectionEndpoint(LOCALHOST, tunnelLocalPort);
        }

        JSch jsch = new JSch();
        if (StringUtils.isNotBlank(connectionInfo.getTunnelPrivateKeyPath())) {
            if (StringUtils.isNotEmpty(connectionInfo.getTunnelPassphrase())) {
                jsch.addIdentity(connectionInfo.getTunnelPrivateKeyPath(), connectionInfo.getTunnelPassphrase());
            } else {
                jsch.addIdentity(connectionInfo.getTunnelPrivateKeyPath());
            }
        }

        Session session = jsch.getSession(
                connectionInfo.getTunnelUser(),
                connectionInfo.getTunnelHost(),
                Integer.parseInt(StringUtils.defaultIfBlank(connectionInfo.getTunnelPort(), "22"))
        );
        if (StringUtils.isNotEmpty(connectionInfo.getTunnelPassword())) {
            session.setPassword(connectionInfo.getTunnelPassword());
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(Protocol.DEFAULT_TIMEOUT);
        int localPort = session.setPortForwardingL(
                0,
                connectionInfo.getUrl(),
                Integer.parseInt(connectionInfo.getPort())
        );
        tunnelSession = session;
        tunnelLocalPort = localPort;
        return new ConnectionEndpoint(LOCALHOST, localPort);
    }

    private DefaultJedisClientConfig createClientConfig(int db) throws Exception {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .database(db)
                .timeoutMillis(Protocol.DEFAULT_TIMEOUT);

        if (StringUtils.isNotEmpty(connectionInfo.getUser())) {
            builder.user(connectionInfo.getUser());
        }
        if (StringUtils.isNotEmpty(connectionInfo.getPassword())) {
            builder.password(connectionInfo.getPassword());
        }

        if (Boolean.TRUE.equals(connectionInfo.getSslTls())) {
            builder.ssl(true);
            SslConfiguration sslConfiguration = createSslConfiguration();
            if (sslConfiguration.getSslSocketFactory() != null) {
                builder.sslSocketFactory(sslConfiguration.getSslSocketFactory());
            }
            if (sslConfiguration.getHostnameVerifier() != null) {
                builder.hostnameVerifier(sslConfiguration.getHostnameVerifier());
            }
        }
        return builder.build();
    }

    private SslConfiguration createSslConfiguration() throws Exception {
        HostnameVerifier hostnameVerifier = Boolean.FALSE.equals(connectionInfo.getSslVerifyHostname())
                ? (hostname, session) -> true
                : HttpsURLConnection.getDefaultHostnameVerifier();

        boolean customSslContext = Boolean.TRUE.equals(connectionInfo.getSslTrustAllCertificates())
                || StringUtils.isNotBlank(connectionInfo.getSslTruststorePath())
                || StringUtils.isNotBlank(connectionInfo.getSslKeystorePath());
        if (!customSslContext) {
            return new SslConfiguration(null, hostnameVerifier);
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(loadKeyManagers(), loadTrustManagers(), new SecureRandom());
        return new SslConfiguration(sslContext.getSocketFactory(), hostnameVerifier);
    }

    private KeyManager[] loadKeyManagers() throws Exception {
        if (StringUtils.isBlank(connectionInfo.getSslKeystorePath())) {
            return null;
        }

        KeyStore keyStore = loadStandardKeyStore(
                connectionInfo.getSslKeystorePath(),
                connectionInfo.getSslKeystorePassword()
        );
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, toPasswordChars(connectionInfo.getSslKeystorePassword()));
        return keyManagerFactory.getKeyManagers();
    }

    private TrustManager[] loadTrustManagers() throws Exception {
        if (Boolean.TRUE.equals(connectionInfo.getSslTrustAllCertificates())) {
            return new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
        }
        if (StringUtils.isBlank(connectionInfo.getSslTruststorePath())) {
            return null;
        }

        KeyStore trustStore = loadTrustStore(
                connectionInfo.getSslTruststorePath(),
                connectionInfo.getSslTruststorePassword()
        );
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }

    private KeyStore loadTrustStore(String path, String password) throws Exception {
        if (isCertificateFile(path)) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            try (InputStream inputStream = new FileInputStream(path)) {
                Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(inputStream);
                int index = 0;
                for (Certificate certificate : certificates) {
                    keyStore.setCertificateEntry("cert-" + index++, certificate);
                }
            }
            return keyStore;
        }
        return loadStandardKeyStore(path, password);
    }

    private KeyStore loadStandardKeyStore(String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(getKeyStoreType(path));
        try (InputStream inputStream = new FileInputStream(path)) {
            keyStore.load(inputStream, toPasswordChars(password));
        }
        return keyStore;
    }

    private String getKeyStoreType(String path) {
        String lowerCasePath = StringUtils.lowerCase(path);
        if (StringUtils.endsWithAny(lowerCasePath, ".p12", ".pfx")) {
            return "PKCS12";
        }
        return KeyStore.getDefaultType();
    }

    private boolean isCertificateFile(String path) {
        String lowerCasePath = StringUtils.lowerCase(path);
        return StringUtils.endsWithAny(lowerCasePath, ".crt", ".cer", ".pem");
    }

    private char[] toPasswordChars(String password) {
        return StringUtils.isEmpty(password) ? null : password.toCharArray();
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
                sb.append(String.format("\\x%02x", b & 0xff));
            }
        }
        return sb.toString();
    }

    private void initPool() {
        try {
            ConnectionEndpoint endpoint = getConnectionEndpoint();
            pool = new JedisPool(
                    JEDIS_POOL_CONFIG,
                    new HostAndPort(endpoint.getHost(), endpoint.getPort()),
                    createClientConfig(Protocol.DEFAULT_DATABASE)
            );
        } catch (Exception e) {
            invalidate();
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
            JedisPool jedisPool = getJedisPool();
            if (jedisPool == null) {
                return null;
            }
            Jedis resource = jedisPool.getResource();
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

            int count = 1;
            int min = 0;
            int max = 0;
            while (true) {
                try {
                    jedis.select(count - 1);
                    min = count;
                } catch (JedisDataException jedisDataException) {
                    max = count;
                }
                if (max != 0) {
                    count = min + (max - min) / 2;
                } else {
                    count = min * 2;
                }
                if (max - 1 == min) {
                    // reset
                    jedis.select(Protocol.DEFAULT_DATABASE);
                    return min;
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
                        default:
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
     * 模糊匹配满足条件的key, 用于查询 Key Tree 中的数据
     *
     * @param limit   结果集最大数量
     * @param pattern key的正则表达式
     * @return 匹配的key集合
     * @since Redis 2.8
     * @since 使用scan 替代keys keys如果数据量过大，会直接使redis崩溃
     */
    public List<String> scanKeys(String cursor, String pattern, int limit, int db) {
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            ScanParams scanParams = new ScanParams();
            scanParams.count(limit);
            scanParams.match(pattern);
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            return scanResult.getResult();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
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

    @Getter
    private static class ConnectionEndpoint {
        private final String host;
        private final int port;

        private ConnectionEndpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    @Getter
    private static class SslConfiguration {
        private final SSLSocketFactory sslSocketFactory;
        private final HostnameVerifier hostnameVerifier;

        private SslConfiguration(SSLSocketFactory sslSocketFactory, HostnameVerifier hostnameVerifier) {
            this.sslSocketFactory = sslSocketFactory;
            this.hostnameVerifier = hostnameVerifier;
        }
    }
}
