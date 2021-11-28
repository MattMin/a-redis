package com.mzyupc.aredis.message;

import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.view.ConnectionManager;

/**
 * 连接更新监听器
 *
 * @author mzyupc@163.com
 * @date 2021/11/22 3:41 下午
 */
public class ConnectionChangeListener implements ARedisStateChangeListener {

    private final ConnectionManager connectionManager;

    public ConnectionChangeListener(Project project) {
        this.connectionManager = ConnectionManager.getInstance(project);
    }

    @Override
    public void stateChanged(ARedisStateChangeEvent event) {
        connectionManager.reloadConnections();
    }
}
