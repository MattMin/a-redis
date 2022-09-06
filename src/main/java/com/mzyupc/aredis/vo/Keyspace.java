package com.mzyupc.aredis.vo;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Keyspace {
    private Integer db;
    private Long keys;
    private Long expires;
    private Long avgTtl;
}
