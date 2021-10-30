package com.mzyupc.aredis.view.textfield;

import com.intellij.ide.DataManager;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorSettingsProvider;
import com.intellij.ui.EditorTextField;
import com.mzyupc.aredis.enums.ValueFormatEnum;

import javax.swing.*;
import java.awt.*;

/**
 * @author mzyupc@163.com
 * @date 2021/9/9 8:57 下午
 */
public class EditorTextFieldManager {

    public static EditorTextField createEditorTextField(Project project, Language language, String text) {
        EditorTextField valueTextArea = new ValueTextField(language, project, text, false);
        valueTextArea.setAutoscrolls(true);
        valueTextArea.setOneLineMode(false);
        valueTextArea.setMinimumSize(new Dimension(100, 100));
        valueTextArea.addSettingsProvider(new EditorSettingsProvider() {
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
        return valueTextArea;
    }

    /**
     * 更新value展示的数据格式
     *  @param parent
     * @param formatEnum
     */
    public static EditorTextField formatValue(Project project, JComponent parent, ValueFormatEnum formatEnum, EditorTextField oldTextFiled) {
        EditorTextField valueTextArea;
        switch (formatEnum) {
            case HTML:
                valueTextArea = createEditorTextField(project, HTMLLanguage.INSTANCE, oldTextFiled.getText());
                break;
            case XML:
                valueTextArea = createEditorTextField(project, XMLLanguage.INSTANCE, oldTextFiled.getText());
                break;
            case JSON:
                valueTextArea = createEditorTextField(project, JsonLanguage.INSTANCE, oldTextFiled.getText());
                break;
            case PLAIN:
                valueTextArea = createEditorTextField(project, PlainTextLanguage.INSTANCE, oldTextFiled.getText());
                break;
            default:
                return null;
        }

        parent.remove(oldTextFiled);
        parent.add(valueTextArea);

        // 触发 ReformatCode
        ActionManager am = ActionManager.getInstance();
        am.getAction("ReformatCode").actionPerformed(
                new AnActionEvent(
                        null,
                        DataManager.getInstance().getDataContext(valueTextArea),
                        ActionPlaces.UNKNOWN,
                        new Presentation(),
                        ActionManager.getInstance(),
                        0
                )
        );
        return valueTextArea;
    }

}
