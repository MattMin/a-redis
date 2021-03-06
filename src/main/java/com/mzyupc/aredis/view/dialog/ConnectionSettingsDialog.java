package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.JBColor;
import com.intellij.ui.NumberDocument;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.view.ConnectionManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang.StringUtils;
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
    private Tree connectionTree;
    private ConnectionManager connectionManager;

    private Project project;

    /**
     * if connectionId is blank ? New Connection : Edit Connection
     *
     * @param project
     * @param connection
     * @param connectionTree
     */
    public ConnectionSettingsDialog(Project project, ConnectionInfo connection, Tree connectionTree, ConnectionManager connectionManager) {
        super(project);
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connection = connection;
        this.connectionTree = connectionTree;
        this.connectionManager = connectionManager;
        this.setTitle("Connection Settings");
        this.setSize(650, 240);
        this.myOKAction = new CustomOKAction();
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    /**
     * ????????????????????????
     *
     * @return
     */
    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        boolean newConnection = connection == null;

        nameTextField = new JTextField(newConnection ? null : connection.getName());
        nameTextField.setToolTipText("Connection Name");

        // url port ?????????
        hostField = new JTextField(newConnection ? null : connection.getUrl());
        hostField.setToolTipText("Host");
        portField = new JTextField();
        portField.setToolTipText("Port");
        portField.setDocument(new NumberDocument());
        portField.setText(newConnection ? null : connection.getPort());

        // password?????????
        passwordField = new JPasswordField(newConnection ? null : connection.getPassword());

        // ????????????
        JCheckBox showPasswordCheckBox = new JCheckBox("Show Password");
        showPasswordCheckBox.setBorder(JBUI.Borders.emptyRight(10));
        showPasswordCheckBox.setPreferredSize(new Dimension(140, 12));
        showPasswordCheckBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                passwordField.setEchoChar((char) 0);
            } else {
                passwordField.setEchoChar('*');
            }
        });

        // ????????????
        globalCheckBox = new JCheckBox("As Global");
        globalCheckBox.setSelected(!newConnection && connection.getGlobal());
        globalCheckBox.setBorder(JBUI.Borders.emptyRight(10));
        globalCheckBox.setPreferredSize(new Dimension(140, 12));

        JTextPane testResult = new JTextPane();
        testResult.setMargin(JBUI.insetsLeft(10));
        testResult.setOpaque(false);
        testResult.setEditable(false);
        testResult.setFocusable(false);
        testResult.setAlignmentX(SwingConstants.LEFT);

        LoadingDecorator loadingDecorator = new LoadingDecorator(testResult, this, 0);
        // ??????????????????
        JButton testButton = new JButton("Test Connection");
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
                    ReadAction.nonBlocking(() -> {
                        try {
                            RedisPoolManager.TestConnectionResult testConnectionResult =
                                    RedisPoolManager.getTestConnectionResult(hostField.getText(),
                                            Integer.parseInt(portField.getText()),
                                            password);
                            testResult.setText(testConnectionResult.getMsg());
                            if (testConnectionResult.isSuccess()) {
                                testResult.setForeground(JBColor.GREEN);
                            } else {
                                testResult.setForeground(JBColor.RED);
                            }
                        } finally {
                            loadingDecorator.stopLoading();
                        }
                        return null;
                    }).submit(ThreadPoolManager.getExecutor());

                }
            }
        });

        JPanel connectionSettingsPanel = new JPanel();
        BoxLayout boxLayout = new BoxLayout(connectionSettingsPanel, BoxLayout.Y_AXIS);
        connectionSettingsPanel.setLayout(boxLayout);

        JLabel connectionNameLabel = new JLabel("Connection Name:");
        connectionNameLabel.setPreferredSize(new Dimension(130, 12));
        connectionNameLabel.setBorder(JBUI.Borders.emptyLeft(10));

        JLabel hostLabel = new JLabel("Host:");
        hostLabel.setBorder(JBUI.Borders.emptyLeft(10));
        hostLabel.setPreferredSize(new Dimension(130, 12));

        JLabel portLabel = new JLabel("Port:");
        portLabel.setBorder(JBUI.Borders.emptyLeft(4));
        JPanel portPanel = new JPanel(new BorderLayout());
        portPanel.add(portLabel, BorderLayout.WEST);
        portPanel.add(portField, BorderLayout.CENTER);
        portPanel.setPreferredSize(new Dimension(140, 12));
        portPanel.setBorder(JBUI.Borders.emptyRight(40));

        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setBorder(JBUI.Borders.emptyLeft(10));
        passwordLabel.setPreferredSize(new Dimension(130, 12));

        JPanel connectionNameRowPanel = new JPanel(new BorderLayout());
        connectionNameRowPanel.add(connectionNameLabel, BorderLayout.WEST);
        connectionNameRowPanel.add(nameTextField, BorderLayout.CENTER);
        connectionNameRowPanel.add(globalCheckBox, BorderLayout.EAST);

        JPanel hostRowPanel = new JPanel(new BorderLayout());
        hostRowPanel.add(hostLabel, BorderLayout.WEST);
        hostRowPanel.add(hostField, BorderLayout.CENTER);
        hostRowPanel.add(portPanel, BorderLayout.EAST);

        JPanel passwordRowPanel = new JPanel(new BorderLayout());
        passwordRowPanel.add(passwordLabel, BorderLayout.WEST);
        passwordRowPanel.add(passwordField, BorderLayout.CENTER);
        passwordRowPanel.add(showPasswordCheckBox, BorderLayout.EAST);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(testButton);
        JPanel testConnectionSettingsPanel = new JPanel(new GridLayout(2, 1));
        testConnectionSettingsPanel.add(row);
        testConnectionSettingsPanel.add(loadingDecorator.getComponent());

        // todo ssl/tls
//        JPanel sslPanel = new JPanel(new BorderLayout());
//        JCheckBox sslCheckBox = new JCheckBox("SSL/TLS");
//
//        JPanel sslCheckBoxRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
//        sslCheckBoxRow.add(sslCheckBox);
//
//        TextFieldWithBrowseButton publicKeyField = new TextFieldWithBrowseButton();
//        publicKeyField.setEditable(false);
//        publicKeyField.addBrowseFolderListener(
//                "Select a Public Key",
//                "Select a public key description",
//                project,
//                getFileChooserDescriptor());
//
//        JBLabel publicKeyLabel = new JBLabel("Public Key");
//        JPanel publicKeyRow = new JPanel(new BorderLayout());
//
//        JPanel keySelectPanel = new JPanel(new BorderLayout());
//        keySelectPanel.add(publicKeyField, BorderLayout.NORTH);
//        keySelectPanel.setVisible(false);
//
//        sslPanel.add(sslCheckBoxRow, BorderLayout.NORTH);
//        sslPanel.add(keySelectPanel, BorderLayout.CENTER);
//
//        sslCheckBox.addItemListener(e -> {
//            if (e.getStateChange() == ItemEvent.SELECTED) {
//                keySelectPanel.setVisible(true);
//            } else {
//                keySelectPanel.setVisible(false);
//            }
//        });


        connectionSettingsPanel.add(connectionNameRowPanel);
        connectionSettingsPanel.add(hostRowPanel);
        connectionSettingsPanel.add(passwordRowPanel);
//        connectionSettingsPanel.add(sslPanel);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(connectionSettingsPanel, BorderLayout.NORTH);
        centerPanel.add(testConnectionSettingsPanel, BorderLayout.SOUTH);
        return centerPanel;
    }

    @Override
    public @Nullable
    JComponent getPreferredFocusedComponent() {
        return nameTextField;
    }

    /**
     * ????????????
     *
     * @return ??????????????????null???????????????????????? ValidationInfo ??????
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
     * ????????? ok Action
     */
    protected class CustomOKAction extends DialogWrapperAction {

        protected CustomOKAction() {
            super("OK");
            putValue(DialogWrapper.DEFAULT_ACTION, true);
        }

        @Override
        protected void doAction(ActionEvent e) {
            // ??????ok???????????????????????????
            ValidationInfo validationInfo = doValidate(false);
            if (validationInfo != null) {
                ErrorDialog.show(validationInfo.message);
            } else {
                DefaultTreeModel connectionTreeModel = (DefaultTreeModel) connectionTree.getModel();
                if (connection == null) {
                    // ??????connection
                    String password = null;
                    if (StringUtils.isNotBlank(new String(passwordField.getPassword()))) {
                        password = new String(passwordField.getPassword());
                    }
                    // ?????????????????????
                    ConnectionInfo connectionInfo = ConnectionInfo.builder()
                            .name(nameTextField.getText())
                            .url(hostField.getText())
                            .port(portField.getText())
                            .global(globalCheckBox.isSelected())
                            .password(password)
                            .build();
                    propertyUtil.saveConnection(connectionInfo);
                    // connectionTree ???????????????
                    connectionManager.addConnectionToList(connectionTreeModel, connectionInfo);
                    close(CANCEL_EXIT_CODE);

                } else {
                    // ??????connection
                    String password = null;
                    if (StringUtils.isNotBlank(new String(passwordField.getPassword()))) {
                        password = new String(passwordField.getPassword());
                    }
                    // ?????????????????????
                    connection.setName(nameTextField.getText());
                    connection.setUrl(hostField.getText());
                    connection.setPort(portField.getText());
                    connection.setPassword(password);
                    if (connection.getGlobal() != globalCheckBox.isSelected()) {
                        // ?????????????????????
                        connection.setGlobal(globalCheckBox.isSelected());
                        propertyUtil.saveConnection(connection);
                    }
                    // ??????redisPoolMgr
                    RedisPoolManager redisPoolManager = new RedisPoolManager(connection);
                    connectionManager.getConnectionRedisMap().put(connection.getId(), redisPoolManager);
                    // ??????connectionNode???connectionInfo
                    TreePath selectionPath = connectionTree.getSelectionPath();
                    DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
                    connectionNode.setUserObject(connection);
                    // ????????????connectionNode
                    connectionTreeModel.reload(connectionNode);

                    close(OK_EXIT_CODE);
                }

                connectionManager.emitConnectionChange();
            }
        }
    }

    private FileChooserDescriptor getFileChooserDescriptor() {
        return new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter((file) ->
                        Comparing.equal(file.getExtension(), "crt", false)
                                || Comparing.equal(file.getExtension(), "key", false)
                                || Comparing.equal(file.getExtension(), "pem", false));
    }

}
