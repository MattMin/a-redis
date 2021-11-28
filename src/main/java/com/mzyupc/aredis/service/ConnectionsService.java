package com.mzyupc.aredis.service;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "RedisHelper", storages = {
        @Storage(StoragePathMacros.WORKSPACE_FILE)
})
public class ConnectionsService implements PersistentStateComponent<ConnectionsService.State> {

    public static ConnectionsService getInstance(Project project) {
        return project.getService(ConnectionsService.class);
    }

    @Data
    static class State {
        private List<ConnectionInfo> connections = new ArrayList<>();
    }

    private State myState = new State();

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public List<ConnectionInfo> getConnections() {
        return myState.getConnections();
    }
}
