package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.NumberDocument;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.view.ConnectionManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.File;

/**
 * @author mzyupc@163.com
 */
public class ConnectionSettingsDialog extends DialogWrapper implements Disposable {

    private static final int LABEL_WIDTH = 150;
    private static final int RIGHT_SPACER_WIDTH = 170;
    private static final int PORT_SEPARATOR_WIDTH = 6;
    private static final int PORT_FIELD_WIDTH = 52;
    private static final int TEST_RESULT_HEIGHT = 96;
    private static final Icon PASSWORD_VISIBLE_ICON = IconLoader.getIcon("/icons/password-visible.svg", ConnectionSettingsDialog.class);
    private static final Icon PASSWORD_HIDDEN_ICON = IconLoader.getIcon("/icons/password-hidden.svg", ConnectionSettingsDialog.class);

    JTextField nameTextField;
    JTextField hostField;
    JTextField portField;
    JPasswordField passwordField;
    JCheckBox globalCheckBox;
    JTextField userNameTextField;
    JCheckBox sshTunnelCheckBox;
    JTextField tunnelHostField;
    JTextField tunnelPortField;
    JTextField tunnelUserField;
    JCheckBox tunnelVerifyHostKeyCheckBox;
    JPasswordField tunnelPasswordField;
    TextFieldWithBrowseButton tunnelPrivateKeyField;
    JPasswordField tunnelPassphraseField;
    JRadioButton tunnelPasswordAuthRadio;
    JRadioButton tunnelPrivateKeyAuthRadio;
    JPanel tunnelPasswordRowPanel;
    JPanel tunnelPrivateKeyRowPanel;
    JPanel tunnelPassphraseRowPanel;
    JCheckBox sslTlsCheckBox;
    JCheckBox clusterModeCheckBox;
    JCheckBox sslTrustAllCertificatesCheckBox;
    JCheckBox sslVerifyHostnameCheckBox;
    TextFieldWithBrowseButton sslTruststoreField;
    JPasswordField sslTruststorePasswordField;
    TextFieldWithBrowseButton sslKeystoreField;
    JPasswordField sslKeystorePasswordField;
    JPanel sshTunnelConfigPanel;
    JPanel sslConfigPanel;
    JPanel centerPanel;
    JTextPane testResultTextPane;
    LoadingDecorator testResultLoadingDecorator;
    private final PropertyUtil propertyUtil;
    private final ConnectionInfo connection;
    private final Tree connectionTree;
    private final ConnectionManager connectionManager;
    private final Disposable loadingDecoratorDisposable;

    private final Project project;
    private volatile boolean disposed;

    /**
     * if connectionId is blank ? New Connection : Edit Connection
     *
     * @param project project context
     * @param connection current connection when editing, null when creating
     * @param connectionTree connection tree in the tool window
     */
    public ConnectionSettingsDialog(Project project, ConnectionInfo connection, Tree connectionTree, ConnectionManager connectionManager) {
        super(project);
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.connection = connection;
        this.connectionTree = connectionTree;
        this.connectionManager = connectionManager;
        this.loadingDecoratorDisposable = Disposer.newDisposable("ConnectionSettingsDialog.loadingDecorator");
        this.setTitle("Connection Settings");
        this.setSize(720, 460);
        this.myOKAction = new CustomOKAction();
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    /**
     * 新建连接的对话框
     *
     * @return center panel
     */
    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        boolean newConnection = connection == null;
        boolean tunnelPrivateKeyAuth = !newConnection && StringUtils.isNotBlank(connection.getTunnelPrivateKeyPath());

        nameTextField = new JTextField(newConnection ? null : connection.getName());
        nameTextField.setToolTipText("Connection Name");

        hostField = new JTextField(newConnection ? null : connection.getUrl());
        hostField.setToolTipText("Host");
        portField = new JTextField();
        portField.setToolTipText("Port");
        portField.setDocument(new NumberDocument());
        portField.setText(newConnection ? null : connection.getPort());
        setupPortField(portField);

        passwordField = new JPasswordField(newConnection ? null : connection.getPassword());
        passwordField.setToolTipText("Redis-server authentication password (Optional)");
        JComponent passwordFieldComponent = createPasswordFieldComponent(passwordField);

        globalCheckBox = new JCheckBox("As Global");
        globalCheckBox.setSelected(!newConnection && Boolean.TRUE.equals(connection.getGlobal()));
        globalCheckBox.setBorder(JBUI.Borders.emptyRight(10));
        globalCheckBox.setPreferredSize(new Dimension(RIGHT_SPACER_WIDTH, 24));

        userNameTextField = new JTextField(newConnection ? null : connection.getUser());
        userNameTextField.setToolTipText("Redis-server authentication username (Optional, Redis > 6.0)");

        sshTunnelCheckBox = new JCheckBox("Enable SSH Tunnel");
        sshTunnelCheckBox.setSelected(!newConnection && Boolean.TRUE.equals(connection.getSshTunnel()));

        tunnelHostField = new JTextField(newConnection ? null : connection.getTunnelHost());
        tunnelHostField.setToolTipText("SSH tunnel host");

        tunnelPortField = new JTextField();
        tunnelPortField.setToolTipText("SSH tunnel port");
        tunnelPortField.setDocument(new NumberDocument());
        tunnelPortField.setText(newConnection ? "22" : StringUtils.defaultIfBlank(connection.getTunnelPort(), "22"));
        setupPortField(tunnelPortField);

        tunnelUserField = new JTextField(newConnection ? null : connection.getTunnelUser());
        tunnelUserField.setToolTipText("SSH tunnel username");

        tunnelVerifyHostKeyCheckBox = new JCheckBox("Verify SSH host key");
        tunnelVerifyHostKeyCheckBox.setSelected(newConnection || Boolean.TRUE.equals(connection.getTunnelVerifyHostKey()));
        tunnelVerifyHostKeyCheckBox.setToolTipText("Verify the SSH server host key using ~/.ssh/known_hosts");
        tunnelVerifyHostKeyCheckBox.setBorder(JBUI.Borders.emptyRight(10));
        tunnelVerifyHostKeyCheckBox.setPreferredSize(new Dimension(RIGHT_SPACER_WIDTH, getStandardInputFieldHeight()));

        tunnelPasswordField = new JPasswordField(newConnection ? null : connection.getTunnelPassword());
        tunnelPasswordField.setToolTipText("SSH tunnel password");
        JComponent tunnelPasswordFieldComponent = createPasswordFieldComponent(tunnelPasswordField);

        tunnelPrivateKeyField = createBrowseField(
                newConnection ? null : connection.getTunnelPrivateKeyPath(),
                "Select a Private Key",
                "Select an SSH private key file",
                getPrivateKeyFileChooserDescriptor());

        tunnelPassphraseField = new JPasswordField(newConnection ? null : connection.getTunnelPassphrase());
        tunnelPassphraseField.setToolTipText("SSH private key passphrase (Optional)");
        JComponent tunnelPassphraseFieldComponent = createPasswordFieldComponent(tunnelPassphraseField);

        tunnelPasswordAuthRadio = new JRadioButton("Password");
        tunnelPrivateKeyAuthRadio = new JRadioButton("Private Key");
        ButtonGroup tunnelAuthGroup = new ButtonGroup();
        tunnelAuthGroup.add(tunnelPasswordAuthRadio);
        tunnelAuthGroup.add(tunnelPrivateKeyAuthRadio);
        if (tunnelPrivateKeyAuth) {
            tunnelPrivateKeyAuthRadio.setSelected(true);
        } else {
            tunnelPasswordAuthRadio.setSelected(true);
        }

        sslTlsCheckBox = new JCheckBox("Enable SSL/TLS");
        sslTlsCheckBox.setSelected(!newConnection && Boolean.TRUE.equals(connection.getSslTls()));

        clusterModeCheckBox = new JCheckBox("Enable Cluster Mode");
        clusterModeCheckBox.setSelected(!newConnection && Boolean.TRUE.equals(connection.getClusterMode()));

        sslTrustAllCertificatesCheckBox = new JCheckBox("Trust all certificates");
        sslTrustAllCertificatesCheckBox.setSelected(!newConnection && Boolean.TRUE.equals(connection.getSslTrustAllCertificates()));

        sslVerifyHostnameCheckBox = new JCheckBox("Verify hostname");
        sslVerifyHostnameCheckBox.setSelected(newConnection
                || connection.getSslVerifyHostname() == null
                || Boolean.TRUE.equals(connection.getSslVerifyHostname()));

        sslTruststoreField = createBrowseField(
                newConnection ? null : connection.getSslTruststorePath(),
                "Select CA File / Truststore",
                "Select a CA file or truststore",
                getFileChooserDescriptor("jks", "p12", "pfx", "crt", "cer", "pem"));

        sslTruststorePasswordField = new JPasswordField(newConnection ? null : connection.getSslTruststorePassword());
        sslTruststorePasswordField.setToolTipText("Truststore password (Optional for PEM/CRT)");
        JComponent sslTruststorePasswordFieldComponent = createPasswordFieldComponent(sslTruststorePasswordField);

        sslKeystoreField = createBrowseField(
                newConnection ? null : connection.getSslKeystorePath(),
                "Select Client Keystore",
                "Select a client certificate keystore file (.jks, .p12, .pfx)",
                getFileChooserDescriptor("jks", "p12", "pfx"));

        sslKeystorePasswordField = new JPasswordField(newConnection ? null : connection.getSslKeystorePassword());
        sslKeystorePasswordField.setToolTipText("Client key password (Optional)");
        JComponent sslKeystorePasswordFieldComponent = createPasswordFieldComponent(sslKeystorePasswordField);

        testResultTextPane = new JTextPane();
        testResultTextPane.setMargin(JBUI.emptyInsets());
        testResultTextPane.setOpaque(false);
        testResultTextPane.setEditable(false);
        testResultTextPane.setFocusable(false);
        testResultTextPane.setAlignmentX(SwingConstants.LEFT);
        testResultTextPane.setVisible(true);
        testResultTextPane.setText(" ");
        testResultLoadingDecorator = new LoadingDecorator(testResultTextPane, loadingDecoratorDisposable, 0);

        JPanel generalConfigPanel = new JPanel();
        generalConfigPanel.setLayout(new BoxLayout(generalConfigPanel, BoxLayout.Y_AXIS));
        generalConfigPanel.add(createFieldRow("Connection Name:", nameTextField, globalCheckBox));
        generalConfigPanel.add(createHostPortRow());
        generalConfigPanel.add(createFieldRow("Password:", passwordFieldComponent, createRightSpacer()));
        generalConfigPanel.add(createFieldRow("Username:", userNameTextField, createRightSpacer()));

        sshTunnelConfigPanel = new JPanel();
        sshTunnelConfigPanel.setLayout(new BoxLayout(sshTunnelConfigPanel, BoxLayout.Y_AXIS));
        sshTunnelConfigPanel.add(createHostPortRow("SSH Host:", tunnelHostField, tunnelPortField));
        sshTunnelConfigPanel.add(createFieldRow("SSH Username:", tunnelUserField, tunnelVerifyHostKeyCheckBox));
        sshTunnelConfigPanel.add(createTunnelAuthTypeRow());
        tunnelPasswordRowPanel = createFieldRow("SSH Password:", tunnelPasswordFieldComponent, createRightSpacer());
        tunnelPrivateKeyRowPanel = createFieldRow("Private Key File:", tunnelPrivateKeyField, createRightSpacer());
        tunnelPassphraseRowPanel = createFieldRow("Private Key Password:", tunnelPassphraseFieldComponent, createRightSpacer());
        sshTunnelConfigPanel.add(tunnelPasswordRowPanel);
        sshTunnelConfigPanel.add(tunnelPrivateKeyRowPanel);
        sshTunnelConfigPanel.add(tunnelPassphraseRowPanel);

        sslConfigPanel = new JPanel();
        sslConfigPanel.setLayout(new BoxLayout(sslConfigPanel, BoxLayout.Y_AXIS));
        sslConfigPanel.add(createOptionRow(sslTrustAllCertificatesCheckBox, sslVerifyHostnameCheckBox));
        sslConfigPanel.add(createFieldRow("CA File:", sslTruststoreField, createRightSpacer()));
        sslConfigPanel.add(createFieldRow("CA Password:", sslTruststorePasswordFieldComponent, createRightSpacer()));
        sslConfigPanel.add(createFieldRow("Client Cert:", sslKeystoreField, createRightSpacer()));
        sslConfigPanel.add(createFieldRow("Key Password:", sslKeystorePasswordFieldComponent, createRightSpacer()));

        JPanel clusterConfigPanel = new JPanel();
        clusterConfigPanel.setLayout(new BoxLayout(clusterConfigPanel, BoxLayout.Y_AXIS));
        clusterConfigPanel.add(createDescriptionRow("Connect current host/port as a Redis Cluster seed node."));
        clusterConfigPanel.add(createDescriptionRow("Cluster mode uses only DB0 and automatically discovers cluster nodes."));
        clusterConfigPanel.add(createDescriptionRow("SSH Tunnel is not supported together with Cluster Mode."));

        updateSectionVisibility(sshTunnelConfigPanel, sshTunnelCheckBox.isSelected());
        updateSectionVisibility(sslConfigPanel, sslTlsCheckBox.isSelected());
        updateTunnelAuthModeVisibility();
        sshTunnelCheckBox.addItemListener(e -> updateSectionVisibility(sshTunnelConfigPanel, e.getStateChange() == ItemEvent.SELECTED));
        sslTlsCheckBox.addItemListener(e -> updateSectionVisibility(sslConfigPanel, e.getStateChange() == ItemEvent.SELECTED));
        tunnelPasswordAuthRadio.addItemListener(e -> updateTunnelAuthModeVisibility());
        tunnelPrivateKeyAuthRadio.addItemListener(e -> updateTunnelAuthModeVisibility());

        JBTabbedPane securityTabs = new JBTabbedPane();
        securityTabs.addTab("General", createStaticTabPanel(generalConfigPanel));
        securityTabs.addTab("Cluster", createTabPanel(clusterModeCheckBox, clusterConfigPanel));
        securityTabs.addTab("SSH Tunnel", createTabPanel(sshTunnelCheckBox, sshTunnelConfigPanel));
        securityTabs.addTab("SSL/TLS", createTabPanel(sslTlsCheckBox, sslConfigPanel));
        securityTabs.setPreferredSize(new Dimension(0, 320));

        centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.add(securityTabs, BorderLayout.CENTER);
        centerPanel.add(createTestResultPanel(), BorderLayout.SOUTH);
        return centerPanel;
    }

    @Override
    protected Action @NotNull [] createLeftSideActions() {
        return new Action[]{new TestConnectionAction()};
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getCancelAction(), getOKAction()};
    }

    private JPanel createHostPortRow() {
        return createHostPortRow("Host:", hostField, portField);
    }


    private JPanel createHostPortRow(String labelText, JComponent field, JComponent sideField) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBorder(JBUI.Borders.emptyBottom(6));
        applyInputFieldHeight(field);

        JPanel sidePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sidePanel.setOpaque(false);
        sidePanel.setPreferredSize(new Dimension(RIGHT_SPACER_WIDTH, getStandardInputFieldHeight()));
        sidePanel.add(createPortSeparatorLabel());
        applyInputFieldHeight(sideField);
        sidePanel.add(sideField);

        rowPanel.add(createLabel(labelText), BorderLayout.WEST);
        rowPanel.add(field, BorderLayout.CENTER);
        rowPanel.add(sidePanel, BorderLayout.EAST);
        return rowPanel;
    }

    private JPanel createFieldRow(String labelText, JComponent field, JComponent extraComponent) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBorder(JBUI.Borders.emptyBottom(6));
        applyInputFieldHeight(field);
        rowPanel.add(createLabel(labelText), BorderLayout.WEST);
        rowPanel.add(field, BorderLayout.CENTER);
        rowPanel.add(extraComponent, BorderLayout.EAST);
        return rowPanel;
    }

    private JPanel createAlignedCheckBoxRow(JCheckBox checkBox) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBorder(JBUI.Borders.emptyBottom(6));
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftPanel.setOpaque(false);
        leftPanel.add(checkBox);
        rowPanel.add(leftPanel, BorderLayout.WEST);
        return rowPanel;
    }

    private JPanel createDescriptionRow(String text) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setOpaque(false);
        rowPanel.setBorder(JBUI.Borders.empty(0, 10, 8, 10));
        JLabel label = new JLabel(text);
        label.setForeground(JBColor.GRAY);
        rowPanel.add(label, BorderLayout.WEST);
        return rowPanel;
    }

    private JPanel createTunnelAuthTypeRow() {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBorder(JBUI.Borders.emptyBottom(6));
        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionPanel.add(tunnelPasswordAuthRadio);
        optionPanel.add(Box.createHorizontalStrut(20));
        optionPanel.add(tunnelPrivateKeyAuthRadio);
        rowPanel.add(createLabel("Auth Type:"), BorderLayout.WEST);
        rowPanel.add(optionPanel, BorderLayout.CENTER);
        rowPanel.add(createRightSpacer(), BorderLayout.EAST);
        return rowPanel;
    }

    private JPanel createOptionRow(JCheckBox leftOption, JCheckBox rightOption) {
        JPanel rowPanel = new JPanel(new BorderLayout());
        rowPanel.setBorder(JBUI.Borders.emptyBottom(6));
        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionPanel.add(leftOption);
        optionPanel.add(Box.createHorizontalStrut(20));
        optionPanel.add(rightOption);
        rowPanel.add(createLabel(""), BorderLayout.WEST);
        rowPanel.add(optionPanel, BorderLayout.CENTER);
        rowPanel.add(createRightSpacer(), BorderLayout.EAST);
        return rowPanel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setPreferredSize(new Dimension(LABEL_WIDTH, 12));
        label.setBorder(JBUI.Borders.emptyLeft(10));
        return label;
    }

    private JLabel createPortSeparatorLabel() {
        JLabel label = new JLabel(":");
        label.setPreferredSize(new Dimension(PORT_SEPARATOR_WIDTH, getStandardInputFieldHeight()));
        return label;
    }

    private JComponent createRightSpacer() {
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(RIGHT_SPACER_WIDTH, getStandardInputFieldHeight()));
        return spacer;
    }

    private void setupPortField(JTextField textField) {
        Dimension size = new Dimension(PORT_FIELD_WIDTH, getStandardInputFieldHeight());
        textField.setColumns(6);
        textField.setPreferredSize(size);
        textField.setMinimumSize(size);
        textField.setMaximumSize(size);
    }

    private JComponent createPasswordFieldComponent(JPasswordField passwordField) {
        char defaultEchoChar = passwordField.getEchoChar() == 0 ? '*' : passwordField.getEchoChar();
        Insets standardTextFieldMargin = hostField != null
                ? hostField.getMargin()
                : new JTextField().getMargin();
        passwordField.setEchoChar(defaultEchoChar);
        applyInputFieldHeight(passwordField);

        JToggleButton toggleButton = new JToggleButton(PASSWORD_VISIBLE_ICON);
        toggleButton.setToolTipText("Show Password");
        toggleButton.setFocusable(false);
        toggleButton.setOpaque(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setFocusPainted(false);
        toggleButton.setRolloverEnabled(true);
        toggleButton.setBorder(JBUI.Borders.empty(0, 4));
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.setPreferredSize(new Dimension(28, 20));
        toggleButton.addItemListener(e -> {
            boolean showPassword = e.getStateChange() == ItemEvent.SELECTED;
            passwordField.setEchoChar(showPassword ? (char) 0 : defaultEchoChar);
            toggleButton.setIcon(showPassword ? PASSWORD_HIDDEN_ICON : PASSWORD_VISIBLE_ICON);
            toggleButton.setToolTipText(showPassword ? "Hide Password" : "Show Password");
        });

        if (standardTextFieldMargin != null) {
            passwordField.setMargin(JBUI.insets(standardTextFieldMargin.top,
                    standardTextFieldMargin.left,
                    standardTextFieldMargin.bottom,
                    standardTextFieldMargin.right + toggleButton.getPreferredSize().width));
        }

        JPanel wrapperPanel = new JPanel(null) {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }

            @Override
            public void doLayout() {
                int width = getWidth();
                int height = getHeight();
                passwordField.setBounds(0, 0, width, height);

                Dimension buttonSize = toggleButton.getPreferredSize();
                Insets insets = passwordField.getInsets();
                int rightInset = insets == null ? 0 : insets.right;
                int x = Math.max(0, width - buttonSize.width - Math.max(4, rightInset - buttonSize.width));
                int y = Math.max(0, (height - buttonSize.height) / 2);
                toggleButton.setBounds(x, y, buttonSize.width, buttonSize.height);
            }
        };
        wrapperPanel.setOpaque(false);
        wrapperPanel.add(passwordField);
        wrapperPanel.add(toggleButton);
        wrapperPanel.setComponentZOrder(toggleButton, 0);
        wrapperPanel.setComponentZOrder(passwordField, 1);
        wrapperPanel.setPreferredSize(new Dimension(passwordField.getPreferredSize().width, getStandardInputFieldHeight()));
        return wrapperPanel;
    }

    private void applyInputFieldHeight(JComponent component) {
        Dimension preferredSize = component.getPreferredSize();
        component.setPreferredSize(new Dimension(preferredSize.width, getStandardInputFieldHeight()));
    }

    private int getStandardInputFieldHeight() {
        if (hostField != null) {
            return hostField.getPreferredSize().height;
        }
        return new JTextField().getPreferredSize().height;
    }

    private JPanel createTabPanel(JCheckBox enableCheckBox, JComponent configPanel) {
        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setBorder(JBUI.Borders.empty(10));

        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.add(createAlignedCheckBoxRow(enableCheckBox));
        northPanel.add(configPanel);

        tabPanel.add(northPanel, BorderLayout.NORTH);
        return tabPanel;
    }

    private JPanel createStaticTabPanel(JComponent contentPanel) {
        JPanel tabPanel = new JPanel(new BorderLayout());
        tabPanel.setBorder(JBUI.Borders.empty(10));
        tabPanel.add(contentPanel, BorderLayout.NORTH);
        return tabPanel;
    }

    private JPanel createTestResultPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.emptyBottom(4));
        Dimension resultAreaSize = new Dimension(0, TEST_RESULT_HEIGHT);
        testResultTextPane.setPreferredSize(resultAreaSize);
        testResultTextPane.setMinimumSize(resultAreaSize);
        JComponent loadingComponent = testResultLoadingDecorator.getComponent();
        loadingComponent.setPreferredSize(resultAreaSize);
        loadingComponent.setMinimumSize(resultAreaSize);

        JBScrollPane scrollPane = new JBScrollPane(
                loadingComponent,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.setPreferredSize(resultAreaSize);
        scrollPane.setMinimumSize(resultAreaSize);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void updateSectionVisibility(JComponent component, boolean visible) {
        component.setVisible(visible);
        if (centerPanel != null) {
            centerPanel.revalidate();
            centerPanel.repaint();
        }
    }

    private void updateTunnelAuthModeVisibility() {
        boolean usePrivateKey = tunnelPrivateKeyAuthRadio != null && tunnelPrivateKeyAuthRadio.isSelected();
        updateSectionVisibility(tunnelPasswordRowPanel, !usePrivateKey);
        updateSectionVisibility(tunnelPrivateKeyRowPanel, usePrivateKey);
        updateSectionVisibility(tunnelPassphraseRowPanel, usePrivateKey);
    }

    private TextFieldWithBrowseButton createBrowseField(String initialValue, String title, String description, FileChooserDescriptor descriptor) {
        descriptor.setTitle(title);
        descriptor.setDescription(description);

        TextFieldWithBrowseButton browseField = new TextFieldWithBrowseButton();
        browseField.setText(initialValue);
        browseField.addActionListener(e -> FileChooser.chooseFile(descriptor, project, null, file -> {
            if (file != null) {
                browseField.setText(file.getPath());
            }
        }));
        return browseField;
    }

    private ConnectionInfo buildConnectionInfo(@Nullable String connectionId) {
        boolean usePrivateKey = tunnelPrivateKeyAuthRadio != null && tunnelPrivateKeyAuthRadio.isSelected();
        return ConnectionInfo.builder()
                .id(connectionId)
                .name(getOptionalText(nameTextField))
                .url(getOptionalText(hostField))
                .port(getOptionalText(portField))
                .global(globalCheckBox.isSelected())
                .password(getOptionalPassword(passwordField))
                .user(getOptionalText(userNameTextField))
                .clusterMode(clusterModeCheckBox.isSelected())
                .sshTunnel(sshTunnelCheckBox.isSelected())
                .tunnelHost(getOptionalText(tunnelHostField))
                .tunnelPort(getOptionalText(tunnelPortField))
                .tunnelUser(getOptionalText(tunnelUserField))
                .tunnelVerifyHostKey(tunnelVerifyHostKeyCheckBox.isSelected())
                .tunnelPassword(usePrivateKey ? null : getOptionalPassword(tunnelPasswordField))
                .tunnelPrivateKeyPath(usePrivateKey ? getOptionalText(tunnelPrivateKeyField) : null)
                .tunnelPassphrase(usePrivateKey ? getOptionalPassword(tunnelPassphraseField) : null)
                .sslTls(sslTlsCheckBox.isSelected())
                .sslTrustAllCertificates(sslTrustAllCertificatesCheckBox.isSelected())
                .sslVerifyHostname(sslVerifyHostnameCheckBox.isSelected())
                .sslTruststorePath(getOptionalText(sslTruststoreField))
                .sslTruststorePassword(getOptionalPassword(sslTruststorePasswordField))
                .sslKeystorePath(getOptionalText(sslKeystoreField))
                .sslKeystorePassword(getOptionalPassword(sslKeystorePasswordField))
                .build();
    }

    private void copyConnectionInfo(ConnectionInfo source, ConnectionInfo target) {
        target.setName(source.getName());
        target.setUrl(source.getUrl());
        target.setPort(source.getPort());
        target.setPassword(source.getPassword());
        target.setUser(source.getUser());
        target.setGlobal(source.getGlobal());
        target.setClusterMode(source.getClusterMode());
        target.setSshTunnel(source.getSshTunnel());
        target.setTunnelHost(source.getTunnelHost());
        target.setTunnelPort(source.getTunnelPort());
        target.setTunnelUser(source.getTunnelUser());
        target.setTunnelVerifyHostKey(source.getTunnelVerifyHostKey());
        target.setTunnelPassword(source.getTunnelPassword());
        target.setTunnelPrivateKeyPath(source.getTunnelPrivateKeyPath());
        target.setTunnelPassphrase(source.getTunnelPassphrase());
        target.setSslTls(source.getSslTls());
        target.setSslTrustAllCertificates(source.getSslTrustAllCertificates());
        target.setSslVerifyHostname(source.getSslVerifyHostname());
        target.setSslTruststorePath(source.getSslTruststorePath());
        target.setSslTruststorePassword(source.getSslTruststorePassword());
        target.setSslKeystorePath(source.getSslKeystorePath());
        target.setSslKeystorePassword(source.getSslKeystorePassword());
    }

    private String getOptionalText(JTextField textField) {
        return StringUtils.trimToNull(textField.getText());
    }

    private String getOptionalText(TextFieldWithBrowseButton textField) {
        return StringUtils.trimToNull(textField.getText());
    }

    private String getOptionalPassword(JPasswordField passwordTextField) {
        String password = new String(passwordTextField.getPassword());
        return StringUtils.isEmpty(password) ? null : password;
    }

    private ValidationInfo validateFilePath(String path, String fieldName) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return new ValidationInfo(fieldName + " file does not exist");
        }
        return null;
    }

    private ValidationInfo validateClientKeystorePath(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        if (!RedisPoolManager.isSupportedClientKeystorePath(path)) {
            return new ValidationInfo("Client Certificate must be a JKS or PKCS12 keystore (.jks, .p12, .pfx)");
        }
        return null;
    }

    @Override
    public @Nullable
    JComponent getPreferredFocusedComponent() {
        return nameTextField;
    }

    /**
     * 校验数据
     *
     * @return 通过必须返回null，不通过返回一个 ValidationInfo 信息
     */
    @Nullable
    protected ValidationInfo doValidate(boolean isTest) {
        if (!isTest && StringUtils.isBlank(nameTextField.getText())) {
            return new ValidationInfo("Connection Name can not be empty");
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

        if (clusterModeCheckBox.isSelected() && sshTunnelCheckBox.isSelected()) {
            return new ValidationInfo("Cluster Mode does not support SSH Tunnel currently");
        }

        if (sshTunnelCheckBox.isSelected()) {
            if (StringUtils.isBlank(tunnelHostField.getText())) {
                return new ValidationInfo("SSH Host can not be empty");
            }
            if (StringUtils.isBlank(tunnelPortField.getText())) {
                return new ValidationInfo("SSH Port can not be empty");
            }
            if (!StringUtils.isNumeric(tunnelPortField.getText())) {
                return new ValidationInfo("SSH Port must be in digital form");
            }
            if (StringUtils.isBlank(tunnelUserField.getText())) {
                return new ValidationInfo("SSH Username can not be empty");
            }
            if (tunnelPrivateKeyAuthRadio.isSelected()) {
                if (StringUtils.isBlank(tunnelPrivateKeyField.getText())) {
                    return new ValidationInfo("Private Key file can not be empty");
                }
                ValidationInfo validationInfo = validateFilePath(tunnelPrivateKeyField.getText(), "Private Key");
                if (validationInfo != null) {
                    return validationInfo;
                }
            } else if (tunnelPasswordField.getPassword().length == 0) {
                return new ValidationInfo("SSH Password can not be empty");
            }
        }

        if (sslTlsCheckBox.isSelected()) {
            ValidationInfo validationInfo = validateFilePath(sslTruststoreField.getText(), "CA File");
            if (validationInfo != null) {
                return validationInfo;
            }
            validationInfo = validateFilePath(sslKeystoreField.getText(), "Client Certificate");
            if (validationInfo != null) {
                return validationInfo;
            }
            validationInfo = validateClientKeystorePath(sslKeystoreField.getText());
            if (validationInfo != null) {
                return validationInfo;
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        disposed = true;
        if (testResultLoadingDecorator != null) {
            testResultLoadingDecorator.stopLoading();
        }
        if (!Disposer.isDisposed(loadingDecoratorDisposable)) {
            Disposer.dispose(loadingDecoratorDisposable);
        }
        super.dispose();
    }

    private boolean isDialogDisposed() {
        return disposed || Disposer.isDisposed(loadingDecoratorDisposable);
    }

    /**
     * 自定义 ok Action
     */
    protected class CustomOKAction extends DialogWrapperAction {

        protected CustomOKAction() {
            super("OK");
            putValue(DialogWrapper.DEFAULT_ACTION, true);
        }

        @Override
        protected void doAction(ActionEvent e) {
            ValidationInfo validationInfo = doValidate(false);
            if (validationInfo != null) {
                ErrorDialog.show(validationInfo.message);
                return;
            }

            DefaultTreeModel connectionTreeModel = (DefaultTreeModel) connectionTree.getModel();
            ConnectionInfo currentConnection = buildConnectionInfo(connection == null ? null : connection.getId());
            if (connection == null) {
                propertyUtil.saveConnection(currentConnection);
                connectionManager.addConnectionToList(connectionTreeModel, currentConnection);
                close(CANCEL_EXIT_CODE);
            } else {
                copyConnectionInfo(currentConnection, connection);
                propertyUtil.saveConnection(connection);
                RedisPoolManager oldRedisPoolManager = connectionManager.getConnectionRedisMap().get(connection.getId());
                if (oldRedisPoolManager != null) {
                    oldRedisPoolManager.invalidate();
                }
                RedisPoolManager redisPoolManager = new RedisPoolManager(connection);
                connectionManager.getConnectionRedisMap().put(connection.getId(), redisPoolManager);
                TreePath selectionPath = connectionTree.getSelectionPath();
                if (selectionPath != null && selectionPath.getPathCount() > 1) {
                    DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
                    connectionNode.setUserObject(connection);
                    connectionTreeModel.reload(connectionNode);
                } else {
                    connectionTreeModel.reload();
                }
                close(OK_EXIT_CODE);
            }

            connectionManager.emitConnectionChange();
        }
    }

    protected class TestConnectionAction extends DialogWrapperAction {

        protected TestConnectionAction() {
            super("Test Connection");
        }

        @Override
        protected void doAction(ActionEvent e) {
            ValidationInfo validationInfo = doValidate(true);
            if (validationInfo != null) {
                ErrorDialog.show(validationInfo.message);
                return;
            }

            testResultTextPane.setText("Testing connection...");
            testResultTextPane.setForeground(JBColor.GRAY);
            centerPanel.revalidate();
            centerPanel.repaint();
            testResultLoadingDecorator.startLoading(false);
            ThreadPoolManager.execute(() -> {
                try {
                    RedisPoolManager.TestConnectionResult testConnectionResult =
                            RedisPoolManager.getTestConnectionResult(buildConnectionInfo(connection == null ? null : connection.getId()));
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (isDialogDisposed()) {
                            return;
                        }
                        String message = StringUtils.defaultIfBlank(testConnectionResult.getMsg(), testConnectionResult.isSuccess() ? "Connection succeeded." : "Connection failed.");
                        if (testConnectionResult.isSuccess()) {
                            testResultTextPane.setText(isGenericSuccessMessage(message)
                                    ? "✓ Connection succeeded."
                                    : "✓ Connection succeeded. " + message);
                        } else {
                            testResultTextPane.setText(isGenericFailureMessage(message)
                                    ? "✗ Connection failed."
                                    : "✗ Connection failed. " + message);
                        }
                        testResultTextPane.setForeground(testConnectionResult.isSuccess() ? JBColor.GREEN : JBColor.RED);
                        testResultLoadingDecorator.stopLoading();
                        centerPanel.revalidate();
                        centerPanel.repaint();
                    }, ModalityState.stateForComponent(centerPanel));
                } catch (Throwable throwable) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (isDialogDisposed()) {
                            return;
                        }
                        String message = throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage();
                        testResultTextPane.setText("✗ Connection failed. " + StringUtils.defaultIfBlank(message, "Failed"));
                        testResultTextPane.setForeground(JBColor.RED);
                        testResultLoadingDecorator.stopLoading();
                        centerPanel.revalidate();
                        centerPanel.repaint();
                    }, ModalityState.stateForComponent(centerPanel));
                }
            });
        }
    }

    private FileChooserDescriptor getFileChooserDescriptor(String... extensions) {
        return new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter((file) ->
                        file.isDirectory() || matchesExtension(file.getExtension(), extensions));
    }

    private FileChooserDescriptor getPrivateKeyFileChooserDescriptor() {
        return new FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter(file -> file.isDirectory()
                        || matchesExtension(file.getExtension(), "pem", "key", "ppk")
                        || isOpenSshPrivateKeyFile(file.getName()));
    }

    private boolean matchesExtension(String extension, String... extensions) {
        if (extension == null) {
            return false;
        }
        for (String candidate : extensions) {
            if (Comparing.equal(extension, candidate, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOpenSshPrivateKeyFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return false;
        }
        return StringUtils.startsWith(fileName, "id_");
    }

    private boolean isGenericSuccessMessage(String message) {
        String trimmedMessage = StringUtils.trim(message);
        return "Succeeded".equalsIgnoreCase(trimmedMessage)
                || "Success".equalsIgnoreCase(trimmedMessage)
                || "Connection succeeded.".equalsIgnoreCase(trimmedMessage);
    }

    private boolean isGenericFailureMessage(String message) {
        String trimmedMessage = StringUtils.trim(message);
        return "Failed".equalsIgnoreCase(trimmedMessage)
                || "Fail".equalsIgnoreCase(trimmedMessage)
                || "Connection failed.".equalsIgnoreCase(trimmedMessage);
    }

}
