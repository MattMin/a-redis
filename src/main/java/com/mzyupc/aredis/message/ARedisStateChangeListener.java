package com.mzyupc.aredis.message;

import com.intellij.util.messages.Topic;

/**
 * ARedis状态更新监听器接口
 *
 * @author mzyupc@163.com
 * @date 2021/11/22 3:13 下午
 */
public interface ARedisStateChangeListener {

    @Topic.ProjectLevel
    Topic<ARedisStateChangeListener> AREDIS_STATE_CHANGE_TOPIC = Topic.create("AREDIS_STATE_CHANGE", ARedisStateChangeListener.class);

    void stateChanged(ARedisStateChangeEvent event);
}
