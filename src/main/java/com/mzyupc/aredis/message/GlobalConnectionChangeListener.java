package com.mzyupc.aredis.message;

import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.view.ConnectionManager;

/**
 * @author mzyupc@163.com
 * @date 2021/11/22 3:41 下午
 */
public class GlobalConnectionChangeListener implements ARedisStateChangeListener {

    private final Project project;
    private ConnectionManager connectionManager;

    public GlobalConnectionChangeListener(Project project) {
        this.project = project;
        this.connectionManager = ConnectionManager.getInstance(project);
    }

    @Override
    public void stateChanged() {
        connectionManager.reloadConnections();
    }
}
