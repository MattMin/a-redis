package com.mzyupc.aredis.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.action.CustomAction;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.textfield.ConsoleCommandTextArea;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.jetbrains.annotations.NotNull;

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
        // init result area
        JBTextArea resultArea = new JBTextArea();
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setMargin(JBUI.insetsLeft(10));
        JBScrollPane executeResultScrollPane = new JBScrollPane(resultArea);
        executeResultScrollPane.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        // init console result toolbar
        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(createClearAction(resultArea));
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actions, false);
        // init result area and result toolbar container
        JPanel executeResultPanel = new JPanel(new BorderLayout());
        executeResultPanel.add(executeResultScrollPane, BorderLayout.CENTER);
        executeResultPanel.add(actionToolbar.getComponent(), BorderLayout.EAST);

        // init console area
        cmdTextArea = new ConsoleCommandTextArea(resultArea, redisPoolManager);
        JPanel cmdPanel = new JPanel(new BorderLayout());
        cmdPanel.add(cmdTextArea);
        JBScrollPane cmdScrollPane = new JBScrollPane(cmdPanel);

        container = new JBSplitter(true,  0.6F);
        container.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        container.setFirstComponent(cmdScrollPane);
        container.setSecondComponent(executeResultPanel);

        this.add(container, BorderLayout.CENTER);
    }

    private AnAction createClearAction(JBTextArea resultArea) {
        return new CustomAction("Clear result", "Clear result", AllIcons.Actions.GC){
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                resultArea.setText("");
            }
        };
    }

    public ConsoleCommandTextArea getCmdTextArea() {
        return this.cmdTextArea;
    }
}
