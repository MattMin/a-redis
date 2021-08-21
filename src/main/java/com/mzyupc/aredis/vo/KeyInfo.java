package com.mzyupc.aredis.vo;

import lombok.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/21 9:12 下午
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KeyInfo {

    private String key;

    /**
     * 删除标记, true:删除
     */
    private boolean del;

    @Override
    public String toString() {
        return del ? "(Removed) " + key : key;
    }
}
