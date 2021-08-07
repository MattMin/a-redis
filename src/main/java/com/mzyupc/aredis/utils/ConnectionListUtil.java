package com.mzyupc.aredis.utils;

import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.vo.ConnectionInfo;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author mzyupc@163.com
 */
public class ConnectionListUtil {

    public static void addConnectionToList(JPanel connectionPanel, ConnectionInfo connection) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(connection, true);

        // 模拟子节点
        root.add(new DefaultMutableTreeNode("节点1"));
        root.add(new DefaultMutableTreeNode("节点2"));
        root.add(new DefaultMutableTreeNode("节点3"));

        Tree tree = new Tree(root);
        // tree左对齐
        tree.setAlignmentX(Component.LEFT_ALIGNMENT);

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
                            // todo 如果是折叠的, 查询这个连接的所有DB

                        }
                    }
                }
            }
        });

        connectionPanel.add(tree);
    }
}
