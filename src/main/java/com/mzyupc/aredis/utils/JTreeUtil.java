package com.mzyupc.aredis.utils;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;

/**
 * @author mzyupc@163.com
 * @date 2021/8/12 2:33 下午
 */
public class JTreeUtil {

    /**
     * 展开所有
     *
     * @param tree
     */
    public static void expandTree(JTree tree) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        expandAll(tree, new TreePath(root));
    }


    /**
     * 展开指定路径
     *
     * @param tree
     * @param parent
     */
    public static void expandAll(JTree tree, TreePath parent) {
        // Traverse children
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0) {
            for (Enumeration<? extends TreeNode> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                expandAll(tree, path);
            }
        }

        tree.expandPath(parent);
    }
}
