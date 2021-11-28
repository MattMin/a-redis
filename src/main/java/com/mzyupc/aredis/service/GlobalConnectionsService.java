package com.mzyupc.aredis.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "RedisHelper", storages = {
        @Storage("redisHelper.xml")
})
public class GlobalConnectionsService implements PersistentStateComponent<GlobalConnectionsService.State> {

    private State myState = new State();

    public static GlobalConnectionsService getInstance() {
        return ApplicationManager.getApplication().getService(GlobalConnectionsService.class);
    }

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

    public Boolean getReloadAfterAddingTheKey() {
        return myState.getReloadAfterAddingTheKey();
    }

    public void setReloadAfterAddingTheKey(Boolean reloadAfterAddingTheKey) {
        myState.setReloadAfterAddingTheKey(reloadAfterAddingTheKey);
    }

    @Data
    static class State {
        private List<ConnectionInfo> connections = new ArrayList<>();
        private Boolean reloadAfterAddingTheKey;
    }
}
