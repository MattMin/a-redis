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
}
