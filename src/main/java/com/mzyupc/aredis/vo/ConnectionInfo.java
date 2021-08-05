package com.mzyupc.aredis.vo;

import lombok.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/5 4:33 下午
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

    private String password;

    @Override
    public String toString() {
        return this.name;
    }
}
