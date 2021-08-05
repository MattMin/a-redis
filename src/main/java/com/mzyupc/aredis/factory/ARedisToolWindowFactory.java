package com.mzyupc.aredis.factory;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.mzyupc.aredis.MyToolWindow;
import com.mzyupc.aredis.window.ARedisToolWindow;
import org.jetbrains.annotations.NotNull;

/**
 * @author mzyupc@163.com
 * @date 2021/8/4 2:10 下午
 */
public class ARedisToolWindowFactory implements ToolWindowFactory {
    @Override
    public boolean isApplicable(@NotNull Project project) {
        return ToolWindowFactory.super.isApplicable(project);
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ARedisToolWindow aRedisToolWindow = new ARedisToolWindow(project, toolWindow);
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(aRedisToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public void init(@NotNull ToolWindow toolWindow) {
        ToolWindowFactory.super.init(toolWindow);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return ToolWindowFactory.super.shouldBeAvailable(project);
    }

    @Override
    public boolean isDoNotActivateOnStart() {
        return ToolWindowFactory.super.isDoNotActivateOnStart();
    }
}
