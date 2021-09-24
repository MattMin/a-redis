package com.mzyupc.aredis.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.textfield.ConsoleCommandTextArea;
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
    private ConsoleCommandTextArea cmdTextArea;

    public ConsolePanel(Project project, ConnectionInfo connectionInfo, RedisPoolManager redisPoolManager) {
        this.project = project;
        this.connectionInfo = connectionInfo;
        this.redisPoolManager = redisPoolManager;
        this.setLayout(new BorderLayout());
        init();
    }

    private void init() {
        JBTextArea resultArea = new JBTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setMargin(JBUI.insetsLeft(10));
        JBScrollPane executeResultScrollPane = new JBScrollPane(resultArea);
        executeResultScrollPane.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel executeResultPanel = new JPanel(new BorderLayout());
        executeResultPanel.add(executeResultScrollPane, BorderLayout.CENTER);

        cmdTextArea = new ConsoleCommandTextArea(resultArea, redisPoolManager);
        JBScrollPane cmdScrollPane = new JBScrollPane(cmdTextArea);
        JPanel cmdPanel = new JPanel(new BorderLayout());
        cmdPanel.add(cmdScrollPane, BorderLayout.CENTER);

        container = new JBSplitter(true,  0.8F);
        container.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        container.setFirstComponent(cmdPanel);
        container.setSecondComponent(executeResultPanel);

        this.add(container, BorderLayout.CENTER);
    }

    public ConsoleCommandTextArea getCmdTextArea() {
        return this.cmdTextArea;
    }
}
