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

    public Map<Integer, String> getGroupSymbols() {
        return groupSymbols;
    }

    public Boolean getGlobal() {
        return global;
    }
}
