package com.mzyupc.aredis.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.dialog.ConnectionSettingsDialog;
import com.mzyupc.aredis.persistence.PropertyUtil;
import com.mzyupc.aredis.vo.ConnectionInfo;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 2:08 下午
 */
public class ARedisToolWindow {

    private Project project;
    private JPanel aRedisWindowContent;
    private JToolBar aRedisToolBar;
    private JPanel connectionPanel;
    private PropertyUtil propertyUtil;

    public ARedisToolWindow(Project project, ToolWindow toolWindow){
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        initARedisToolBar();
        initConnections();
    }

    public JPanel getContent(){
        return aRedisWindowContent;
    }

    /**
     * 初始化工具栏
     */
    private void initARedisToolBar() {

        JButton addButtun = new JButton(AllIcons.General.Add);
        addButtun.setContentAreaFilled(false);
        addButtun.setBorderPainted(false);
        addButtun.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // todo 弹出连接配置窗口
                ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, null);
                connectionSettingsDialog.show();
            }
        });

        aRedisToolBar.add(addButtun);
        aRedisToolBar.setFloatable(false);
    }

    private void initConnections() {
        //todo 初始化连接
        List<ConnectionInfo> connections = propertyUtil.getConnections();
        for (ConnectionInfo connection : connections) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(connection, true);
            Tree tree = new Tree(root);
            root.add(new DefaultMutableTreeNode("节点1"));
            root.add(new DefaultMutableTreeNode("节点2"));

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
            row.add(tree);
            connectionPanel.add(row);
        }
    }

    private void createUIComponents() {
        connectionPanel = new JPanel();
        connectionPanel.setLayout(new GridLayout(10, 1));
    }
}
