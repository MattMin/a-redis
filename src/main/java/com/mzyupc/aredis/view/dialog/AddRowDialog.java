package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.mzyupc.aredis.enums.RedisValueTypeEnum;
import com.mzyupc.aredis.enums.ValueFormatEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.ext.swing.DoubleDocument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Consumer;

import static com.mzyupc.aredis.view.ValueTextAreaManager.createValueTextArea;
import static com.mzyupc.aredis.view.ValueTextAreaManager.formatValue;

/**
 * @author mzyupc@163.com
 */
@Slf4j
public class AddRowDialog extends DialogWrapper {

    private Consumer<ActionEvent> customOkAction;

    private final RedisValueTypeEnum valueTypeEnum;

    private JTextField scoreOrFieldTextField;

    private EditorTextField valueTextArea;

    private final Project project;

    public AddRowDialog(@Nullable Project project, RedisValueTypeEnum valueTypeEnum) {
        super(project);
        this.project = project;
        this.valueTypeEnum = valueTypeEnum;

        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        JPanel valuePanel = createValuePanel();
        JPanel container = new JPanel(new BorderLayout());
        container.setMinimumSize(new Dimension(500, 250));
        container.add(valuePanel, BorderLayout.CENTER);
        return container;
    }

    @NotNull
    private JPanel createValuePanel() {
        switch (valueTypeEnum) {
            case String:
            case List:
            case Set:
                return createSimpleValuePanel();

            case Zset:
                return createZSetValuePanel();

            case Hash:
                return createHashValuePanel();

            default:
                return new JPanel();
        }
    }

    @NotNull
    private JPanel createZSetValuePanel() {
        JPanel scorePanel = new JPanel(new BorderLayout());
        JBLabel scoreLabel = new JBLabel("Score:");
        scoreLabel.setPreferredSize(new Dimension(50, 25));
        scorePanel.add(scoreLabel, BorderLayout.WEST);
        scoreOrFieldTextField = new JTextField();
        scoreOrFieldTextField.setDocument(new DoubleDocument());
        scorePanel.add(scoreOrFieldTextField, BorderLayout.CENTER);

        JPanel zsetValuePanel = createSimpleValuePanel();

        JPanel zsetTypePanel = new JPanel(new BorderLayout());
        zsetTypePanel.add(scorePanel, BorderLayout.NORTH);
        zsetTypePanel.add(zsetValuePanel, BorderLayout.CENTER);
        return zsetTypePanel;
    }

    @NotNull
    private JPanel createHashValuePanel() {
        JPanel scorePanel = new JPanel(new BorderLayout());
        JBLabel scoreLabel = new JBLabel("Field:");
        scoreLabel.setPreferredSize(new Dimension(50, 25));
        scorePanel.add(scoreLabel, BorderLayout.WEST);
        scoreOrFieldTextField = new JTextField();
        scorePanel.add(scoreOrFieldTextField, BorderLayout.CENTER);

        JPanel hashValuePanel = createSimpleValuePanel();

        JPanel zsetTypePanel = new JPanel(new BorderLayout());
        zsetTypePanel.add(scorePanel, BorderLayout.NORTH);
        zsetTypePanel.add(hashValuePanel, BorderLayout.CENTER);
        return zsetTypePanel;
    }

    /**
     * 创建一个value panel
     * @return
     */
    @NotNull
    private JPanel createSimpleValuePanel() {
        valueTextArea = createValueTextArea(project, PlainTextLanguage.INSTANCE, "");
        JPanel stringTypePanel = new JPanel(new BorderLayout());
        JComboBox<ValueFormatEnum> newKeyValueFormatEnumJComboBox = new JComboBox<>(ValueFormatEnum.values());
        newKeyValueFormatEnumJComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ItemEvent.SELECTED == e.getStateChange()) {
                    ValueFormatEnum formatEnum = (ValueFormatEnum) e.getItem();
                    valueTextArea = formatValue(project, stringTypePanel, formatEnum, valueTextArea);
                }
            }
        });

        JPanel viewAsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewAsPanel.add(new JBLabel("View as:"));
        viewAsPanel.add(newKeyValueFormatEnumJComboBox);

        JPanel valueLabelPanel = new JPanel(new BorderLayout());
        valueLabelPanel.add(new JBLabel("Value:"), BorderLayout.WEST);
        valueLabelPanel.add(viewAsPanel, BorderLayout.EAST);

        stringTypePanel.add(valueLabelPanel, BorderLayout.NORTH);
        stringTypePanel.add(valueTextArea, BorderLayout.CENTER);
        return stringTypePanel;
    }


    @NotNull
    @Override
    protected Action[] createActions() {
        DialogWrapperExitAction exitAction = new DialogWrapperExitAction("Cancel", CANCEL_EXIT_CODE);
        AddRowDialog.CustomOKAction okAction = new AddRowDialog.CustomOKAction();
        // 设置默认的焦点按钮
        okAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
        return new Action[]{exitAction, okAction};
    }


    /**
     * 自定义 ok Action
     */
    protected class CustomOKAction extends DialogWrapperAction {
        protected CustomOKAction() {
            super("OK");
        }

        @Override
        protected void doAction(ActionEvent e) {
            if (customOkAction != null) {
                customOkAction.accept(e);
            }
        }
    }

    public String getValue() {
       return valueTextArea.getText();
    }

    public String getScoreOrField() {
        return scoreOrFieldTextField.getText();
    }

    public void setCustomOkAction(Consumer<ActionEvent> customOkAction) {
        this.customOkAction = customOkAction;
    }

}
