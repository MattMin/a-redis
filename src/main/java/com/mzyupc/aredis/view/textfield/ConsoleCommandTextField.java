package com.mzyupc.aredis.view.textfield;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorSettingsProvider;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/9/13 11:03 上午
 */
public class ConsoleCommandTextField extends LanguageTextField {

    public ConsoleCommandTextField(Language language, Project project) {
        super(language, project, "", false);
        this.setAutoscrolls(true);
        this.setOneLineMode(false);
        this.setAutoscrolls(true);
        this.setMinimumSize(new Dimension(100, 100));
        this.addSettingsProvider(new EditorSettingsProvider() {
            @Override
            public void customizeSettings(EditorEx editorEx) {
                EditorSettings settings = editorEx.getSettings();
                settings.setUseSoftWraps(true);
                // 行号
                settings.setLineNumbersShown(true);
                // 折叠
                settings.setFoldingOutlineShown(true);
                settings.setWhitespacesShown(true);
                settings.setLeadingWhitespaceShown(true);
                settings.setRefrainFromScrolling(false);
                settings.setAnimatedScrolling(true);
            }
        });
    }

    @Override
    protected @NotNull EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.setVerticalScrollbarVisible(true);
        return editor;
    }

}
