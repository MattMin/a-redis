package com.mzyupc.aredis.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.mzyupc.aredis.dialog.ConnectionSettingsDialog;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 2:08 下午
 */
public class ARedisToolWindow {

    private Project project;
    private JPanel aRedisWindowContent;
    private JTree connection;
    private JToolBar aRedisToolBar;

    public ARedisToolWindow(Project project, ToolWindow toolWindow){
        this.project = project;
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
    }

}
