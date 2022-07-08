package com.mzyupc.aredis.vo;

import com.google.common.base.Objects;
import lombok.*;

import java.util.Map;

/**
 * @author mzyupc@163.com
 */
@Getter
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
}
