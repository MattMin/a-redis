package com.mzyupc.aredis.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.JBColor;
import com.mzyupc.aredis.dialog.NewConnectionSettingsDialog;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
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
    }

    public JPanel getContent(){
        return aRedisWindowContent;
    }

    /**
     * 初始化工具栏
     */
    private void initARedisToolBar() {

//        JLabel addLabel = new JLabel(AllIcons.General.Add);
//        addLabel.setToolTipText("Connect to Redis Server");

        JButton addButtun = new JButton(AllIcons.General.Add);
        addButtun.setContentAreaFilled(false);
        addButtun.setBorderPainted(false);
//        addButtun.setPreferredSize(new Dimension(30, 30));
        addButtun.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // todo 弹出连接配置窗口
                NewConnectionSettingsDialog newConnectionSettingsDialog = new NewConnectionSettingsDialog(project);
                newConnectionSettingsDialog.show();
            }
        });

//        aRedisToolBar.add(addLabel);
        aRedisToolBar.add(addButtun);
        aRedisToolBar.setFloatable(false);
    }

    /**
     * 弹出新建连接对话框
     */
    private void popupNewConnectionSettings() {
        DialogWrapper dialogWrapper = new DialogWrapper(project) {
            @Override
            protected void init() {
                super.init();
            }

            @Override
            protected @Nullable JComponent createCenterPanel() {
                JPanel connectionSettingsPanel = new JPanel();

                JTextField urlField = new JTextField();
                JTextField passwordField = new JPasswordField();

                connectionSettingsPanel.add(urlField);
                connectionSettingsPanel.add(passwordField);
                return connectionSettingsPanel;
            }
        };

        dialogWrapper.setTitle("New Connection Settings");
        dialogWrapper.show();
    }
}
