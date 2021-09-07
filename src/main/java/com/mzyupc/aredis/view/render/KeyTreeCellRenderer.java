package com.mzyupc.aredis.view.render;

import com.intellij.icons.AllIcons;
import com.mzyupc.aredis.vo.KeyInfo;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/22 1:48 下午
 */
public class KeyTreeCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        // 根节点
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        Object userObject = node.getUserObject();
        if (row == 0) {
            this.setIcon(AllIcons.Debugger.Db_array);
        } else if (leaf) {
            if (userObject instanceof KeyInfo) {
                this.setIcon(AllIcons.Debugger.Value);
            }
        } else {
            this.setIcon(AllIcons.Nodes.Folder);
        }

        return this;
    }
}
