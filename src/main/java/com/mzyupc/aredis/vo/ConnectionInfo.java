package com.mzyupc.aredis.vo;

import com.google.common.base.Objects;
import com.intellij.util.xmlb.annotations.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * @author mzyupc@163.com
 */
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConnectionInfo {


    private String id;

    private String name;

    private String url;

    private String port;

    private String user;

    @Transient
    private String password;

    private Boolean sshTunnel;

    private String tunnelHost;

    private String tunnelPort;

    private String tunnelUser;

    @Transient
    private String tunnelPassword;

    private String tunnelPrivateKeyPath;

    @Transient
    private String tunnelPassphrase;

    private Boolean sslTls;

    private Boolean sslTrustAllCertificates;

    private Boolean sslVerifyHostname;

    private String sslTruststorePath;

    @Transient
    private String sslTruststorePassword;

    private String sslKeystorePath;

    @Transient
    private String sslKeystorePassword;

    /**
     * 每个db的分组标识
     */
    private Map<Integer, String> groupSymbols;

    /**
     * 是否全局配置
     */
    private Boolean global;

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConnectionInfo that = (ConnectionInfo) o;
        return Objects.equal(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    @Transient
    public String getPassword() {
        return password;
    }

    public Boolean getSshTunnel() {
        return sshTunnel;
    }

    public String getTunnelHost() {
        return tunnelHost;
    }

    public String getTunnelPort() {
        return tunnelPort;
    }

    public String getTunnelUser() {
        return tunnelUser;
    }

    @Transient
    public String getTunnelPassword() {
        return tunnelPassword;
    }

    public String getTunnelPrivateKeyPath() {
        return tunnelPrivateKeyPath;
    }

    @Transient
    public String getTunnelPassphrase() {
        return tunnelPassphrase;
    }

    public Boolean getSslTls() {
        return sslTls;
    }

    public Boolean getSslTrustAllCertificates() {
        return sslTrustAllCertificates;
    }

    public Boolean getSslVerifyHostname() {
        return sslVerifyHostname;
    }

    public String getSslTruststorePath() {
        return sslTruststorePath;
    }

    @Transient
    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }

    public String getSslKeystorePath() {
        return sslKeystorePath;
    }

    @Transient
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    public Map<Integer, String> getGroupSymbols() {
        return groupSymbols;
    }

    public Boolean getGlobal() {
        return global;
    }
}
