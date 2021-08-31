package com.mzyupc.aredis.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.utils.PropertyUtil;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;

/**
 * @author mzyupc@163.com
 */
@Getter
public class ARedisToolWindow implements Disposable {
    /**
     * 激活的连接的个数
     */
    public static boolean isConnected = false;

    private Project project;
    private JPanel aRedisWindowContent;
    private JPanel connectionPanel;
    private PropertyUtil propertyUtil;
    private Tree connectionTree;

    private ConnectionManager connectionManager;

    public ARedisToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        connectionManager.initConnections(connectionTree);
    }

    public JPanel getContent() {
        return aRedisWindowContent;
    }

    @Override
    public void dispose() {
        // 关闭所有连接
        connectionManager.dispose();
    }

    /**
     * 初始化自定义UI组件
     */
    private void createUIComponents() {
        connectionPanel = new JPanel();
        connectionPanel.setLayout(new BorderLayout());

        // 连接树
        connectionManager = ConnectionManager.getInstance(project);
        connectionTree = connectionManager.createConnectionTree(this, connectionPanel);

        // 工具栏
        connectionPanel.add(
                connectionManager.createConnectionActionToolbar(connectionTree, connectionPanel).getComponent(),
                BorderLayout.NORTH
        );
    }
}
