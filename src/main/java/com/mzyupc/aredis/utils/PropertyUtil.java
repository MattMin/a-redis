package com.mzyupc.aredis.utils;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author mzyupc@163.com
 */
public class PropertyUtil {
    /**
     * connection id集合的key
     */
    private static final String CONNECTION_ID_LIST_KEY = "connectionIds";

    private static PropertiesComponent properties;

    private static PropertyUtil instance = new PropertyUtil();

    private PropertyUtil () {}

    public static PropertyUtil getInstance(Project project) {
        if (properties == null) {
            properties = PropertiesComponent.getInstance(project);
        }
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

        return Arrays.stream(ids).map(id -> {
            String connection = properties.getValue(id);
            return JSON.parseObject(connection, ConnectionInfo.class);
        }).collect(Collectors.toList());
    }

    /**
     * 保存连接信息
     * @param connectionInfo 连接信息
     * @return 连接ID
     */
    public String saveConnection(ConnectionInfo connectionInfo) {
        if (connectionInfo == null) {
            return null;
        }

        String id = UUID.randomUUID().toString();
        connectionInfo.setId(id);
        properties.setValue(id, JSON.toJSONString(connectionInfo));

        String[] ids = properties.getValues(CONNECTION_ID_LIST_KEY);
        if (ids == null || ids.length == 0) {
            properties.setValues(CONNECTION_ID_LIST_KEY, new String[]{id});
        } else {
            ArrayList<String> idList = Lists.newArrayList(ids);
            idList.add(id);
            properties.setValues(CONNECTION_ID_LIST_KEY, idList.toArray(new String[]{}));
        }
        return id;
    }

    /**
     * 删除连接
     *
     * @param id 连接ID
     */
    public void removeConnection(String id) {
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
}
