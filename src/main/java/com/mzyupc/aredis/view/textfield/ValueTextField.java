package com.mzyupc.aredis.view.textfield;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;

/**
 * @author mzyupc@163.com
 * @date 2021/9/13 11:03 上午
 */
public class ValueTextField extends LanguageTextField {

    public ValueTextField(Language language, Project project, String text, boolean b) {
        super(language, project, text, b);
    }

    @Override
    protected @NotNull EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        // 解决: 回车换行后如果不调整窗口大小, 则滚动条不会出现
        // ValueTextField外层不需要再套一层JBScrollPane
        editor.setVerticalScrollbarVisible(true);
        return editor;
    }

}
