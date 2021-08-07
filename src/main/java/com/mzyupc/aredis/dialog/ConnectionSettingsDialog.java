package com.mzyupc.aredis.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.mzyupc.aredis.utils.ConnectionListUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * @author mzyupc@163.com
 */
public class ConnectionSettingsDialog extends DialogWrapper {

    JTextField nameTextField;
    JTextField hostField;
    JTextField portField;
    JTextField passwordField;
    private PropertyUtil propertyUtil;
    private String connectionId;
    private CustomOKAction okAction;
    private JPanel connectionPanel;
    private Map<String, RedisPoolMgr> connectionRedisMap;

    public ConnectionSettingsDialog(Project project, String connectionId, JPanel connectionPanel, Map<String, RedisPoolMgr> connectionRedisMap) {
        super(project);
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connectionId = connectionId;
        this.connectionPanel = connectionPanel;
        this.connectionRedisMap = connectionRedisMap;
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
    protected @Nullable
    JComponent createCenterPanel() {
        ConnectionInfo connection = propertyUtil.getConnection(connectionId);
        boolean newConnection = connection == null;

        // TODO 参数校验, 输入框下面展示提示
        nameTextField = new JTextField(newConnection ? null : connection.getName());
        nameTextField.setToolTipText("Connection Name");

        // url port 输入框
        hostField = new JTextField(newConnection ? null : connection.getUrl());
        hostField.setToolTipText("Host");

        portField = new JTextField(newConnection ? null : connection.getPort());
        portField.setToolTipText("Port");

        // password输入框
        passwordField = new JPasswordField();

        // 测试连接按钮
        JButton testButton = new JButton("Test Connection");
        testButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // todo 测试连接

            }
        });
        JLabel testResult = new JLabel();

        JPanel connectionSettingsPanel = new JPanel();

        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        //组件填充显示区域
        constraints.fill = GridBagConstraints.BOTH;
        connectionSettingsPanel.setLayout(gridBagLayout);


        JLabel connnectionNameLabel = new JLabel("Connection Name:");

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(connnectionNameLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weightx = 0.85;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(nameTextField, constraints);

        connectionSettingsPanel.add(connnectionNameLabel);
        connectionSettingsPanel.add(nameTextField);


        JLabel hostLabel = new JLabel("Host:");

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(hostLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.55;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(hostField, constraints);

        JLabel portLabel = new JLabel("Port:", SwingConstants.CENTER);

        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(portLabel, constraints);

        constraints.gridx = 3;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(portField, constraints);

        connectionSettingsPanel.add(hostLabel);
        connectionSettingsPanel.add(hostField);
        connectionSettingsPanel.add(portLabel);
        connectionSettingsPanel.add(portField);


        JLabel passwordLabel = new JLabel("Password:");

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(passwordLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weightx = 0.85;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(passwordField, constraints);

        connectionSettingsPanel.add(passwordLabel);
        connectionSettingsPanel.add(passwordField);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(testButton, constraints);

        constraints.gridx = 1;
        constraints.gridy = 3;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weightx = 0.85;
        constraints.weighty = 0.25;
        gridBagLayout.setConstraints(testResult, constraints);

        connectionSettingsPanel.add(testButton);
        connectionSettingsPanel.add(testResult);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(connectionSettingsPanel, BorderLayout.NORTH);
        return centerPanel;
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
     * 校验数据
     *
     * @return 通过必须返回null，不通过返回一个 ValidationInfo 信息
     */
    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (StringUtils.isBlank(nameTextField.getText())) {
            return new ValidationInfo("Name can not be empty");
        }
        if (StringUtils.isBlank(hostField.getText())) {
            return new ValidationInfo("Url can not be empty");
        }
        if (StringUtils.isBlank(portField.getText())) {
            return new ValidationInfo("Port can not be empty");
        }
        return null;
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
                Messages.showMessageDialog(validationInfo.message, "Verification Failed", Messages.getInformationIcon());
            } else {
                // 保存connection
                String password = null;
                if (StringUtils.isNotBlank(passwordField.getText())) {
                    password = passwordField.getText();
                }

                ConnectionInfo connectionInfo = ConnectionInfo.builder()
                        .name(nameTextField.getText())
                        .url(hostField.getText())
                        .port(portField.getText())
                        // TODO 持久化敏感数据
                        .password(password)
                        .build();
                propertyUtil.saveConnection(connectionInfo);

                // connection列表中添加节点
                ConnectionListUtil.addConnectionToList(connectionPanel, connectionInfo, connectionRedisMap);

                close(CANCEL_EXIT_CODE);
            }
        }
    }

}
