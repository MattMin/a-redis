package com.mzyupc.aredis.view.render;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.mzyupc.aredis.vo.KeyInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author mzyupc@163.com
 * @date 2021/8/22 1:48 下午
 */
public class KeyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row,
                                      boolean hasFocus) {
        // 根节点
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        Object userObject = node.getUserObject();
        // 节点名
        this.append(node.toString());
        // 节点图标
        if (row == 0) {
            this.setIcon(AllIcons.Debugger.Db_array);
        } else if (leaf) {
            if (userObject instanceof KeyInfo) {
                this.setIcon(AllIcons.Debugger.Value);
            }
        } else {
            this.setIcon(AllIcons.Nodes.Folder);
        }

    }
}
