package com.mzyupc.aredis.action;

import com.intellij.icons.AllIcons;

/**
 * @author mzyupc@163.com
 *
 * 清空
 */
public class ClearAction extends CustomAction {

    public ClearAction() {
        super("Flush DB", "Flush DB", AllIcons.Actions.GC);
    }
}
