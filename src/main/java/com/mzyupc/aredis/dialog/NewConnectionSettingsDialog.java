package com.mzyupc.aredis.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 4:33 下午
 */
public class NewConnectionSettingsDialog extends DialogWrapper {

    public NewConnectionSettingsDialog(@Nullable Project project) {
        super(project);
        this.setTitle("New Connection Settings");
        this.setSize(500, 500);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel connectionSettingsPanel = new JPanel();

        JTextField urlField = new JTextField();
        JTextField portField = new JTextField();

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Url: "));
        row1.add(urlField);
        row1.add(new JLabel(":"));
        row1.add(portField);

        JTextField passwordField = new JPasswordField();

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Password: "));
        row2.add(passwordField);

        JButton testButton = new JButton("Test Connection");
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(testButton);

        connectionSettingsPanel.setLayout(new GridLayout(10, 1));
        connectionSettingsPanel.add(row1);
        connectionSettingsPanel.add(row2);
        connectionSettingsPanel.add(row3);

        return connectionSettingsPanel;
    }
}
