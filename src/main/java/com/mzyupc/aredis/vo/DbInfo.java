package com.mzyupc.aredis.vo;

import com.google.common.base.Objects;
import lombok.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/7 3:30 下午
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DbInfo {

    /**
     * db index, from 0
     */
    private Integer index;

    /**
     * key的个数
     */
    private Long keyCount;

    private String connectionId;

    @Override
    public String toString() {
        return String.format("DB%s (%s)", index, keyCount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DbInfo dbInfo = (DbInfo) o;
        return Objects.equal(index, dbInfo.index) && Objects.equal(connectionId, dbInfo.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(index, connectionId);
    }
}
