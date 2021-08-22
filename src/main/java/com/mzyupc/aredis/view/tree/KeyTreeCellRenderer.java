package com.mzyupc.aredis.view.tree;

import com.intellij.icons.AllIcons;

import javax.swing.*;
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
        if (row == 0) {
            this.setIcon(AllIcons.Debugger.Db_array);
        } else if (leaf) {
            this.setIcon(AllIcons.Debugger.Value);
            // todo 置灰删除的key
//            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
//            KeyInfo keyInfo = (KeyInfo) node.getUserObject();
//            if (keyInfo.isDel()) {
//                this.setTextNonSelectionColor(JBColor.GRAY);
//            } else {
//                this.setTextNonSelectionColor(JBColor.BLACK);
//            }
        } else {
            this.setIcon(AllIcons.Actions.MenuOpen);
        }

        return this;
    }
}
