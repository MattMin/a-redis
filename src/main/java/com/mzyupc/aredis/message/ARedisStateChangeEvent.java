package com.mzyupc.aredis.message;

import lombok.Builder;
import lombok.Data;

/**
 * ARedis状态修改事件
 *
 * @author mzyupc@163.com
 * @date 2021/11/23 8:02 下午
 */
@Data
@Builder
public class ARedisStateChangeEvent {
    private ARedisEventType eventType;
    private Object msg;
}
