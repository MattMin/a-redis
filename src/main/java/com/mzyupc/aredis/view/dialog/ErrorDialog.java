package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;

/**
 * @author mzyupc@163.com
 * @date 2021/8/21 6:14 下午
 */
public class ErrorDialog {
    public static void show(String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showMessageDialog(message, "Error", Messages.getErrorIcon());
        });
    }
}
