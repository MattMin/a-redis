package com.mzyupc.aredis.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.language.RedisCmdLanguage;
import com.mzyupc.aredis.view.textfield.ConsoleCommandTextField;
import com.mzyupc.aredis.vo.ConnectionInfo;

import javax.swing.*;
import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/9/16 8:18 下午
 */
public class ConsolePanel extends JPanel {

    private final Project project;
    private final ConnectionInfo connectionInfo;
    private final RedisPoolManager redisPoolManager;
    private JBSplitter container;

    public ConsolePanel(Project project, ConnectionInfo connectionInfo, RedisPoolManager redisPoolManager) {
        this.project = project;
        this.connectionInfo = connectionInfo;
        this.redisPoolManager = redisPoolManager;
        this.setLayout(new BorderLayout());
        init();
    }

    private void init() {
        JPanel cmdPanel = new JPanel(new BorderLayout());
        // todo redis cmd language
        ConsoleCommandTextField cmdTextField = new ConsoleCommandTextField(RedisCmdLanguage.INSTANCE, project);
        cmdPanel.add(cmdTextField, BorderLayout.CENTER);

        JBTextField textField = new JBTextField();
        textField.setEditable(false);
        JBScrollPane executeResultScrollPane = new JBScrollPane(textField);
        JPanel executeResultPanel = new JPanel(new BorderLayout());
        executeResultPanel.add(executeResultScrollPane, BorderLayout.CENTER);

        container = new JBSplitter(true,  0.8F);
        container.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        container.setFirstComponent(cmdPanel);
        container.setSecondComponent(executeResultPanel);

        this.add(container, BorderLayout.CENTER);
    }
}
