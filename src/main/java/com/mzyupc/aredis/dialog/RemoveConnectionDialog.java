package com.mzyupc.aredis.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.utils.ConnectionListUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * @author mzyupc@163.com
 * @date 2021/8/7 5:33 下午
 */
public class RemoveConnectionDialog extends DialogWrapper {
    private CustomOKAction okAction;
    private PropertyUtil propertyUtil;
    private Tree connectionTree;
    private Map<String, RedisPoolMgr> connectionRedisMap;

    public RemoveConnectionDialog(@Nullable Project project, Tree connectionTree, Map<String, RedisPoolMgr> connectionRedisMap) {
        super(project);
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connectionRedisMap = connectionRedisMap;
        this.connectionTree = connectionTree;

        this.setTitle("Confirm");
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        return new JLabel("Are you sure you want to delete the connection?");
    }

    /**
     * 覆盖默认的ok/cancel按钮
     *
     * @return
     */
    @NotNull
    @Override
    protected Action[] createActions() {
        DialogWrapperExitAction exitAction = new DialogWrapperExitAction("Cancel", CANCEL_EXIT_CODE);
        okAction = new CustomOKAction();
        // 设置默认的焦点按钮
        okAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
        return new Action[]{exitAction, okAction};
    }

    /**
     * 自定义 ok Action
     */
    protected class CustomOKAction extends DialogWrapperAction {
        protected CustomOKAction() {
            super("OK");
        }

        @Override
        protected void doAction(ActionEvent e) {
            // connection列表中移除
            ConnectionListUtil.removeConnectionFromTree(connectionTree, connectionRedisMap, propertyUtil);

            close(CANCEL_EXIT_CODE);
        }
    }
}

