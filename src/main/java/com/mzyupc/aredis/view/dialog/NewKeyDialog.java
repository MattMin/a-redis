package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.mzyupc.aredis.view.dialog.enums.NewKeyValueFormatEnum;
import com.mzyupc.aredis.view.dialog.enums.RedisValueTypeEnum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Consumer;

/**
 * @author mzyupc@163.com
 * <p>
 * 新增key的对话框
 */
public class NewKeyDialog extends DialogWrapper {

    private final Consumer<ActionEvent> customOkFunction;

    public NewKeyDialog(@Nullable Project project, Consumer<ActionEvent> customOkFunction) {
        super(project);
        this.customOkFunction = customOkFunction;
        this.init();
    }


    @Override
    protected void init() {
        super.init();
    }


    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setMinimumSize(new Dimension(500, 250));

        CardLayout cardLayout = new CardLayout();
        JPanel valuePanel = new JPanel(cardLayout);
//        valuePanel.add("String", new JLabel("String"));


        JPanel viewAsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewAsPanel.add(new Label("View as:"));
        JComboBox<NewKeyValueFormatEnum> newKeyValueFormatEnumJComboBox = new JComboBox<>(NewKeyValueFormatEnum.values());
        viewAsPanel.add(newKeyValueFormatEnumJComboBox);


        JPanel stringTypePanel = new JPanel(new BorderLayout());
        stringTypePanel.add(viewAsPanel, BorderLayout.NORTH);
        JBTextArea valueTextArea = new JBTextArea();
        valueTextArea.setLineWrap(true);
        valueTextArea.setRows(10);
        JBScrollPane jbScrollPane = new JBScrollPane(valueTextArea);
        stringTypePanel.add(jbScrollPane, BorderLayout.CENTER);
        valuePanel.add(RedisValueTypeEnum.String.name(), stringTypePanel);

        // todo
        valuePanel.add("List", new JLabel("List"));
        valuePanel.add("Set", new JLabel("Set"));
        valuePanel.add("Zset", new JLabel("ZSet"));
        valuePanel.add("Hash", new JLabel("Hash"));


        JComboBox<RedisValueTypeEnum> redisValueTypeEnumJComboBox = new JComboBox<>(RedisValueTypeEnum.values());
        redisValueTypeEnumJComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                RedisValueTypeEnum type = (RedisValueTypeEnum) e.getItem();
                cardLayout.show(valuePanel, type.name());
            }
        });

        JPanel keyPanel = new JPanel(new BorderLayout());
        keyPanel.setMinimumSize(new Dimension(300, 10));
        keyPanel.add(new JLabel("Key:"), BorderLayout.WEST);
        JTextField keyTextField = new JTextField();
        JPanel keyTextFieldPanel = new JPanel(new BorderLayout());
        keyTextFieldPanel.setBorder(new EmptyBorder(5,0,5,0));
        keyTextFieldPanel.add(keyTextField, BorderLayout.CENTER);
        keyPanel.add(keyTextFieldPanel, BorderLayout.CENTER);

        JPanel typePanel = new JPanel();
        JBLabel typeLabel = new JBLabel("Type:");
        typeLabel.setBorder(new EmptyBorder(5,0,5,0));
        typePanel.add(typeLabel);

        redisValueTypeEnumJComboBox.setSelectedIndex(0);
        typePanel.add(redisValueTypeEnumJComboBox);

        JPanel keyAndTypePanel = new JPanel(new BorderLayout());
        keyAndTypePanel.add(keyPanel, BorderLayout.CENTER);
        keyAndTypePanel.add(typePanel, BorderLayout.AFTER_LINE_ENDS);

        container.add(keyAndTypePanel, BorderLayout.NORTH);
        container.add(valuePanel, BorderLayout.CENTER);
        return container;
    }


    @NotNull
    @Override
    protected Action[] createActions() {
        DialogWrapperExitAction exitAction = new DialogWrapperExitAction("Cancel", CANCEL_EXIT_CODE);
        NewKeyDialog.CustomOKAction okAction = new NewKeyDialog.CustomOKAction();
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
            if (customOkFunction != null) {
                customOkFunction.accept(e);
            }
            close(CANCEL_EXIT_CODE);
        }
    }
}
