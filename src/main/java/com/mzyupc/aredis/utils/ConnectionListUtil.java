package com.mzyupc.aredis.utils;

import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.RedisDbInfo;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * @author mzyupc@163.com
 */
public class ConnectionListUtil {

    /**
     * 添加连接到connectionPanel
     *
     * @param connectionPanel
     * @param connection
     * @param connectionRedisMap
     */
    public static void addConnectionToList(JPanel connectionPanel, ConnectionInfo connection, Map<String, RedisPoolMgr> connectionRedisMap) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(connection, true);

        Tree tree = new Tree(root);
        // tree左对齐
        tree.setAlignmentX(Component.LEFT_ALIGNMENT);

        tree.addTreeExpansionListener(new TreeExpansionListener() {

            /**
             * tree 展开事件
             * @param event
             */
            @Override
            public void treeExpanded(TreeExpansionEvent event) {

                // 如果根节点没有子节点, 则查询DB
                if (root.getChildCount() == 0) {
                    RedisPoolMgr redisPoolMgr = new RedisPoolMgr(connection);
                    int dbCount = redisPoolMgr.getDbCount();

                    // 创建db列表
                    for (int i = 0; i < dbCount; i++) {
                        Long keyCount = redisPoolMgr.dbSize(i);
                        RedisDbInfo dbInfo = RedisDbInfo.builder()
                                .index(i)
                                .keyCount(keyCount)
                                .build();
                        root.add(new DefaultMutableTreeNode(dbInfo, false));
                    }

                    // 保存这个连接的redisPool
                    connectionRedisMap.put(connection.getId(), redisPoolMgr);
                }
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {

            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 双击事件
                if (e.getClickCount() == 2) {
                    TreePath pathForLocation = tree.getPathForLocation(e.getX(), e.getY());

                    if (pathForLocation == null) {
                        return;
                    }

                    // 如果是根节点
                    if (pathForLocation.getPathCount() == 1) {
                        if (tree.isCollapsed(pathForLocation)) {
                            // 如果根节点没有子节点, 则查询DB
                            if (root.getChildCount() == 0) {
                                RedisPoolMgr redisPoolMgr = new RedisPoolMgr(connection);
                                int dbCount = redisPoolMgr.getDbCount();

                                // 创建db列表
                                for (int i = 0; i < dbCount; i++) {
                                    Long keyCount = redisPoolMgr.dbSize(i);
                                    RedisDbInfo dbInfo = RedisDbInfo.builder()
                                            .index(i)
                                            .keyCount(keyCount)
                                            .build();
                                    root.add(new DefaultMutableTreeNode(dbInfo, false));
                                }

                                // 保存这个连接的redisPool
                                connectionRedisMap.put(connection.getId(), redisPoolMgr);
                            }

                        }
                    }
                }
            }
        });

        connectionPanel.add(tree);
    }

    /**
     * 移除选中的连接
     *
     * @param connectionPanel
     * @param connectionRedisMap
     * @param propertyUtil
     */
    public static void removeConnectionFromList(JPanel connectionPanel, Map<String, RedisPoolMgr> connectionRedisMap, PropertyUtil propertyUtil) {
        // 从connectionPanel移除元素
        ConnectionInfo connectionInfo = getSelectedConnectionAndRemove(connectionPanel);
        if (connectionInfo == null) {
            return;
        }

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

    /**
     * 查询connectionPanel中选中的connectionInfo, 并删除选中的tree
     *
     * @param connectionPanel
     * @return
     */
    private static ConnectionInfo getSelectedConnectionAndRemove(JPanel connectionPanel) {
        // 查询选中的 connection
        Component[] components = connectionPanel.getComponents();
        for (Component component : components) {
            if (component instanceof Tree) {
                Tree connectionTree = (Tree) component;
                boolean rowSelected = connectionTree.isRowSelected(0);
                if (rowSelected) {
                    DefaultMutableTreeNode[] selectedNodes = connectionTree.getSelectedNodes(
                            DefaultMutableTreeNode.class,
                            DefaultMutableTreeNode::isRoot
                    );
                    DefaultMutableTreeNode root = selectedNodes[0];
                    // 移除选中的组件
                    connectionPanel.remove(component);
                    // 返回connectionInfo
                    return (ConnectionInfo) root.getUserObject();
                }
            }
        }
        return null;
    }
}
