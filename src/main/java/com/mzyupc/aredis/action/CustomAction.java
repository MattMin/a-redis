package com.mzyupc.aredis.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * @author mzyupc@163.com
 */
public abstract class CustomAction extends AnAction {
    protected Consumer<AnActionEvent> action;

    public CustomAction(String text, String description, Icon icon) {
        super(text, description, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        if (this.action == null) {
            throw new RuntimeException("action not set");
        }
        action.accept(anActionEvent);
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }

    public void setAction(Consumer<AnActionEvent> action) {
        this.action = action;
    }
}
