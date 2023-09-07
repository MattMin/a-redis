package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.function.Consumer;

/**
 * FlushDB 按钮专用提示框
 * @author mzyupc@163.com
 */
public class FlushDbConfirmDialog extends DialogWrapper {

    private final Consumer<ActionEvent> customOkFunction;

    /**
     * @param project project
     * @param customOkFunction 自定义的ok按钮功能
     */
    public FlushDbConfirmDialog(@NotNull Project project, Consumer<ActionEvent> customOkFunction) {
        super(project);
        this.customOkFunction = customOkFunction;
        this.setTitle("Confirm");
        this.setResizable(false);
        this.setAutoAdjustable(true);
        this.myOKAction = new CustomOKAction();
        this.init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel();
        JLabel jLabel = new JLabel("Input \"FLUSH DB\" to flush ");
        jLabel.setBorder(JBUI.Borders.empty(10, 0));
        centerPanel.add(jLabel);

        // 初始化 OK 按钮为禁用
        DialogWrapper that = this;
        that.setOKActionEnabled(false);

        JBTextField confirmWorldsField = getJbTextField(that);
        centerPanel.add(confirmWorldsField);
        return centerPanel;
    }

    @NotNull
    private static JBTextField getJbTextField(DialogWrapper that) {
        JBTextField confirmWorldsField = new JBTextField(7);
        confirmWorldsField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                // do noting
            }

            @Override
            public void keyPressed(KeyEvent e) {
                // do noting
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String confirmWorlds = confirmWorldsField.getText();
                that.setOKActionEnabled("FLUSH DB".equals(confirmWorlds));
            }
        });
        return confirmWorldsField;
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
            if (customOkFunction != null) {
                customOkFunction.accept(e);
            }
            close(OK_EXIT_CODE);
        }
    }
}

