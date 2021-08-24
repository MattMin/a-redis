package com.mzyupc.aredis.vo;

import lombok.*;

/**
 * @author mzyupc@163.com
 *
 * 分段的key
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FragmentedKey {

    private String fragmentedKey;

    @Override
    public String toString() {
        return fragmentedKey;
    }
}
