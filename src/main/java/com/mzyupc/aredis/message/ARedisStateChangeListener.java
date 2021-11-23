package com.mzyupc.aredis.message;

import com.intellij.util.messages.Topic;
/**
 * @author mzyupc@163.com
 * @date 2021/11/22 3:13 下午
 */
public interface ARedisStateChangeListener {

    @Topic.ProjectLevel
    Topic<ARedisStateChangeListener> AREDIS_STATE_CHANGE_TOPIC = Topic.create("AREDIS_STATE_CHANGE", ARedisStateChangeListener.class);

    void stateChanged();
}
