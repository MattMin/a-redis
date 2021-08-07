package com.mzyupc.aredis.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.mzyupc.aredis.dialog.ConnectionSettingsDialog;
import com.mzyupc.aredis.dialog.RemoveConnectionDialog;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Getter;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mzyupc.aredis.utils.ConnectionListUtil.addConnectionToList;

/**
 * @author mzyupc@163.com
 */
@Getter
public class ARedisToolWindow {

    private Project project;
    private JPanel aRedisWindowContent;
    private JToolBar aRedisToolBar;
    private JPanel connectionPanel;
    private PropertyUtil propertyUtil;
    // 连接id对应的redis连接
    private Map<String, RedisPoolMgr> connectionRedisMap;

    public ARedisToolWindow(Project project, ToolWindow toolWindow){
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        connectionRedisMap = new HashMap<>();
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

        // 添加连接的按钮
        JButton addButtun = new JButton(AllIcons.General.Add);
        addButtun.setContentAreaFilled(false);
        addButtun.setBorderPainted(false);
        addButtun.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 弹出连接配置窗口
                ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, null, connectionPanel, connectionRedisMap);
                connectionSettingsDialog.show();
            }
        });

        // 移除连接的按钮
        JButton removeButton = new JButton(AllIcons.General.Remove);
        removeButton.setContentAreaFilled(false);
        removeButton.setBorderPainted(false);
        removeButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 弹出删除确认对话框
                RemoveConnectionDialog removeConnectionDialog = new RemoveConnectionDialog(project, connectionPanel, connectionRedisMap);
                removeConnectionDialog.show();
            }
        });

        aRedisToolBar.add(addButtun);
        aRedisToolBar.add(removeButton);
        aRedisToolBar.setFloatable(false);
    }

    /**
     * 初始化连接
     */
    private void initConnections() {
        List<ConnectionInfo> connections = propertyUtil.getConnections();
//        DefaultListModel<Tree> treeList = new DefaultListModel<>();
        for (ConnectionInfo connection : connections) {
            addConnectionToList(this.connectionPanel, connection, connectionRedisMap);
        }
    }

    private void createUIComponents() {
        connectionPanel = new JPanel();
        // panel内的元素垂直布局
        BoxLayout boxLayout = new BoxLayout(connectionPanel, BoxLayout.Y_AXIS);
        connectionPanel.setLayout(boxLayout);
    }
}
