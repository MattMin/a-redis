package com.mzyupc.aredis.view.render;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

/**
 * @author mzyupc@163.com
 * @date 2021/8/22 1:48 下午
 */
public class ConnectionTreeCellRenderer extends ColoredTreeCellRenderer {

    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row,
                                      boolean hasFocus) {
        // 根节点
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        TreeNode[] path = node.getPath();
        // 节点名
        this.append(node.toString());
        // 节点图标
        if (path.length == 2) {
            this.setIcon(AllIcons.Debugger.Db_db_object);
        } else {
            this.setIcon(AllIcons.Debugger.Db_array);
        }

    }
}
