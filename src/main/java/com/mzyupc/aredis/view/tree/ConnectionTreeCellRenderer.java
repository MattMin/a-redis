package com.mzyupc.aredis.view.tree;

import com.intellij.icons.AllIcons;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/22 1:48 下午
 */
public class ConnectionTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        // 根节点
        DefaultMutableTreeNode node =  (DefaultMutableTreeNode) value;
        TreeNode[] path = node.getPath();
        if (path.length == 2) {
            this.setIcon(AllIcons.Debugger.Db_db_object);
        } else {
            this.setIcon(AllIcons.Debugger.Db_array);
        }

        return this;
    }
}
