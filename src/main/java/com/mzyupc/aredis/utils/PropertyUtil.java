package com.mzyupc.aredis.utils;

import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.service.ConnectionsService;
import com.mzyupc.aredis.service.GlobalConnectionsService;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import org.apache.commons.lang.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.mzyupc.aredis.view.ARedisKeyValueDisplayPanel.DEFAULT_GROUP_SYMBOL;

/**
 * @author mzyupc@163.com
 */
public class PropertyUtil {
    /**
     * connection id集合的key
     */
    private static final String CONNECTION_ID_LIST_KEY = "connectionIds";

    private static final String RELOAD_AFTER_ADDING_THE_KEY = "reloadAfterAddingTheKey";

    private static final String DB_COUNT_KEY = "dbCount:";

    private PropertiesComponent properties;

    private GlobalConnectionsService globalConnectionsService;

    private ConnectionsService connectionsService;

    private PropertyUtil(Project project) {
        properties = PropertiesComponent.getInstance(project);
        globalConnectionsService = GlobalConnectionsService.getInstance();
        connectionsService = ConnectionsService.getInstance(project);
    }

    public static PropertyUtil getInstance(Project project) {
        PropertyUtil propertyUtil = new PropertyUtil(project);

        // 迁移之前的配置
        // 迁移RELOAD_AFTER_ADDING_THE_KEY
        if (propertyUtil.properties.isValueSet(RELOAD_AFTER_ADDING_THE_KEY)) {
            final boolean aBoolean = propertyUtil.properties.getBoolean(RELOAD_AFTER_ADDING_THE_KEY, false);
            propertyUtil.globalConnectionsService.setReloadAfterAddingTheKey(aBoolean);
            propertyUtil.properties.unsetValue(RELOAD_AFTER_ADDING_THE_KEY);
        }

        // 迁移CONNECTION_ID_LIST_KEY
        final List<ConnectionInfo> connections = propertyUtil.getConnectionsOld();
        if (!connections.isEmpty()) {
            final List<ConnectionInfo> newConnections = propertyUtil.connectionsService.getConnections();
            for (ConnectionInfo connection : connections) {
                connection.setGlobal(false);
                propertyUtil.removeConnectionOld(connection.getId());
                newConnections.add(connection);
            }
            propertyUtil.properties.unsetValue(CONNECTION_ID_LIST_KEY);
        }
        return propertyUtil;
    }

    /**
     * 返回所有已配置的连接id
     *
     * @return 连接列表元素
     */
    public List<ConnectionInfo> getConnectionsOld() {
        String[] ids = properties.getValues(CONNECTION_ID_LIST_KEY);
        if (ids == null || ids.length == 0) {
            return Lists.newArrayList();
        }

        List<ConnectionInfo> result = new ArrayList<>();
        for (String id : ids) {
            String connection = properties.getValue(id);
            if (StringUtils.isEmpty(connection)) {
                removeConnectionOld(id);
                continue;
            }
            result.add(JSON.parseObject(connection, ConnectionInfo.class));
        }
        return result;
    }

    public List<ConnectionInfo> getConnections() {
        final List<ConnectionInfo> globalConnections = globalConnectionsService.getConnections();
        final List<ConnectionInfo> connections = connectionsService.getConnections();
        if (connections.isEmpty() && globalConnections.isEmpty()) {
            return Lists.newArrayList();
        }

        List<ConnectionInfo> result = new ArrayList<>(globalConnections.size() + connections.size());
        for (ConnectionInfo connection : globalConnections) {
            connection.setGlobal(true);
            result.add(connection);
        }
        for (ConnectionInfo connection : connections) {
            connection.setGlobal(false);
            result.add(connection);
        }
        return result;
    }

    /**
     * 保存连接信息
     *
     * @param connectionInfo 连接信息
     * @return 连接ID
     */
    public String saveConnection(ConnectionInfo connectionInfo) {
        if (connectionInfo == null) {
            return null;
        }

        String connectionInfoId = connectionInfo.getId();
        if (StringUtils.isEmpty(connectionInfoId)) {
            connectionInfoId = UUID.randomUUID().toString();
        }

        connectionInfo.setId(connectionInfoId);

        // Brainless deletion
        globalConnectionsService.getConnections().remove(connectionInfo);
        connectionsService.getConnections().remove(connectionInfo);
        if (Boolean.TRUE.equals(connectionInfo.getGlobal())) {
            globalConnectionsService.getConnections().add(connectionInfo);
        } else {
            connectionsService.getConnections().add(connectionInfo);
        }
        return connectionInfoId;
    }

    /**
     * 删除连接
     *
     * @param id 连接ID
     */
    public void removeConnectionOld(String id) {
        String[] ids = properties.getValues(CONNECTION_ID_LIST_KEY);
        if (ids == null || ids.length == 0) {
            return;
        }

        ArrayList<String> idList = Lists.newArrayList(ids);
        if (!idList.contains(id)) {
            return;
        }

        idList.remove(id);
        properties.setValues(CONNECTION_ID_LIST_KEY, idList.toArray(new String[]{}));
        properties.unsetValue(id);

        // 移除groupSymbol
        for (int i = 0; i < getDbCountOld(id); i++) {
            properties.unsetValue(getGroupSymbolKey(DbInfo.builder()
                    .connectionId(id)
                    .index(i)
                    .build()));
        }
        properties.unsetValue(DB_COUNT_KEY + id);
    }

    public void removeConnection(ConnectionInfo connectionInfo, RedisPoolManager redisPoolManager) {
        globalConnectionsService.getConnections().remove(connectionInfo);
        connectionsService.getConnections().remove(connectionInfo);
    }

    /**
     * 查询连接
     *
     * @param id 连接ID
     * @return 连接信息
     */
    public ConnectionInfo getConnection(String id) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }

        final Map<String, ConnectionInfo> collect = getConnections().stream()
                .collect(Collectors.toMap(ConnectionInfo::getId, Function.identity()));

        return collect.get(id);
    }

    public boolean getReloadAfterAddingTheKey() {
        return globalConnectionsService.getReloadAfterAddingTheKey() != null && globalConnectionsService.getReloadAfterAddingTheKey();
    }

    public void setReloadAfterAddingTheKey(boolean reloadAfterAddingTheKey) {
        globalConnectionsService.setReloadAfterAddingTheKey(reloadAfterAddingTheKey);
    }

    public void saveGroupSymbol(DbInfo dbInfo, String groupSymbol) {
        final ConnectionInfo connection = getConnection(dbInfo.getConnectionId());
        Map<Integer, String> groupSymbols = connection.getGroupSymbols();
        if (groupSymbols == null) {
            groupSymbols = new HashMap<>();
            connection.setGroupSymbols(groupSymbols);
        }
        groupSymbols.put(dbInfo.getIndex(), groupSymbol);
    }

    public String getGroupSymbol(DbInfo dbInfo) {
        return Optional.ofNullable(getConnection(dbInfo.getConnectionId()).getGroupSymbols())
                .map(e -> e.getOrDefault(dbInfo.getIndex(), DEFAULT_GROUP_SYMBOL))
                .orElse(DEFAULT_GROUP_SYMBOL);
    }

    public void setDbCount(String connectionId, int dbCount) {
        //properties.setValue(DB_COUNT_KEY + connectionId, dbCount + "");
    }

    public int getDbCountOld(String connectionId) {
        return properties.getInt(DB_COUNT_KEY + connectionId, 0);
    }

    private String getGroupSymbolKey(DbInfo dbInfo) {
        return dbInfo.getConnectionId() + ":" + dbInfo.getIndex();
    }
}
