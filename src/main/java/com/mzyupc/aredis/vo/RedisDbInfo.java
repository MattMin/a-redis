package com.mzyupc.aredis.vo;

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
public class RedisDbInfo {

    /**
     * db index, from 0
     */
    private Integer index;

    /**
     * key的个数
     */
    private Long keyCount;

    @Override
    public String toString() {
        return String.format("DB%s (%s)", index, keyCount);
    }
}
