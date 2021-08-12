package com.mzyupc.aredis.utils;

import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.RedisDbInfo;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author mzyupc@163.com
 */
public class ConnectionListUtil {
    /**
     * 激活的连接的个数
     */
    public static boolean isConnected = false;

    /**
     * 连接id对应的redis连接
     */
    public static Map<String, RedisPoolMgr> connectionRedisMap = new HashMap<>();

    /**
     * 添加连接到connectionPanel
     *
     * @param treeModel
     * @param connection
     */
    public static void addConnectionToList(DefaultTreeModel treeModel, ConnectionInfo connection) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.add(new DefaultMutableTreeNode(connection));

        // 保存这个连接的redisPool
        RedisPoolMgr redisPoolMgr = new RedisPoolMgr(connection);
        connectionRedisMap.put(connection.getId(), redisPoolMgr);

        treeModel.reload();
    }

    /**
     * 添加DB子节点到connection节点下面
     *
     * @param connectionNode
     */
    public static void addDbs2Connection(DefaultMutableTreeNode connectionNode) {
        ConnectionInfo connection = (ConnectionInfo) connectionNode.getUserObject();
        RedisPoolMgr redisPoolMgr = connectionRedisMap.get(connection.getId());
        int dbCount = redisPoolMgr.getDbCount();

        // 创建db列表
        for (int i = 0; i < dbCount; i++) {
            Long keyCount = redisPoolMgr.dbSize(i);
            RedisDbInfo dbInfo = RedisDbInfo.builder()
                    .index(i)
                    .keyCount(keyCount)
                    .build();
            connectionNode.add(new DefaultMutableTreeNode(dbInfo, false));
        }
    }

    public static void removeAllDbs(DefaultMutableTreeNode connectionNode) {
        connectionNode.removeAllChildren();
        ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
        String id = connectionInfo.getId();
    }

    /**
     * 移除选中的连接
     *
     * @param connectionTree
     * @param connectionRedisMap
     * @param propertyUtil
     */
    public static void removeConnectionFromTree(Tree connectionTree, Map<String, RedisPoolMgr> connectionRedisMap, PropertyUtil propertyUtil) {
        // 从connectionTree移除元素
        List<ConnectionInfo> connectionInfoList = getSelectedConnectionAndRemove(connectionTree);
        if (CollectionUtils.isEmpty(connectionInfoList)) {
            return;
        }

        for (ConnectionInfo connectionInfo : connectionInfoList) {
            // 关闭redis连接池
            String connectionInfoId = connectionInfo.getId();
            RedisPoolMgr redisPoolMgr = connectionRedisMap.get(connectionInfoId);
            // 查询过DB才有
            if (redisPoolMgr != null) {
                redisPoolMgr.invalidate();
            }

            // 从connectionRedisMap中移除
            connectionRedisMap.remove(connectionInfoId);

            // 从properties中移除
            propertyUtil.removeConnection(connectionInfoId);
        }
    }


    /**
     * 查询connectionPanel中选中的connectionInfo, 并删除选中的tree
     *
     * @param connectionTree
     * @return
     */
    @Nullable
    private static List<ConnectionInfo> getSelectedConnectionAndRemove(Tree connectionTree) {
        // 查询选中的 connection
        DefaultTreeModel connectionTreeModel = (DefaultTreeModel) connectionTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) connectionTreeModel.getRoot();

        TreePath[] selectionPaths = connectionTree.getSelectionPaths();
        if (selectionPaths == null || selectionPaths.length == 0) {
            return null;
        }

        List<ConnectionInfo> result = new ArrayList<>();
        for (TreePath selectionPath : selectionPaths) {
            Object[] path = selectionPath.getPath();
            if (path.length != 2) {
                continue;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
            result.add((ConnectionInfo) connectionNode.getUserObject());
            root.remove(connectionNode);
        }

        if (!result.isEmpty()) {
            connectionTreeModel.reload();
        }
        return result;
    }

    /**
     * 复制选中的连接
     *
     * @param connectionTree
     * @return
     */
    public static ConnectionInfo duplicateConnections(Tree connectionTree) {
        TreePath selectionPath = connectionTree.getSelectionPath();
        if (selectionPath == null || selectionPath.getPathCount() != 2) {
            return null;
        }
        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
        ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
        ConnectionInfo newConnectionInfo = ConnectionInfo.builder()
                .id(UUID.randomUUID().toString())
                .name(connectionInfo.getName() + "_copy")
                .url(connectionInfo.getUrl())
                .port(connectionInfo.getPort())
                .password(connectionInfo.getPassword())
                .build();
        addConnectionToList((DefaultTreeModel) connectionTree.getModel(), newConnectionInfo);
        return newConnectionInfo;
    }
}
