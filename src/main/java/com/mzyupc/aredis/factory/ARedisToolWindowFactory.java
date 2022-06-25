package com.mzyupc.aredis.factory;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.mzyupc.aredis.view.ARedisToolWindow;
import org.jetbrains.annotations.NotNull;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 2:10 下午
 */
public class ARedisToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ARedisToolWindow aRedisToolWindow = new ARedisToolWindow(project, toolWindow);
        ContentFactory contentFactory = ApplicationManager.getApplication().getService(ContentFactory.class);
        Content content = contentFactory.createContent(aRedisToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
