package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.NumberDocument;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.ConnectionManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;

/**
 * @author mzyupc@163.com
 */
public class ConnectionSettingsDialog extends DialogWrapper implements Disposable {

    JTextField nameTextField;
    JTextField hostField;
    JTextField portField;
    JPasswordField passwordField;
    JCheckBox globalCheckBox;
    private PropertyUtil propertyUtil;
    private ConnectionInfo connection;
    private CustomOKAction okAction;
    private Tree connectionTree;
    private ConnectionManager connectionManager;

    /**
     * if connectionId is blank ? New Connection : Edit Connection
     * @param project
     * @param connection
     * @param connectionTree
     */
    public ConnectionSettingsDialog(Project project, ConnectionInfo connection, Tree connectionTree, ConnectionManager connectionManager) {
        super(project);
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connection = connection;
        this.connectionTree = connectionTree;
        this.connectionManager = connectionManager;
        this.setTitle("Connection Settings");
        this.setSize(600, 300);
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
        boolean newConnection = connection == null;

        // TODO 参数校验, 输入框下面展示提示
        nameTextField = new JTextField(newConnection ? null : connection.getName());
        nameTextField.setToolTipText("Connection Name");

        // url port 输入框
        hostField = new JTextField(newConnection ? null : connection.getUrl());
        hostField.setToolTipText("Host");
        portField = new JTextField();
        portField.setToolTipText("Port");
        portField.setDocument(new NumberDocument());
        portField.setText(newConnection ? null : connection.getPort());

        // password输入框
        passwordField = new JPasswordField(newConnection ? null : connection.getPassword());

        // 显示密码
        JCheckBox checkBox = new JCheckBox("Show Password");
        checkBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('*');
            }
        });
        checkBox.setBounds(300, 81, 135, 27);

        // 设为全局
        globalCheckBox = new JCheckBox("As global");
        globalCheckBox.setSelected(!newConnection && connection.getGlobal());
        globalCheckBox.setBounds(300, 81, 135, 27);

        // 测试连接按钮
        JButton testButton = new JButton("Test Connection");

        JTextPane testResult = new JTextPane();
        testResult.setMargin(new Insets(0, 10, 0, 0));
        testResult.setOpaque(false);
        testResult.setEditable(false);
        testResult.setFocusable(false);
        testResult.setAlignmentX(SwingConstants.LEFT);


        LoadingDecorator loadingDecorator = new LoadingDecorator(testResult, this, 0);

        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ValidationInfo validationInfo = doValidate(true);
                if (validationInfo != null) {
                    ErrorDialog.show(validationInfo.message);
                } else {
                    String password;
                    if (StringUtils.isNotBlank(new String(passwordField.getPassword()))) {
                        password = new String(passwordField.getPassword());
                    } else {
                        password = null;
                    }

                    loadingDecorator.startLoading(false);
                    ApplicationManager.getApplication().invokeLater(()->{
                        RedisPoolManager.TestConnectionResult testConnectionResult = RedisPoolManager.getTestConnectionResult(hostField.getText(), Integer.parseInt(portField.getText()), password);
                        testResult.setText(testConnectionResult.getMsg());
                        if (testConnectionResult.isSuccess()) {
                            testResult.setForeground(JBColor.GREEN);
                        } else {
                            testResult.setForeground(JBColor.RED);
                        }
                    });
                    loadingDecorator.stopLoading();
                }
            }
        });

        // 使用 GridBagLayout 布局
        GridBagLayout gridBagLayout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        JPanel connectionSettingsPanel = new JPanel();
        connectionSettingsPanel.setLayout(gridBagLayout);

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        JLabel connectionNameLabel = new JLabel("Connection Name:");
        gridBagLayout.setConstraints(connectionNameLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.weightx = 0.85;
        constraints.weighty = 0.33;
        gridBagLayout.setConstraints(nameTextField, constraints);

        connectionSettingsPanel.add(connectionNameLabel);
        connectionSettingsPanel.add(nameTextField);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        JLabel hostLabel = new JLabel("Host:");
        gridBagLayout.setConstraints(hostLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.55;
        constraints.weighty = 0.33;
        gridBagLayout.setConstraints(hostField, constraints);

        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        JLabel portLabel = new JLabel("Port:", SwingConstants.CENTER);
        gridBagLayout.setConstraints(portLabel, constraints);

        constraints.gridx = 3;
        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        gridBagLayout.setConstraints(portField, constraints);

        connectionSettingsPanel.add(hostLabel);
        connectionSettingsPanel.add(hostField);
        connectionSettingsPanel.add(portLabel);
        connectionSettingsPanel.add(portField);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        JLabel passwordLabel = new JLabel("Password:");
        gridBagLayout.setConstraints(passwordLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.gridheight = 1;
        constraints.weightx = 0.7;
        constraints.weighty = 0.33;
        gridBagLayout.setConstraints(passwordField, constraints);

        constraints.gridx = 3;
        constraints.gridy = 2;
        constraints.gridwidth = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.15;
        constraints.weighty = 0.33;
        gridBagLayout.setConstraints(checkBox, constraints);

        connectionSettingsPanel.add(passwordLabel);
        connectionSettingsPanel.add(passwordField);
        connectionSettingsPanel.add(checkBox);
        connectionSettingsPanel.add(globalCheckBox);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(testButton);
        JPanel testConnectionSettingsPanel = new JPanel(new GridLayout(2, 1));
        testConnectionSettingsPanel.add(row);
        testConnectionSettingsPanel.add(loadingDecorator.getComponent());

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(connectionSettingsPanel, BorderLayout.NORTH);
        centerPanel.add(testConnectionSettingsPanel, BorderLayout.SOUTH);
        return centerPanel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return nameTextField;
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
    protected ValidationInfo doValidate(boolean isTest) {
        if (!isTest) {
            if (StringUtils.isBlank(nameTextField.getText())) {
                return new ValidationInfo("Connection Name can not be empty");
            }
        }
        if (StringUtils.isBlank(hostField.getText())) {
            return new ValidationInfo("Host can not be empty");
        }
        String port = portField.getText();
        if (StringUtils.isBlank(port)) {
            return new ValidationInfo("Port can not be empty");
        }
        if (!StringUtils.isNumeric(port)) {
            return new ValidationInfo("Port must be in digital form");
        }
        return null;
    }

    @Override
    public void dispose() {
        super.dispose();
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
            ValidationInfo validationInfo = doValidate(false);
            if (validationInfo != null) {
                ErrorDialog.show(validationInfo.message);
            } else {
                DefaultTreeModel connectionTreeModel = (DefaultTreeModel) connectionTree.getModel();
                if (connection == null) {
                    // 保存connection
                    String password = null;
                    if (StringUtils.isNotBlank(new String(passwordField.getPassword()))) {
                        password = new String(passwordField.getPassword());
                    }
                    // 持久化连接信息
                    ConnectionInfo connectionInfo = ConnectionInfo.builder()
                            .name(nameTextField.getText())
                            .url(hostField.getText())
                            .port(portField.getText())
                            .global(globalCheckBox.isSelected())
                            .password(password)
                            .build();
                    propertyUtil.saveConnection(connectionInfo);
                    // connectionTree 中添加节点
                    connectionManager.addConnectionToList(connectionTreeModel, connectionInfo);
                    close(CANCEL_EXIT_CODE);

                } else {
                    // 更新connection
                    String password = null;
                    if (StringUtils.isNotBlank(new String(passwordField.getPassword()))) {
                        password = new String(passwordField.getPassword());
                    }
                    // 更新持久化信息
                    connection.setName(nameTextField.getText());
                    connection.setUrl(hostField.getText());
                    connection.setPort(portField.getText());
                    connection.setPassword(password);
                    if (connection.getGlobal() != globalCheckBox.isSelected()) {
                        // 更改了配置级别
                        connection.setGlobal(globalCheckBox.isSelected());
                        propertyUtil.saveConnection(connection);
                    }
                    // 更新redisPoolMgr
                    RedisPoolManager redisPoolManager = new RedisPoolManager(connection);
                    connectionManager.getConnectionRedisMap().put(connection.getId(), redisPoolManager);
                    // 设置connectionNode的connectionInfo
                    TreePath selectionPath = connectionTree.getSelectionPath();
                    DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
                    connectionNode.setUserObject(connection);
                    // 重新载入connectionNode
                    connectionTreeModel.reload(connectionNode);

                    close(OK_EXIT_CODE);
                }
            }
        }
    }

}
