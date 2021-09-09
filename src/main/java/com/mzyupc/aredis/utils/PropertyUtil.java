package com.mzyupc.aredis.utils;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.mzyupc.aredis.view.ARedisKeyValueDisplayPanel.DEFAULT_GROUP_SYMBOL;

/**
 * @author mzyupc@163.com
 */
public class PropertyUtil implements PersistentStateComponent {
    /**
     * connection id集合的key
     */
    private static final String CONNECTION_ID_LIST_KEY = "connectionIds";

    private static final String RELOAD_AFTER_ADDING_THE_KEY = "reloadAfterAddingTheKey";

    private static final String DB_COUNT_KEY = "dbCount:";

    private static PropertiesComponent properties;

    private static PropertyUtil instance = new PropertyUtil();

    private PropertyUtil () {}

    public static PropertyUtil getInstance(Project project) {
        properties = PropertiesComponent.getInstance(project);
        return instance;
    }

    /**
     * 返回所有已配置的连接id
     *
     * @return 连接列表元素
     */
    public List<ConnectionInfo> getConnections() {
        String[] ids = properties.getValues(CONNECTION_ID_LIST_KEY);
        if (ids == null || ids.length == 0) {
            return Lists.newArrayList();
        }

        List<ConnectionInfo> result = new ArrayList<>();
        for (String id : ids) {
            String connection = properties.getValue(id);
            if (StringUtils.isEmpty(connection)) {
                removeConnection(id, null);
                continue;
            }
            result.add(JSON.parseObject(connection, ConnectionInfo.class));
        }
        return result;
    }

    /**
     * 保存连接信息
     * @param connectionInfo 连接信息
     * @return 连接ID
     *
     * todo 持久化敏感信息
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
        properties.setValue(connectionInfoId, JSON.toJSONString(connectionInfo));

        String[] ids = properties.getValues(CONNECTION_ID_LIST_KEY);
        if (ids == null || ids.length == 0) {
            properties.setValues(CONNECTION_ID_LIST_KEY, new String[]{connectionInfoId});
        } else {
            ArrayList<String> idList = Lists.newArrayList(ids);
            idList.add(connectionInfoId);
            properties.setValues(CONNECTION_ID_LIST_KEY, idList.toArray(new String[]{}));
        }
        return connectionInfoId;
    }

    /**
     * 删除连接
     *
     * @param id 连接ID
     * @param redisPoolManager
     */
    public void removeConnection(String id, RedisPoolManager redisPoolManager) {
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
        if (redisPoolManager != null) {
            for (int i = 0; i < getDbCount(id); i++) {
                removeGroupSymbol(DbInfo.builder()
                        .connectionId(id)
                        .index(i)
                        .build());
            }
        }
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

        String value = properties.getValue(id);
        if (StringUtils.isEmpty(value)) {
            return null;
        }

        return JSON.parseObject(value, ConnectionInfo.class);
    }

    public boolean getReloadAfterAddingTheKey() {
        return properties.getBoolean(RELOAD_AFTER_ADDING_THE_KEY, false);
    }

    public void setReloadAfterAddingTheKey(boolean reloadAfterAddingTheKey) {
        properties.setValue(RELOAD_AFTER_ADDING_THE_KEY, reloadAfterAddingTheKey);
    }

    public void saveGroupSymbol(DbInfo dbInfo, String groupSymbol) {
        properties.setValue(getGroupSymbolKey(dbInfo), groupSymbol);
    }

    public String getGroupSymbol(DbInfo dbInfo) {
        return properties.getValue(getGroupSymbolKey(dbInfo), DEFAULT_GROUP_SYMBOL);
    }

    public void removeGroupSymbol(DbInfo dbInfo) {
        properties.unsetValue(getGroupSymbolKey(dbInfo));
    }

    public void setDbCount(String connectionId, int dbCount) {
        properties.setValue(DB_COUNT_KEY + connectionId, dbCount + "");
    }

    public int getDbCount(String connectionId) {
        return properties.getInt(DB_COUNT_KEY + connectionId, 0);
    }

    private String getGroupSymbolKey(DbInfo dbInfo) {
        return dbInfo.getConnectionId() + ":" + dbInfo.getIndex();
    }

    @Nullable
    @Override
    public Object getState() {
        return null;
    }

    @Override
    public void loadState(@NotNull Object o) {

    }
}
