package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.view.enums.RedisValueTypeEnum;
import com.mzyupc.aredis.view.enums.ValueFormatEnum;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.ext.swing.DoubleDocument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Consumer;

import static com.mzyupc.aredis.view.ValueTextAreaManager.createValueTextArea;
import static com.mzyupc.aredis.view.ValueTextAreaManager.formatValue;

/**
 * @author mzyupc@163.com
 * <p>
 * 新增key的对话框
 */
@Slf4j
@Getter
public class NewKeyDialog extends DialogWrapper {

    private Consumer<ActionEvent> customOkAction;

    private CardLayout cardLayout;

    // 选中的数据类型
    private RedisValueTypeEnum selectedType;

    /**
     * key 输入框
     */
    private JTextField keyTextField;

    /**
     * zset value
     */
    private JPanel zsetValuePanel;
    /**
     * zset score
     */
    private JTextField scoreTextField;

    /**
     * hash value
     */
    private JPanel hashValuePanel;
    /**
     * hash field
     */
    private JTextField fieldTextField;

    /**
     * Reload after adding the key
     */
    private boolean reloadSelected;

    private final PropertyUtil propertyUtil;

    private final Project project;

    private EditorTextField valueTextArea;

    public NewKeyDialog(@Nullable Project project) {
        super(project);
        propertyUtil = PropertyUtil.getInstance(project);
        reloadSelected = propertyUtil.getReloadAfterAddingTheKey();
        this.project = project;
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }


    @Override
    protected @Nullable
    JComponent createCenterPanel() {

        JPanel keyPanel = createKeyPanel();
        JPanel valuePanel = createValuePanel();
        JPanel typePanel = createTypePanel(valuePanel);

        JPanel keyAndTypePanel = new JPanel(new BorderLayout());
        keyAndTypePanel.add(keyPanel, BorderLayout.NORTH);
        keyAndTypePanel.add(typePanel, BorderLayout.SOUTH);

        JBCheckBox reloadCheckBox = new JBCheckBox("Reload after adding the key", reloadSelected);
        reloadCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                reloadSelected = reloadCheckBox.isSelected();
                propertyUtil.setReloadAfterAddingTheKey(reloadSelected);
            }
        });
        JPanel reloadPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        reloadPanel.add(reloadCheckBox);

        JPanel container = new JPanel(new BorderLayout());
        container.setMinimumSize(new Dimension(500, 250));
        container.add(keyAndTypePanel, BorderLayout.NORTH);
        container.add(valuePanel, BorderLayout.CENTER);
        container.add(reloadPanel, BorderLayout.AFTER_LAST_LINE);

        return container;
    }

    @NotNull
    private JPanel createValuePanel() {
        JPanel stringValuePanel = createSimpleValuePanel();
        JPanel listValuePanel = createSimpleValuePanel();
        JPanel setValuePanel = createSimpleValuePanel();
        zsetValuePanel = createSimpleValuePanel();
        hashValuePanel = createSimpleValuePanel();

        JPanel zsetTypePanel = createZSetValuePanel();
        JPanel hashTypePanel = createHashValuePanel();

        cardLayout = new CardLayout();
        JPanel valuePanel = new JPanel(cardLayout);
        valuePanel.add(RedisValueTypeEnum.String.name(), stringValuePanel);
        valuePanel.add(RedisValueTypeEnum.List.name(), listValuePanel);
        valuePanel.add(RedisValueTypeEnum.Set.name(), setValuePanel);
        valuePanel.add(RedisValueTypeEnum.Zset.name(), zsetTypePanel);
        valuePanel.add(RedisValueTypeEnum.Hash.name(), hashTypePanel);
        return valuePanel;
    }

    @NotNull
    private JPanel createTypePanel(JPanel valuePanel) {
        JBLabel typeLabel = new JBLabel("Type:");
        typeLabel.setPreferredSize(new Dimension(50, 25));

        JComboBox<RedisValueTypeEnum> redisValueTypeEnumJComboBox = new JComboBox<>(RedisValueTypeEnum.values());

        redisValueTypeEnumJComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ItemEvent.SELECTED == e.getStateChange()) {
                    selectedType = (RedisValueTypeEnum) e.getItem();
                    cardLayout.show(valuePanel, selectedType.name());
                }
            }
        });
        redisValueTypeEnumJComboBox.setSelectedIndex(0);
        selectedType = RedisValueTypeEnum.String;

        JPanel typePanel = new JPanel(new BorderLayout());
        typePanel.add(typeLabel, BorderLayout.WEST);
        typePanel.add(redisValueTypeEnumJComboBox, BorderLayout.CENTER);
        return typePanel;
    }

    @NotNull
    private JPanel createKeyPanel() {
        JPanel keyPanel = new JPanel(new BorderLayout());
        keyPanel.setMinimumSize(new Dimension(300, 10));
        JBLabel keyLabel = new JBLabel("Key:");
        keyLabel.setPreferredSize(new Dimension(50, 25));
        keyPanel.add(keyLabel, BorderLayout.WEST);
        keyTextField = new JTextField();
        keyPanel.add(keyTextField, BorderLayout.CENTER);
        return keyPanel;
    }

    @NotNull
    private JPanel createZSetValuePanel() {
        JPanel scorePanel = new JPanel(new BorderLayout());
        JBLabel scoreLabel = new JBLabel("Score:");
        scoreLabel.setPreferredSize(new Dimension(50, 25));
        scorePanel.add(scoreLabel, BorderLayout.WEST);
        scoreTextField = new JTextField();
        scoreTextField.setDocument(new DoubleDocument());
        scorePanel.add(scoreTextField, BorderLayout.CENTER);

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
        fieldTextField = new JTextField();
        scorePanel.add(fieldTextField, BorderLayout.CENTER);

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
        JBScrollPane valueArea = new JBScrollPane(valueTextArea);

        JComboBox<ValueFormatEnum> newKeyValueFormatEnumJComboBox = new JComboBox<>(ValueFormatEnum.values());
        newKeyValueFormatEnumJComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ItemEvent.SELECTED == e.getStateChange()) {
                    ValueFormatEnum formatEnum = (ValueFormatEnum) e.getItem();
                    valueTextArea = formatValue(project, valueArea, formatEnum, valueTextArea.getText());
                }
            }
        });

        JPanel viewAsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        viewAsPanel.add(new JBLabel("View as:"));
        viewAsPanel.add(newKeyValueFormatEnumJComboBox);

        JPanel valueLabelPanel = new JPanel(new BorderLayout());
        valueLabelPanel.add(new JBLabel("Value:"), BorderLayout.WEST);
        valueLabelPanel.add(viewAsPanel, BorderLayout.EAST);

        JPanel stringTypePanel = new JPanel(new BorderLayout());
        stringTypePanel.add(valueLabelPanel, BorderLayout.NORTH);
        stringTypePanel.add(valueArea, BorderLayout.CENTER);
        return stringTypePanel;
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
            if (customOkAction != null) {
                customOkAction.accept(e);
            }
        }
    }

    public void setCustomOkAction(Consumer<ActionEvent> customOkAction) {
        this.customOkAction = customOkAction;
    }
}
