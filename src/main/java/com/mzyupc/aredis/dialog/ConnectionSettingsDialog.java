package com.mzyupc.aredis.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.mzyupc.aredis.utils.ConnectionListUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author mzyupc@163.com
 */
public class ConnectionSettingsDialog extends DialogWrapper {

    private PropertyUtil propertyUtil;
    private String connectionId;
    private  CustomOKAction okAction;
    private JPanel connectionPanel;

    JTextField nameTextField;
    JTextField urlField;
    JTextField portField;
    JTextField passwordField;

    public ConnectionSettingsDialog(Project project, String connectionId, JPanel connectionPanel) {
        super(project);
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connectionId = connectionId;
        this.connectionPanel = connectionPanel;
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

        ConnectionInfo connection = propertyUtil.getConnection(connectionId);
        boolean newConnection = connection == null;

        // TODO 参数校验, 输入框下面展示提示
        nameTextField = new JTextField(newConnection ? "" : connection.getName());
        nameTextField.setToolTipText("Connection Name");
        nameTextField.setPreferredSize(new Dimension(300, 30));
        JPanel row0 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row0.add(new JLabel("Connection Name: "));
        row0.add(nameTextField);

        // url port 输入框
        urlField = new JTextField(newConnection ? "" : connection.getUrl());
        urlField.setToolTipText("Url");
        urlField.setPreferredSize(new Dimension(300, 30));
        portField = new JTextField(newConnection ? "" : connection.getPort());
        portField.setPreferredSize(new Dimension(100, 30));
        portField.setToolTipText("Port");
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Url: "));
        row1.add(urlField);
        row1.add(new JLabel(":"));
        row1.add(portField);

        // password输入框
        passwordField = new JPasswordField();
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Password: "));
        row2.add(passwordField);

        // 测试连接按钮
        JButton testButton = new JButton("Test Connection");
        testButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // todo 测试连接

            }
        });
        JLabel testResultLabel = new JLabel();
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(testButton);
        row3.add(testResultLabel);

        connectionSettingsPanel.setLayout(new GridLayout(10, 1));
        connectionSettingsPanel.add(row0);
        connectionSettingsPanel.add(row1);
        connectionSettingsPanel.add(row2);
        connectionSettingsPanel.add(row3);
        return connectionSettingsPanel;
    }

    /**
     * 覆盖默认的ok/cancel按钮
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
            // 点击ok的时候进行数据校验
            ValidationInfo validationInfo = doValidate();
            if (validationInfo != null) {
                Messages.showMessageDialog(validationInfo.message,"Verification Failed", Messages.getInformationIcon());
            } else {
                // 保存connection
                ConnectionInfo connectionInfo = ConnectionInfo.builder()
                        .name(nameTextField.getText())
                        .url(urlField.getText())
                        .port(portField.getText())
                        // TODO 持久化敏感数据
                        .password(passwordField.getText())
                        .build();
                propertyUtil.saveConnection(connectionInfo);

                // connection列表中添加节点
                ConnectionListUtil.addConnectionToList(connectionPanel, connectionInfo);

                close(CANCEL_EXIT_CODE);
            }
        }
    }

    /**
     * 校验数据
     * @return 通过必须返回null，不通过返回一个 ValidationInfo 信息
     */
    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if(StringUtils.isBlank(nameTextField.getText())) {
            return new ValidationInfo("Name can not be empty");
        }
        if(StringUtils.isBlank(urlField.getText())) {
            return new ValidationInfo("Url can not be empty");
        }
        if(StringUtils.isBlank(portField.getText())) {
            return new ValidationInfo("Port can not be empty");
        }
        return null;
    }

}
