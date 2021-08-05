package com.mzyupc.aredis.dialog;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.mzyupc.aredis.persistence.PropertyUtil;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 4:33 下午
 */
public class ConnectionSettingsDialog extends DialogWrapper {

    private Project project;
    private PropertiesComponent properties;
    private String connectionId;

    public ConnectionSettingsDialog(@Nullable Project project, String connectionId) {
        super(project);
        this.project = project;
        properties = PropertiesComponent.getInstance(project);
        this.connectionId = connectionId;
        this.setTitle("New Connection Settings");
        this.setSize(500, 500);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    /**
     * 新建连接的对话框
     *
     * @return
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel connectionSettingsPanel = new JPanel();

        PropertyUtil propertyUtil = PropertyUtil.getInstance(project);
        ConnectionInfo connection = propertyUtil.getConnection(connectionId);
        boolean newConnection = connection == null;

        JTextField nameTextField = new JTextField(newConnection ? "" : connection.getName());
        nameTextField.setToolTipText("Connection Name");
        nameTextField.setPreferredSize(new Dimension(300, 30));

        JPanel row0 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row0.add(new JLabel("Connection Name: "));
        row0.add(nameTextField);

        // url port 输入框
        JTextField urlField = new JTextField(newConnection ? "" : connection.getUrl());
        urlField.setToolTipText("Url");
        urlField.setPreferredSize(new Dimension(300, 30));
        JTextField portField = new JTextField(newConnection ? "" : connection.getPort());
        portField.setPreferredSize(new Dimension(100, 30));
        portField.setToolTipText("Port");

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Url: "));
        row1.add(urlField);
        row1.add(new JLabel(":"));
        row1.add(portField);

        // password输入框
        JTextField passwordField = new JPasswordField();

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Password: "));
        row2.add(passwordField);

        // 测试连接按钮
        JButton testButton = new JButton("Test Connection");
        testButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                propertyUtil.saveConnection(ConnectionInfo.builder()
                                .name(nameTextField.getText())
                                .url(urlField.getText())
                                .port(portField.getText())
                        // TODO 持久化敏感数据
                                .password(passwordField.getText())
                                .build()
                );
            }
        });

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(testButton);

        connectionSettingsPanel.setLayout(new GridLayout(10, 1));
        connectionSettingsPanel.add(row0);
        connectionSettingsPanel.add(row1);
        connectionSettingsPanel.add(row2);
        connectionSettingsPanel.add(row3);



        return connectionSettingsPanel;
    }

    private String getPropertyKey(String key) {
        return connectionId + ":" + key;
    }
}
