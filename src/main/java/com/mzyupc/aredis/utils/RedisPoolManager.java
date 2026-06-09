package com.mzyupc.aredis.utils;

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
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
import redis.clients.jedis.resps.Tuple;
import redis.clients.jedis.util.JedisClusterCRC16;

import javax.net.ssl.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 */
@Slf4j
public class RedisPoolManager implements Disposable {

    private static final String LOCALHOST = "127.0.0.1";
    private static final String[] CLIENT_KEYSTORE_EXTENSIONS = new String[]{"jks", "p12", "pfx"};
    private static final String CLUSTER_KEYSPACE_SECTION = "# Keyspace\r\ndb0:keys=%s,expires=0,avg_ttl=0";
    private static final String CLUSTER_SELECT_UNSUPPORTED_MESSAGE = "ERR SELECT is not allowed in cluster mode";
    private static final String CLUSTER_CROSS_SLOT_MESSAGE =
            "ERR CROSSSLOT Keys in request don't hash to the same slot";
    private static final String CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE =
            "ERR Command '%s' is not supported in cluster console mode yet. Please connect to a single Redis node to run this command.";
    private static final int CLUSTER_ENDPOINT_CONNECT_TIMEOUT_MILLIS = 200;
    private static final String DEFAULT_KNOWN_HOSTS_PATH = System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";

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
    private final boolean suppressErrorDialog;
    private JedisPool pool = null;
    private JedisCluster cluster = null;
    private volatile Map<String, HostAndPort> clusterHostAndPortMapping = null;
    private Session tunnelSession = null;
    private Integer tunnelLocalPort = null;
    private volatile Throwable lastConnectionError = null;

    public RedisPoolManager(ConnectionInfo connectionInfo) {
        this(connectionInfo, false);
    }

    private RedisPoolManager(ConnectionInfo connectionInfo, boolean suppressErrorDialog) {
        this.connectionInfo = connectionInfo;
        this.db = Protocol.DEFAULT_DATABASE;
        this.suppressErrorDialog = suppressErrorDialog;
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
        RedisPoolManager redisPoolManager = new RedisPoolManager(connectionInfo, true);
        try {
            String pong = redisPoolManager.pingForTestConnection();
            if (pong == null) {
                return TestConnectionResult.builder()
                        .success(false)
                        .msg(redisPoolManager.getLastConnectionErrorMessage("Failed to get Redis connection"))
                        .build();
            }
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
            String errorMsg = redisPoolManager.buildDetailedErrorMessage(e);
            return TestConnectionResult.builder()
                    .success(false)
                    .msg(errorMsg)
                    .build();
        } finally {
            redisPoolManager.invalidate();
        }
    }

    private String pingForTestConnection() throws Exception {
        if (isClusterMode()) {
            return executeOnConfiguredClusterNode(node -> {
                node.clusterInfo();
                return node.ping();
            });
        }
        try (Jedis jedis = getJedis(Protocol.DEFAULT_DATABASE)) {
            if (jedis == null) {
                return null;
            }
            return jedis.ping();
        }
    }

    /**
     * 连接池是否实例化
     *
     * @return
     */
    public boolean isValidate() {
        return pool != null || cluster != null;
    }

    /**
     * 关闭连接池
     */
    public void invalidate() {
        if (isValidate()) {
            if (this.pool != null) {
                this.pool.close();
                this.pool = null;
            }
            if (this.cluster != null) {
                this.cluster.close();
                this.cluster = null;
            }
        }
        if (tunnelSession != null) {
            tunnelSession.disconnect();
            tunnelSession = null;
        }
        tunnelLocalPort = null;
        lastConnectionError = null;
    }

    private synchronized JedisPool getJedisPool() {
        if (pool == null) {
            initPool();
        }
        return pool;
    }

    private synchronized JedisCluster getJedisCluster() {
        if (cluster == null) {
            initPool();
        }
        return cluster;
    }

    @Override
    public void dispose() {
        this.invalidate();
    }

    private synchronized ConnectionEndpoint getConnectionEndpoint() throws Exception {
        if (Boolean.TRUE.equals(connectionInfo.getClusterMode()) && Boolean.TRUE.equals(connectionInfo.getSshTunnel())) {
            throw new IllegalArgumentException("Cluster mode does not support SSH Tunnel currently");
        }
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
        boolean verifyHostKey = Boolean.TRUE.equals(connectionInfo.getTunnelVerifyHostKey());
        if (verifyHostKey) {
            configureKnownHosts(jsch);
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
        config.put("StrictHostKeyChecking", verifyHostKey ? "yes" : "no");
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

    private void configureKnownHosts(JSch jsch) throws Exception {
        File knownHostsFile = new File(DEFAULT_KNOWN_HOSTS_PATH);
        if (knownHostsFile.isFile()) {
            jsch.setKnownHosts(knownHostsFile.getAbsolutePath());
        }
    }

    private DefaultJedisClientConfig createClientConfig(@Nullable Integer db, boolean clusterMode) throws Exception {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder()
                .timeoutMillis(Protocol.DEFAULT_TIMEOUT);

        if (!clusterMode && db != null) {
            builder.database(db);
        }
        if (clusterMode) {
            builder.hostAndPortMapper(createClusterHostAndPortMapper());
        }

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
            if (sslConfiguration.getSslParameters() != null) {
                builder.sslParameters(sslConfiguration.getSslParameters());
            }
        }
        return builder.build();
    }

    private HostAndPortMapper createClusterHostAndPortMapper() {
        final String configuredHost = StringUtils.defaultIfBlank(connectionInfo.getUrl(), LOCALHOST);
        final Map<String, HostAndPort> discoveredMappings = loadClusterHostAndPortMapping(configuredHost);
        return hostAndPort -> {
            if (hostAndPort == null) {
                return null;
            }
            HostAndPort mapped = discoveredMappings.get(getClusterEndpointKey(hostAndPort));
            if (mapped != null) {
                return mapped;
            }
            HostAndPort remappedEndpoint = remapClusterNodeEndpoint(hostAndPort, configuredHost, this::isEndpointReachable);
            if (!sameEndpoint(hostAndPort, remappedEndpoint)) {
                discoveredMappings.put(getClusterEndpointKey(hostAndPort), remappedEndpoint);
            }
            return remappedEndpoint;
        };
    }

    private Map<String, HostAndPort> loadClusterHostAndPortMapping(String configuredHost) {
        if (clusterHostAndPortMapping != null) {
            return clusterHostAndPortMapping;
        }
        synchronized (this) {
            if (clusterHostAndPortMapping != null) {
                return clusterHostAndPortMapping;
            }
            Map<String, HostAndPort> mappings = new ConcurrentHashMap<>();
            try (Jedis seedNode = new Jedis(
                    new HostAndPort(connectionInfo.getUrl(), Integer.parseInt(connectionInfo.getPort())),
                    createClientConfig(null, false))) {
                String clusterNodes = seedNode.clusterNodes();
                if (StringUtils.isNotBlank(clusterNodes)) {
                    List<ClusterNodeDescriptor> clusterNodeDescriptors = extractClusterNodeDescriptors(clusterNodes);
                    for (ClusterNodeDescriptor clusterNodeDescriptor : clusterNodeDescriptors) {
                        HostAndPort announcedEndpoint = clusterNodeDescriptor.getAnnouncedEndpoint();
                        if (announcedEndpoint == null) {
                            continue;
                        }
                        HostAndPort mappedEndpoint = remapClusterNodeEndpoint(announcedEndpoint, configuredHost, this::isEndpointReachable);
                        if (!sameEndpoint(announcedEndpoint, mappedEndpoint)) {
                            putClusterEndpointMapping(mappings, announcedEndpoint.getHost(), announcedEndpoint.getPort(), mappedEndpoint);
                        }

                        if (StringUtils.isNotBlank(clusterNodeDescriptor.getAnnouncedHostName())) {
                            putClusterEndpointMapping(mappings,
                                    clusterNodeDescriptor.getAnnouncedHostName(),
                                    announcedEndpoint.getPort(),
                                    mappedEndpoint);
                        }
                    }

                    Map<String, HostAndPort> publishedPortMappings = discoverPublishedClusterNodeMappings(configuredHost, clusterNodeDescriptors);
                    for (ClusterNodeDescriptor clusterNodeDescriptor : clusterNodeDescriptors) {
                        HostAndPort publishedEndpoint = publishedPortMappings.get(clusterNodeDescriptor.getNodeId());
                        HostAndPort announcedEndpoint = clusterNodeDescriptor.getAnnouncedEndpoint();
                        if (publishedEndpoint == null || announcedEndpoint == null) {
                            continue;
                        }
                        putClusterEndpointMapping(mappings, announcedEndpoint.getHost(), announcedEndpoint.getPort(), publishedEndpoint);
                        if (StringUtils.isNotBlank(clusterNodeDescriptor.getAnnouncedHostName())) {
                            putClusterEndpointMapping(mappings,
                                    clusterNodeDescriptor.getAnnouncedHostName(),
                                    announcedEndpoint.getPort(),
                                    publishedEndpoint);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to pre-load cluster nodes mapping", e);
            }
            clusterHostAndPortMapping = mappings;
            return clusterHostAndPortMapping;
        }
    }

    private Map<String, HostAndPort> discoverPublishedClusterNodeMappings(String configuredHost,
                                                                          List<ClusterNodeDescriptor> clusterNodeDescriptors) {
        if (!shouldDiscoverPublishedClusterPorts(configuredHost, clusterNodeDescriptors)) {
            return Collections.emptyMap();
        }

        int configuredPort = Integer.parseInt(connectionInfo.getPort());
        int portOffset = Math.max(32, clusterNodeDescriptors.size() * 4);
        int startPort = Math.max(1, configuredPort - portOffset);
        int endPort = configuredPort + portOffset;

        Set<String> remainingNodeIds = clusterNodeDescriptors.stream()
                .map(ClusterNodeDescriptor::getNodeId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, HostAndPort> discoveredMappings = new HashMap<>();
        for (int port = startPort; port <= endPort && !remainingNodeIds.isEmpty(); port++) {
            String nodeId = fetchClusterNodeId(configuredHost, port);
            if (StringUtils.isBlank(nodeId) || !remainingNodeIds.remove(nodeId)) {
                continue;
            }
            discoveredMappings.put(nodeId, new HostAndPort(configuredHost, port));
        }
        return discoveredMappings;
    }

    private boolean shouldDiscoverPublishedClusterPorts(String configuredHost,
                                                        List<ClusterNodeDescriptor> clusterNodeDescriptors) {
        if (!isLocalHost(configuredHost)
                || StringUtils.isBlank(connectionInfo.getPort())
                || !StringUtils.isNumeric(connectionInfo.getPort())
                || clusterNodeDescriptors == null
                || clusterNodeDescriptors.size() <= 1) {
            return false;
        }

        int configuredPort = Integer.parseInt(connectionInfo.getPort());
        return clusterNodeDescriptors.stream()
                .map(ClusterNodeDescriptor::getAnnouncedEndpoint)
                .filter(Objects::nonNull)
                .map(HostAndPort::getPort)
                .anyMatch(port -> port > 0 && port != configuredPort);
    }

    private boolean isLocalHost(String host) {
        String s1 = StringUtils.trimToEmpty(host);
        return StringUtil.equalsIgnoreCase(s1, "127.0.0.1")
                || StringUtil.equalsIgnoreCase(s1, "localhost")
                || StringUtil.equalsIgnoreCase(s1, "::1");
    }

    private String fetchClusterNodeId(String host, int port) {
        try (Jedis jedis = new Jedis(new HostAndPort(host, port), createClientConfig(null, false))) {
            return StringUtils.trimToNull(jedis.clusterMyId());
        } catch (Exception e) {
            return null;
        }
    }

    private void putClusterEndpointMapping(Map<String, HostAndPort> mappings, String host, int port, HostAndPort mappedEndpoint) {
        if (StringUtils.isBlank(host) || mappedEndpoint == null) {
            return;
        }
        mappings.put(getClusterEndpointKey(host, port), mappedEndpoint);
    }

    private HostAndPort resolveClusterNodeEndpoint(HostAndPort announcedEndpoint) {
        if (announcedEndpoint == null) {
            return null;
        }
        String configuredHost = StringUtils.defaultIfBlank(connectionInfo.getUrl(), LOCALHOST);
        HostAndPort mappedEndpoint = loadClusterHostAndPortMapping(configuredHost).get(getClusterEndpointKey(announcedEndpoint));
        if (mappedEndpoint != null) {
            return mappedEndpoint;
        }
        return remapClusterNodeEndpoint(announcedEndpoint, configuredHost, this::isEndpointReachable);
    }

    private HostAndPort remapClusterNodeEndpoint(HostAndPort announcedEndpoint, String configuredHost) {
        return remapClusterNodeEndpoint(announcedEndpoint, configuredHost, this::isEndpointReachable);
    }

    static HostAndPort remapClusterNodeEndpoint(HostAndPort announcedEndpoint,
                                                String configuredHost,
                                                BiFunction<String, Integer, Boolean> reachabilityChecker) {
        if (announcedEndpoint == null) {
            return null;
        }

        String announcedHost = StringUtils.trimToNull(announcedEndpoint.getHost());
        int announcedPort = announcedEndpoint.getPort();
        if (announcedHost == null || announcedPort <= 0) {
            return new HostAndPort(StringUtils.defaultIfBlank(configuredHost, LOCALHOST), announcedPort);
        }

        if (Boolean.TRUE.equals(reachabilityChecker.apply(announcedHost, announcedPort))) {
            return announcedEndpoint;
        }

        if (StringUtils.isBlank(configuredHost)) {
            return announcedEndpoint;
        }

        if (Boolean.TRUE.equals(reachabilityChecker.apply(configuredHost, announcedPort))) {
            return new HostAndPort(configuredHost, announcedPort);
        }

        return announcedEndpoint;
    }

    static String getClusterEndpointKey(HostAndPort endpoint) {
        if (endpoint == null) {
            return null;
        }
        return getClusterEndpointKey(endpoint.getHost(), endpoint.getPort());
    }

    static String getClusterEndpointKey(String host, int port) {
        return StringUtils.defaultString(StringUtils.trimToEmpty(host)).toLowerCase(Locale.ROOT) + ":" + port;
    }

    private boolean sameEndpoint(HostAndPort left, HostAndPort right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return StringUtil.equalsIgnoreCase(left.getHost(), right.getHost()) && left.getPort() == right.getPort();
    }

    private static HostAndPort parseClusterNodeEndpoint(String clusterNodeEndpoint) {
        if (StringUtils.isBlank(clusterNodeEndpoint)) {
            return null;
        }

        String endpoint = StringUtils.substringBefore(clusterNodeEndpoint, "@");
        if (StringUtils.isBlank(endpoint)) {
            return null;
        }

        String host;
        String portText;
        if (StringUtil.startsWith(endpoint, "[")) {
            int closingBracketIndex = endpoint.indexOf(']');
            if (closingBracketIndex <= 0 || closingBracketIndex + 2 >= endpoint.length()) {
                return null;
            }
            host = endpoint.substring(1, closingBracketIndex);
            portText = endpoint.substring(closingBracketIndex + 2);
        } else {
            host = StringUtils.substringBeforeLast(endpoint, ":");
            portText = StringUtils.substringAfterLast(endpoint, ":");
        }
        if (StringUtils.isBlank(host) || !StringUtils.isNumeric(portText)) {
            return null;
        }
        return new HostAndPort(host, Integer.parseInt(portText));
    }

    static List<HostAndPort> extractClusterMasterEndpoints(String clusterNodes) {
        Map<String, HostAndPort> masters = new LinkedHashMap<>();
        for (ClusterNodeDescriptor clusterNodeDescriptor : extractClusterNodeDescriptors(clusterNodes)) {
            if (!clusterNodeDescriptor.isMaster() || clusterNodeDescriptor.getAnnouncedEndpoint() == null) {
                continue;
            }
            HostAndPort endpoint = clusterNodeDescriptor.getAnnouncedEndpoint();
            masters.putIfAbsent(getClusterEndpointKey(endpoint), endpoint);
        }
        return new ArrayList<>(masters.values());
    }

    static List<ClusterNodeDescriptor> extractClusterNodeDescriptors(String clusterNodes) {
        if (StringUtils.isBlank(clusterNodes)) {
            return Collections.emptyList();
        }

        List<ClusterNodeDescriptor> descriptors = new ArrayList<>();
        for (String line : clusterNodes.split("\\r?\\n")) {
            String trimmedLine = StringUtils.trimToEmpty(line);
            if (StringUtils.isBlank(trimmedLine)) {
                continue;
            }

            String[] segments = trimmedLine.split("\\s+");
            if (segments.length < 3) {
                continue;
            }

            String[] hostSegments = segments[1].split(",");
            descriptors.add(new ClusterNodeDescriptor(
                    StringUtils.trimToNull(segments[0]),
                    parseClusterNodeEndpoint(hostSegments[0]),
                    findClusterNodeHostnameAlias(hostSegments),
                    StringUtils.trimToEmpty(segments[2])
            ));
        }
        return descriptors;
    }

    private static boolean isClusterNodeMaster(String flags) {
        if (StringUtils.isBlank(flags)) {
            return false;
        }
        Set<String> flagSet = Arrays.stream(flags.split(","))
                .map(StringUtils::trimToEmpty)
                .map(StringUtils::lowerCase)
                .collect(Collectors.toSet());
        return flagSet.contains("master")
                && !flagSet.contains("fail")
                && !flagSet.contains("handshake")
                && !flagSet.contains("noaddr");
    }

    private static String findClusterNodeHostnameAlias(String[] hostSegments) {
        if (hostSegments == null || hostSegments.length < 2) {
            return null;
        }
        for (int i = 1; i < hostSegments.length; i++) {
            String candidate = StringUtils.trimToNull(hostSegments[i]);
            if (candidate != null && !StringUtils.contains(candidate, "=")) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isEndpointReachable(String host, int port) {
        if (StringUtils.isBlank(host) || port <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CLUSTER_ENDPOINT_CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SslConfiguration createSslConfiguration() throws Exception {
        SSLParameters sslParameters = null;
        if (!Boolean.FALSE.equals(connectionInfo.getSslVerifyHostname())) {
            sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        }

        boolean customSslContext = Boolean.TRUE.equals(connectionInfo.getSslTrustAllCertificates())
                || StringUtils.isNotBlank(connectionInfo.getSslTruststorePath())
                || StringUtils.isNotBlank(connectionInfo.getSslKeystorePath());
        if (!customSslContext) {
            return new SslConfiguration(null, sslParameters);
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(loadKeyManagers(), loadTrustManagers(), new SecureRandom());
        return new SslConfiguration(sslContext.getSocketFactory(), sslParameters);
    }

    private KeyManager[] loadKeyManagers() throws Exception {
        if (StringUtils.isBlank(connectionInfo.getSslKeystorePath())) {
            return null;
        }
        if (!isSupportedClientKeystorePath(connectionInfo.getSslKeystorePath())) {
            throw new Exception(String.format(
                    "Client certificate keystore '%s' must be a JKS or PKCS12 file (.jks, .p12, .pfx). PEM/CRT/CER files are not supported here.",
                    connectionInfo.getSslKeystorePath()
            ));
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
        } catch (Exception e) {
            throw buildKeyStoreLoadException(path, e);
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

    public static boolean isSupportedClientKeystorePath(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        String lowerCasePath = StringUtils.lowerCase(path);
        for (String extension : CLIENT_KEYSTORE_EXTENSIONS) {
            if (StringUtils.endsWith(lowerCasePath, "." + extension)) {
                return true;
            }
        }
        return false;
    }

    private Jedis openClusterNode(HostAndPort endpoint) throws Exception {
        if (endpoint == null) {
            return null;
        }
        return new Jedis(endpoint, createClientConfig(null, false));
    }

    private char[] toPasswordChars(String password) {
        return StringUtils.isEmpty(password) ? null : password.toCharArray();
    }

    private Exception buildKeyStoreLoadException(String path, Exception e) {
        String message = buildDetailedErrorMessage(e);
        if (StringUtils.containsIgnoreCase(message, "keystore password was incorrect")
                || StringUtils.containsIgnoreCase(message, "password was incorrect")
                || StringUtils.containsIgnoreCase(message, "keystore tampered with")) {
            return new Exception(String.format(
                    "Failed to load %s '%s': keystore password was incorrect. Please check %s.",
                    isTrustStorePath(path) ? "CA truststore" : "client certificate keystore",
                    path,
                    getCertificatePasswordFieldName(path)
            ), e);
        }

        if (StringUtils.endsWithAny(StringUtils.lowerCase(path), ".p12", ".pfx", ".jks")) {
            return new Exception(String.format(
                    "Failed to load %s '%s'. Please check the file format and %s. Details: %s",
                    isTrustStorePath(path) ? "CA truststore" : "client certificate keystore",
                    path,
                    getCertificatePasswordFieldName(path),
                    StringUtils.defaultIfBlank(message, e.getClass().getSimpleName())
            ), e);
        }
        return e;
    }

    private boolean isTrustStorePath(String path) {
        return StringUtil.equals(path, connectionInfo.getSslTruststorePath());
    }

    private String getCertificatePasswordFieldName(String path) {
        return isTrustStorePath(path) ? "CA Password" : "Key Password";
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

            if (isClusterMode()) {
                return execClusterCommand(cmd, args);
            }

            Connection client = jedis.getClient();
            client.sendCommand(cmd, args);
            try {
                return convertRedisResponse(cmd, client.getOne());
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
            clearLastConnectionError();
            ConnectionEndpoint endpoint = getConnectionEndpoint();
            if (isClusterMode()) {
                cluster = new JedisCluster(
                        Collections.singleton(new HostAndPort(endpoint.getHost(), endpoint.getPort())),
                        createClientConfig(null, true)
                );
            } else {
                pool = new JedisPool(
                        JEDIS_POOL_CONFIG,
                        new HostAndPort(endpoint.getHost(), endpoint.getPort()),
                        createClientConfig(Protocol.DEFAULT_DATABASE, false)
                );
            }
        } catch (Exception e) {
            invalidate();
            rememberConnectionError(e);
            log.error("初始化redis pool失败", e);
            if (!suppressErrorDialog) {
                ErrorDialog.show(buildDetailedErrorMessage(e));
            }
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
            if (isClusterMode()) {
                JedisCluster jedisCluster = getJedisCluster();
                if (jedisCluster == null) {
                    return null;
                }
                clearLastConnectionError();
                return new ClusterJedisAdapter(jedisCluster);
            }
            JedisPool jedisPool = getJedisPool();
            if (jedisPool == null) {
                return null;
            }
            Jedis resource = jedisPool.getResource();
            if (db != Protocol.DEFAULT_DATABASE) {
                resource.select(db);
            }
            clearLastConnectionError();
            return resource;
        } catch (Exception e) {
            rememberConnectionError(e);
            log.warn("Failed to get resource from the pool", e);
            String message = buildDetailedErrorMessage(e);
            if (!suppressErrorDialog) {
                ErrorDialog.show(message);
            }
        }
        return null;
    }

    private void rememberConnectionError(Throwable throwable) {
        lastConnectionError = throwable;
    }

    private void clearLastConnectionError() {
        lastConnectionError = null;
    }

    private String getLastConnectionErrorMessage(String defaultMessage) {
        return lastConnectionError == null ? defaultMessage : buildDetailedErrorMessage(lastConnectionError);
    }

    private String buildDetailedErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        List<String> messages = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null && !messages.contains(message)) {
                messages.add(message);
            }
            current = current.getCause();
        }

        if (messages.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        if (messages.size() == 1) {
            return messages.get(0);
        }
        return messages.get(0) + " Root cause: " + messages.get(messages.size() - 1);
    }

    /**
     * 查询有多少个db
     *
     * @return
     */
    public int getDbCount() {
        if (isClusterMode()) {
            try (Jedis jedis = getJedis(Protocol.DEFAULT_DATABASE)) {
                return jedis == null ? 0 : 1;
            } catch (Exception e) {
                rememberConnectionError(e);
                log.warn("Failed to load cluster db count", e);
                return 0;
            }
        }
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
        if (isClusterMode()) {
            if (db != Protocol.DEFAULT_DATABASE) {
                return 0L;
            }
            try {
                long total = 0L;
                for (Long value : executeOnClusterMasters(Jedis::dbSize)) {
                    total += value == null ? 0L : value;
                }
                return total;
            } catch (Exception e) {
                rememberConnectionError(e);
                throw new IllegalArgumentException(e);
            }
        }
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            return jedis.dbSize();
        }
    }

    public Map<Integer, Keyspace> infoKeyspace() {
        if (isClusterMode()) {
            Long dbSize = dbSize(Protocol.DEFAULT_DATABASE);
            if (dbSize == null) {
                return null;
            }
            return Collections.singletonMap(Protocol.DEFAULT_DATABASE, Keyspace.builder()
                    .db(Protocol.DEFAULT_DATABASE)
                    .keys(dbSize)
                    .expires(0L)
                    .avgTtl(0L)
                    .build());
        }
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
        if (isClusterMode()) {
            if (db != Protocol.DEFAULT_DATABASE) {
                return Collections.emptyList();
            }
            try {
                LinkedHashSet<String> keys = new LinkedHashSet<>();
                ScanParams scanParams = new ScanParams();
                scanParams.count(limit);
                scanParams.match(pattern);
                for (Jedis node : getClusterMasterNodes()) {
                    try (Jedis currentNode = node) {
                        String currentCursor = StringUtils.defaultIfBlank(cursor, ScanParams.SCAN_POINTER_START);
                        do {
                            ScanResult<String> scanResult = currentNode.scan(currentCursor, scanParams);
                            keys.addAll(scanResult.getResult());
                            currentCursor = scanResult.getCursor();
                        } while (!StringUtil.equals(currentCursor, ScanParams.SCAN_POINTER_START) && keys.size() < limit);
                        if (keys.size() >= limit) {
                            break;
                        }
                    }
                }
                return new ArrayList<>(keys).subList(0, Math.min(keys.size(), limit));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        try (Jedis jedis = getJedis(db)) {
            if (jedis == null) {
                return null;
            }
            ScanParams scanParams = new ScanParams();
            scanParams.count(limit);
            scanParams.match(pattern);
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            String currentCursor = StringUtils.defaultIfBlank(cursor, ScanParams.SCAN_POINTER_START);
            do {
                ScanResult<String> scanResult = jedis.scan(currentCursor, scanParams);
                keys.addAll(scanResult.getResult());
                currentCursor = scanResult.getCursor();
            } while (!StringUtil.equals(currentCursor, ScanParams.SCAN_POINTER_START) && keys.size() < limit);
            return new ArrayList<>(keys).subList(0, Math.min(keys.size(), limit));
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

    public boolean isClusterMode() {
        return Boolean.TRUE.equals(connectionInfo.getClusterMode());
    }

    private List<String> execClusterCommand(Protocol.Command cmd, String... args) {
        try {
            if (cmd == Protocol.Command.SELECT) {
                return Collections.singletonList(CLUSTER_SELECT_UNSUPPORTED_MESSAGE);
            }
            if (cmd == Protocol.Command.FLUSHDB || cmd == Protocol.Command.FLUSHALL) {
                return Collections.singletonList(flushClusterDb());
            }
            if (cmd == Protocol.Command.DBSIZE) {
                return Collections.singletonList(String.valueOf(Optional.ofNullable(dbSize(Protocol.DEFAULT_DATABASE)).orElse(0L)));
            }
            if (cmd == Protocol.Command.INFO) {
                return Collections.singletonList(getClusterInfo(args.length == 0 ? null : args[0]));
            }
            if (cmd == Protocol.Command.PING) {
                return Collections.singletonList(StringUtils.defaultIfBlank(executeOnFirstClusterMaster(Jedis::ping), "PONG"));
            }

            ClusterCommandRoute route = resolveClusterCommandRoute(cmd, args);
            if (route.isUnsupported()) {
                return Collections.singletonList(route.getErrorMessage());
            }
            if (hasCrossSlotKeys(cmd, args)) {
                return Collections.singletonList(CLUSTER_CROSS_SLOT_MESSAGE);
            }

            if (route.isFirstMaster()) {
                return executeOnFirstClusterMaster(node -> {
                    Connection client = node.getClient();
                    client.sendCommand(cmd, args);
                    return convertRedisResponse(cmd, client.getOne());
                });
            }

            try (Connection connection = getJedisCluster().getConnectionFromSlot(JedisClusterCRC16.getSlot(args[route.getKeyArgIndex()]))) {
                connection.sendCommand(cmd, args);
                return convertRedisResponse(cmd, connection.getOne());
            }
        } catch (Exception e) {
            return Collections.singletonList(buildDetailedErrorMessage(e));
        }
    }

    static ClusterCommandRoute resolveClusterCommandRoute(Protocol.Command cmd, String... args) {
        if (cmd == null) {
            return ClusterCommandRoute.unsupported(String.format(CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE, "unknown"));
        }
        if (args == null || args.length == 0 || isCommandWithoutKey(cmd)) {
            return ClusterCommandRoute.firstMaster();
        }

        String commandName = cmd.name();
        if (isFirstArgClusterKeyCommand(commandName)) {
            return ClusterCommandRoute.slot(0);
        }
        if ("EVAL".equals(commandName) || "EVALSHA".equals(commandName)) {
            return resolveEvalClusterCommandRoute(commandName, args);
        }
        if ("OBJECT".equals(commandName) || "MEMORY".equals(commandName) || "XINFO".equals(commandName)
                || "XGROUP".equals(commandName)) {
            return args.length >= 2
                    ? ClusterCommandRoute.slot(1)
                    : ClusterCommandRoute.unsupported(String.format(CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE, commandName));
        }
        if ("XREAD".equals(commandName) || "XREADGROUP".equals(commandName)) {
            int streamsKeywordIndex = indexOfIgnoreCase(args, "STREAMS");
            if (streamsKeywordIndex >= 0 && streamsKeywordIndex + 1 < args.length) {
                return ClusterCommandRoute.slot(streamsKeywordIndex + 1);
            }
        }
        return ClusterCommandRoute.unsupported(String.format(CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE, commandName));
    }

    private static ClusterCommandRoute resolveEvalClusterCommandRoute(String commandName, String... args) {
        if (args.length < 2 || !StringUtils.isNumeric(args[1])) {
            return ClusterCommandRoute.unsupported(String.format(CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE, commandName));
        }
        int keyCount = Integer.parseInt(args[1]);
        if (keyCount <= 0) {
            return ClusterCommandRoute.firstMaster();
        }
        return args.length >= 3
                ? ClusterCommandRoute.slot(2)
                : ClusterCommandRoute.unsupported(String.format(CLUSTER_CONSOLE_UNSUPPORTED_COMMAND_MESSAGE, commandName));
    }

    static boolean hasCrossSlotKeys(Protocol.Command cmd, String... args) {
        List<String> keys = extractClusterCommandKeys(cmd, args);
        if (keys.size() <= 1) {
            return false;
        }
        Integer slot = null;
        for (String key : keys) {
            if (StringUtils.isEmpty(key)) {
                continue;
            }
            int currentSlot = JedisClusterCRC16.getSlot(key);
            if (slot == null) {
                slot = currentSlot;
                continue;
            }
            if (slot != currentSlot) {
                return true;
            }
        }
        return false;
    }

    static List<String> extractClusterCommandKeys(Protocol.Command cmd, String... args) {
        if (cmd == null || args == null || args.length == 0) {
            return Collections.emptyList();
        }
        switch (cmd) {
            case DEL:
            case EXISTS:
            case MGET:
            case TOUCH:
            case UNLINK:
                return Arrays.asList(args);
            case MSET:
            case MSETNX:
                return everyNthArg(args, 0, 2);
            case RENAME:
            case RENAMENX:
            case RPOPLPUSH:
            case BRPOPLPUSH:
            case LMOVE:
            case BLMOVE:
            case SMOVE:
                return firstArgs(args, 2);
            case EVAL:
            case EVALSHA:
                return extractEvalKeys(args);
            case XREAD:
            case XREADGROUP:
                return extractXReadKeys(args);
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> everyNthArg(String[] args, int startIndex, int step) {
        List<String> keys = new ArrayList<>();
        for (int i = startIndex; i < args.length; i += step) {
            keys.add(args[i]);
        }
        return keys;
    }

    private static List<String> firstArgs(String[] args, int count) {
        if (args.length < count) {
            return Collections.emptyList();
        }
        return Arrays.asList(Arrays.copyOfRange(args, 0, count));
    }

    private static List<String> extractEvalKeys(String[] args) {
        if (args.length < 2 || !StringUtils.isNumeric(args[1])) {
            return Collections.emptyList();
        }
        int keyCount = Math.min(Integer.parseInt(args[1]), Math.max(0, args.length - 2));
        return keyCount == 0 ? Collections.emptyList() : Arrays.asList(Arrays.copyOfRange(args, 2, 2 + keyCount));
    }

    private static List<String> extractXReadKeys(String[] args) {
        int streamsKeywordIndex = indexOfIgnoreCase(args, "STREAMS");
        if (streamsKeywordIndex < 0 || streamsKeywordIndex + 1 >= args.length) {
            return Collections.emptyList();
        }
        int remainingArgCount = args.length - streamsKeywordIndex - 1;
        int keyCount = remainingArgCount / 2;
        if (keyCount <= 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(Arrays.copyOfRange(args, streamsKeywordIndex + 1, streamsKeywordIndex + 1 + keyCount));
    }

    private static int indexOfIgnoreCase(String[] values, String target) {
        if (values == null || target == null) {
            return -1;
        }
        for (int i = 0; i < values.length; i++) {
            if (StringUtil.equalsIgnoreCase(values[i], target)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCommandWithoutKey(Protocol.Command cmd) {
        return cmd == Protocol.Command.CLUSTER
                || cmd == Protocol.Command.INFO
                || cmd == Protocol.Command.DBSIZE
                || cmd == Protocol.Command.FLUSHDB
                || cmd == Protocol.Command.FLUSHALL
                || cmd == Protocol.Command.PING
                || cmd == Protocol.Command.TIME
                || cmd == Protocol.Command.CLIENT
                || cmd == Protocol.Command.CONFIG
                || cmd == Protocol.Command.COMMAND;
    }

    private static boolean isFirstArgClusterKeyCommand(String commandName) {
        switch (commandName) {
            case "APPEND":
            case "BITCOUNT":
            case "BITPOS":
            case "DECR":
            case "DECRBY":
            case "DEL":
            case "DUMP":
            case "EXISTS":
            case "EXPIRE":
            case "EXPIREAT":
            case "GET":
            case "GETBIT":
            case "GETDEL":
            case "GETEX":
            case "GETRANGE":
            case "GETSET":
            case "HDEL":
            case "HEXISTS":
            case "HGET":
            case "HGETALL":
            case "HINCRBY":
            case "HKEYS":
            case "HLEN":
            case "HMGET":
            case "HMSET":
            case "HSCAN":
            case "HSET":
            case "HVALS":
            case "INCR":
            case "INCRBY":
            case "LINDEX":
            case "LINSERT":
            case "LLEN":
            case "LPOP":
            case "LPUSH":
            case "LRANGE":
            case "LREM":
            case "LSET":
            case "LTRIM":
            case "MGET":
            case "MSET":
            case "PERSIST":
            case "PEXPIRE":
            case "PEXPIREAT":
            case "PFADD":
            case "PFCOUNT":
            case "PTTL":
            case "RENAME":
            case "RENAMENX":
            case "RPOP":
            case "RPOPLPUSH":
            case "RPUSH":
            case "SADD":
            case "SCARD":
            case "SET":
            case "SETBIT":
            case "SETEX":
            case "SETRANGE":
            case "SISMEMBER":
            case "SMEMBERS":
            case "SPOP":
            case "SREM":
            case "SSCAN":
            case "STRLEN":
            case "TOUCH":
            case "TTL":
            case "TYPE":
            case "UNLINK":
            case "XLEN":
            case "XRANGE":
            case "XREVRANGE":
            case "ZADD":
            case "ZCARD":
            case "ZINCRBY":
            case "ZRANGE":
            case "ZRANK":
            case "ZREM":
            case "ZREVRANGE":
            case "ZREVRANK":
            case "ZSCORE":
            case "ZSCAN":
                return true;
            default:
                return false;
        }
    }

    private List<String> convertRedisResponse(Protocol.Command cmd, Object response) {
        if (response == null) {
            return Collections.singletonList("null");
        }
        if (response instanceof List) {
            List<String> respList = new ArrayList<>();
            for (Object itemResp : ((List<?>) response)) {
                if (itemResp == null) {
                    respList.add("null");
                } else if (itemResp instanceof List) {
                    List<?> itemList = (List<?>) itemResp;
                    List<String> strings = itemList.stream().map(this::convertNestedResponseItem).collect(Collectors.toList());
                    respList.add(String.join("\n", strings));
                } else if (itemResp instanceof byte[]) {
                    respList.add(new String((byte[]) itemResp));
                } else {
                    respList.add(JSON.toJSONString(itemResp));
                }
            }
            return respList;
        }
        if (response instanceof Long) {
            return Collections.singletonList(String.valueOf(response));
        }
        if (cmd == Protocol.Command.DUMP && response instanceof byte[]) {
            return Collections.singletonList(getPrintableString((byte[]) response));
        }
        if (response instanceof byte[]) {
            return Collections.singletonList(new String((byte[]) response));
        }
        return Collections.singletonList(String.valueOf(response));
    }

    private String convertNestedResponseItem(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[]) {
            return new String((byte[]) value);
        }
        return JSON.toJSONString(value);
    }

    private String flushClusterDb() throws Exception {
        executeOnClusterMasters(Jedis::flushDB);
        return "OK";
    }

    private String getClusterInfo(@Nullable String section) throws Exception {
        String normalizedSection = StringUtils.trimToNull(section);
        if (StringUtil.equalsIgnoreCase(normalizedSection, "keyspace")) {
            return String.format(CLUSTER_KEYSPACE_SECTION, Optional.ofNullable(dbSize(Protocol.DEFAULT_DATABASE)).orElse(0L));
        }
        return StringUtils.defaultIfBlank(executeOnFirstClusterMaster(node ->
                normalizedSection == null ? node.info() : node.info(normalizedSection)), "");
    }

    private <T> T executeOnConfiguredClusterNode(ClusterNodeCallback<T> callback) throws Exception {
        ConnectionEndpoint endpoint = getConnectionEndpoint();
        try (Jedis jedis = new Jedis(
                new HostAndPort(endpoint.getHost(), endpoint.getPort()),
                createClientConfig(null, false))) {
            return callback.doInNode(jedis);
        }
    }

    private List<HostAndPort> loadClusterMasterEndpoints() {
        try {
            List<HostAndPort> masterEndpoints = executeOnConfiguredClusterNode(node -> extractClusterMasterEndpoints(node.clusterNodes()));
            if (masterEndpoints == null || masterEndpoints.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, HostAndPort> resolvedEndpoints = new LinkedHashMap<>();
            for (HostAndPort masterEndpoint : masterEndpoints) {
                HostAndPort resolvedEndpoint = resolveClusterNodeEndpoint(masterEndpoint);
                if (resolvedEndpoint != null) {
                    resolvedEndpoints.putIfAbsent(getClusterEndpointKey(resolvedEndpoint), resolvedEndpoint);
                }
            }
            return new ArrayList<>(resolvedEndpoints.values());
        } catch (Exception e) {
            log.debug("Failed to discover cluster master endpoints from configured seed node", e);
            return Collections.emptyList();
        }
    }

    @Nullable
    private Jedis getPreferredClusterMasterNode() {
        for (HostAndPort endpoint : loadClusterMasterEndpoints()) {
            try {
                return openClusterNode(endpoint);
            } catch (Exception e) {
                log.debug("Failed to open preferred cluster master node {}", endpoint, e);
            }
        }

        JedisCluster jedisCluster = getJedisCluster();
        if (jedisCluster == null) {
            return null;
        }
        try {
            Connection connection = jedisCluster.getConnectionFromSlot(0);
            if (connection != null) {
                return new Jedis(connection);
            }
        } catch (Exception e) {
            log.debug("Failed to get preferred cluster master node from slot cache, will fall back to other cluster nodes", e);
        }
        return null;
    }

    private List<Jedis> getClusterMasterNodes() {
        Map<String, Jedis> masters = new LinkedHashMap<>();

        for (HostAndPort endpoint : loadClusterMasterEndpoints()) {
            try {
                Jedis node = openClusterNode(endpoint);
                if (node != null) {
                    masters.putIfAbsent(getClusterEndpointKey(endpoint), node);
                }
            } catch (Exception e) {
                log.warn("Failed to connect to discovered cluster master node {}", endpoint, e);
            }
        }
        if (!masters.isEmpty()) {
            return new ArrayList<>(masters.values());
        }

        JedisCluster jedisCluster = getJedisCluster();
        if (jedisCluster == null) {
            return Collections.emptyList();
        }

        for (Map.Entry<String, ConnectionPool> entry : jedisCluster.getClusterNodes().entrySet()) {
            HostAndPort announcedEndpoint = parseClusterNodeEndpoint(entry.getKey());
            HostAndPort resolvedEndpoint = resolveClusterNodeEndpoint(announcedEndpoint);
            String endpointKey = getClusterEndpointKey(resolvedEndpoint);
            if (resolvedEndpoint == null || masters.containsKey(endpointKey)) {
                continue;
            }
            try {
                Jedis node = openClusterNode(resolvedEndpoint);
                if (node != null && isMasterNode(node)) {
                    masters.put(endpointKey, node);
                } else if (node != null) {
                    node.close();
                }
            } catch (Exception e) {
                log.warn("Failed to inspect cluster node {}", resolvedEndpoint, e);
            }
        }
        return new ArrayList<>(masters.values());
    }

    private boolean isMasterNode(Jedis node) {
        try {
            return StringUtils.containsIgnoreCase(node.info("replication"), "role:master");
        } catch (Exception e) {
            log.warn("Failed to determine cluster node role", e);
            return false;
        }
    }

    private <T> List<T> executeOnClusterMasters(ClusterNodeCallback<T> callback) throws Exception {
        List<T> result = new ArrayList<>();
        for (Jedis node : getClusterMasterNodes()) {
            try (Jedis currentNode = node) {
                result.add(callback.doInNode(currentNode));
            }
        }
        return result;
    }

    private <T> T executeOnFirstClusterMaster(ClusterNodeCallback<T> callback) throws Exception {
        try {
            T configuredNodeResult = executeOnConfiguredClusterNode(callback);
            if (configuredNodeResult != null) {
                return configuredNodeResult;
            }
        } catch (Exception e) {
            log.debug("Failed to execute cluster callback on configured seed node, will try discovered nodes", e);
        }

        Jedis preferredNode = getPreferredClusterMasterNode();
        if (preferredNode != null) {
            try (Jedis currentNode = preferredNode) {
                return callback.doInNode(currentNode);
            }
        }
        for (Jedis node : getClusterMasterNodes()) {
            try (Jedis currentNode = node) {
                return callback.doInNode(currentNode);
            }
        }
        return null;
    }

    private interface ClusterNodeCallback<T> {
        T doInNode(Jedis jedis) throws Exception;
    }

    @Getter
    static class ClusterCommandRoute {
        private final Integer keyArgIndex;
        private final String errorMessage;

        private ClusterCommandRoute(Integer keyArgIndex, String errorMessage) {
            this.keyArgIndex = keyArgIndex;
            this.errorMessage = errorMessage;
        }

        static ClusterCommandRoute firstMaster() {
            return new ClusterCommandRoute(null, null);
        }

        static ClusterCommandRoute slot(int keyArgIndex) {
            return new ClusterCommandRoute(keyArgIndex, null);
        }

        static ClusterCommandRoute unsupported(String errorMessage) {
            return new ClusterCommandRoute(null, errorMessage);
        }

        boolean isFirstMaster() {
            return errorMessage == null && keyArgIndex == null;
        }

        boolean isUnsupported() {
            return errorMessage != null;
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
    static class ClusterNodeDescriptor {
        private final String nodeId;
        private final HostAndPort announcedEndpoint;
        private final String announcedHostName;
        private final String flags;

        private ClusterNodeDescriptor(String nodeId, HostAndPort announcedEndpoint, String announcedHostName, String flags) {
            this.nodeId = nodeId;
            this.announcedEndpoint = announcedEndpoint;
            this.announcedHostName = announcedHostName;
            this.flags = flags;
        }

        private boolean isMaster() {
            return isClusterNodeMaster(flags);
        }
    }

    @Getter
    private static class SslConfiguration {
        private final SSLSocketFactory sslSocketFactory;
        private final SSLParameters sslParameters;

        private SslConfiguration(SSLSocketFactory sslSocketFactory, SSLParameters sslParameters) {
            this.sslSocketFactory = sslSocketFactory;
            this.sslParameters = sslParameters;
        }
    }

    private class ClusterJedisAdapter extends Jedis {
        private final JedisCluster delegate;

        private ClusterJedisAdapter(JedisCluster delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
        }

        @Override
        public Connection getClient() {
            throw new UnsupportedOperationException("Cluster mode does not expose a single Redis connection client");
        }

        @Override
        public String ping() {
            try {
                return executeOnFirstClusterMaster(Jedis::ping);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String flushDB() {
            try {
                return flushClusterDb();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String flushAll() {
            return flushDB();
        }

        @Override
        public boolean exists(String key) {
            return delegate.exists(key);
        }

        @Override
        public String type(String key) {
            return delegate.type(key);
        }

        @Override
        public long ttl(String key) {
            return delegate.ttl(key);
        }

        @Override
        public String get(String key) {
            return delegate.get(key);
        }

        @Override
        public long llen(String key) {
            return delegate.llen(key);
        }

        @Override
        public List<String> lrange(String key, long start, long stop) {
            return delegate.lrange(key, start, stop);
        }

        @Override
        public long scard(String key) {
            return delegate.scard(key);
        }

        @Override
        public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
            return delegate.sscan(key, cursor, params);
        }

        @Override
        public long zcard(String key) {
            return delegate.zcard(key);
        }

        @Override
        public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
            return delegate.zscan(key, cursor, params);
        }

        @Override
        public long hlen(String key) {
            return delegate.hlen(key);
        }

        @Override
        public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
            return delegate.hscan(key, cursor, params);
        }

        @Override
        public long dbSize() {
            return Optional.ofNullable(RedisPoolManager.this.dbSize(Protocol.DEFAULT_DATABASE)).orElse(0L);
        }

        @Override
        public String info(String section) {
            try {
                return getClusterInfo(section);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String info() {
            return info(null);
        }

        @Override
        public String set(String key, String value) {
            return delegate.set(key, value);
        }

        @Override
        public String setex(String key, long seconds, String value) {
            return delegate.setex(key, seconds, value);
        }

        @Override
        public long expire(String key, long seconds) {
            return delegate.expire(key, seconds);
        }

        @Override
        public long del(String key) {
            return delegate.del(key);
        }

        @Override
        public long del(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                deleted += delegate.del(key);
            }
            return deleted;
        }

        @Override
        public long lpush(String key, String... strings) {
            return delegate.lpush(key, strings);
        }

        @Override
        public long hset(String key, String field, String value) {
            return delegate.hset(key, field, value);
        }

        @Override
        public long sadd(String key, String... members) {
            return delegate.sadd(key, members);
        }

        @Override
        public long zadd(String key, double score, String member) {
            return delegate.zadd(key, score, member);
        }

        @Override
        public String lset(String key, long index, String value) {
            return delegate.lset(key, index, value);
        }

        @Override
        public long lrem(String key, long count, String value) {
            return delegate.lrem(key, count, value);
        }

        @Override
        public long srem(String key, String... members) {
            return delegate.srem(key, members);
        }

        @Override
        public long zrem(String key, String... members) {
            return delegate.zrem(key, members);
        }

        @Override
        public long hdel(String key, String... fields) {
            return delegate.hdel(key, fields);
        }

        @Override
        public long renamenx(String oldkey, String newkey) {
            return delegate.renamenx(oldkey, newkey);
        }

        @Override
        public ScanResult<String> scan(String cursor, ScanParams params) {
            String pattern = params == null ? "*" : StringUtils.defaultIfBlank(params.match(), "*");
            List<String> keys = scanKeys(cursor, pattern, 20000, Protocol.DEFAULT_DATABASE);
            return new ScanResult<>(ScanParams.SCAN_POINTER_START, keys);
        }
    }
}
