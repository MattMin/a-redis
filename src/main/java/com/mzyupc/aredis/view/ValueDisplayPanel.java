package com.mzyupc.aredis.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.enums.RedisValueTypeEnum;
import com.mzyupc.aredis.enums.ValueFormatEnum;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.dialog.AddRowDialog;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.KeyInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.batik.ext.swing.DoubleDocument;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;
import static com.mzyupc.aredis.view.ValueTextAreaManager.createValueTextArea;
import static com.mzyupc.aredis.view.ValueTextAreaManager.formatValue;
import static redis.clients.jedis.ScanParams.SCAN_POINTER_START;

/**
 * @author mzyupc@163.com
 * @date 2021/8/26 8:43 下午
 * <p>
 * Value展示区
 */
@Slf4j
public class ValueDisplayPanel extends JPanel {

    private static final String REMOVED_VALUE = "_VALUE_REMOVED_BY_AREDIS_";
    private final int pageSize = 100;
    private Project project;
    private String key;
    private Value value;
    private Long ttl;
    private RedisValueTypeEnum typeEnum;
    private RedisPoolManager redisPoolManager;
    private DbInfo dbInfo;
    private ARedisKeyValueDisplayPanel parent;
    private KeyTreeDisplayPanel keyTreeDisplayPanel;
    private LoadingDecorator loadingDecorator;

    private EditorTextField valueTextArea;

    /**
     * value内部预览区 选中的value
     */
    private String selectedValue = "";
    /**
     * value内部预览区 选中的field或者score, 依赖于typeEnum
     */
    private String selectedFieldOrScore = "";
    /**
     * value内部预览区 选中的行
     */
    private int selectedRow = -1;
    private int pageIndex = 1;
    /**
     * 一共有多少条数据
     */
    private long total = -1;

    private Map<Integer, String> pageIndexPointerMap;

    private ValueDisplayPanel() {
    }

    private ValueDisplayPanel(LayoutManager layout) {
        super(layout);
        pageIndexPointerMap = new HashMap<>();
        pageIndexPointerMap.put(1, SCAN_POINTER_START);
    }

    public static ValueDisplayPanel getInstance() {
        return new ValueDisplayPanel(new BorderLayout());
    }

    /**
     * 初始化
     *
     * @param project
     * @param parent
     * @param keyTreeDisplayPanel
     * @param key
     * @param redisPoolManager
     * @param dbInfo
     * @param loadingDecorator
     */
    public void init(Project project,
                     ARedisKeyValueDisplayPanel parent,
                     KeyTreeDisplayPanel keyTreeDisplayPanel,
                     String key,
                     RedisPoolManager redisPoolManager,
                     DbInfo dbInfo,
                     LoadingDecorator loadingDecorator) {
        this.redisPoolManager = redisPoolManager;
        this.dbInfo = dbInfo;
        this.project = project;
        this.parent = parent;
        this.keyTreeDisplayPanel = keyTreeDisplayPanel;
        this.loadingDecorator = loadingDecorator;

        loadingDecorator.startLoading(false);

        ApplicationManager.getApplication().invokeLater(() -> {
            try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                Boolean exists = jedis.exists(key);
                if (exists == null || !exists) {
                    ErrorDialog.show("No such key: " + key);
                    return;
                }

                this.key = key;
                this.ttl = jedis.ttl(key);
                String type = jedis.type(key);


                ScanParams scanParams = new ScanParams();
                scanParams.count(pageSize);
                scanParams.match("*");

                switch (type) {
                    case "string":
                        typeEnum = RedisValueTypeEnum.String;
                        String stringValue = jedis.get(key);
                        value = new Value(stringValue);
                        break;

                    case "list":
                        typeEnum = RedisValueTypeEnum.List;
                        int start = (pageIndex - 1) * pageSize;
                        total = jedis.llen(key);
                        List<String> listValue = jedis.lrange(key, start, start + pageSize - 1);
                        value = new Value(listValue);
                        break;

                    case "set":
                        typeEnum = RedisValueTypeEnum.Set;
                        total = jedis.scard(key);
                        String setPointer = pageIndexPointerMap.get(pageIndex);
                        ScanResult<String> sscanResult = jedis.sscan(key, setPointer, scanParams);
                        pageIndexPointerMap.put(pageIndex + 1, sscanResult.getStringCursor());
                        value = new Value(sscanResult.getResult());
                        break;

                    case "zset":
                        typeEnum = RedisValueTypeEnum.Zset;
                        total = jedis.zcard(key);
                        String zsetPointer = pageIndexPointerMap.get(pageIndex);
                        ScanResult<Tuple> zscanResult = jedis.zscan(key, zsetPointer, scanParams);
                        pageIndexPointerMap.put(pageIndex + 1, zscanResult.getStringCursor());
                        value = new Value(zscanResult.getResult());
                        break;

                    case "hash":
                        typeEnum = RedisValueTypeEnum.Hash;
                        total = jedis.hlen(key);
                        String hashPointer = pageIndexPointerMap.get(pageIndex);
                        ScanResult<Map.Entry<String, String>> hscanResult = jedis.hscan(key, hashPointer, scanParams);
                        pageIndexPointerMap.put(pageIndex + 1, hscanResult.getStringCursor());
                        value = new Value(hscanResult.getResult());
                        break;

                    default:
                        return;
                }

                this.initWithValue();
            }
        });

        loadingDecorator.stopLoading();
    }

    private void initWithValue() {
        // key 不存在
        if (value == null) {
            return;
        }

        this.removeAll();

        // 初始化value预览工具栏区
        initValuePreviewToolbarPanel();

        // 初始化value预览区
        initValuePreviewPanel();
    }

    /**
     * 初始化value预览工具栏区
     */
    private void initValuePreviewToolbarPanel() {
        JBTextField keyTextField = new JBTextField(key);
        keyTextField.setPreferredSize(new Dimension(200, 28));
        keyTextField.setToolTipText(keyTextField.getText());
        keyTextField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyTextField.setToolTipText(keyTextField.getText());
            }
        });

        JButton renameButton = createRenameButton(keyTextField);

        JButton reloadButton = createReloadValueButton();

        JButton deleteButton = createDeleteButton();

        JBTextField ttlTextField = new JBTextField();
        ttlTextField.setDocument(new DoubleDocument());
        ttlTextField.setPreferredSize(new Dimension(70, 27));
        ttlTextField.setText(ttl.toString());

        JButton ttlButton = createTTLButton(ttlTextField);

        /**
         * value预览工具栏区
         */
        JPanel valuePreviewToolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        valuePreviewToolbarPanel.add(new JLabel(typeEnum.name() + ":"));
        valuePreviewToolbarPanel.add(keyTextField);
        valuePreviewToolbarPanel.add(renameButton);
        valuePreviewToolbarPanel.add(reloadButton);
        valuePreviewToolbarPanel.add(deleteButton);
        valuePreviewToolbarPanel.add(new JLabel("TTL:"));
        valuePreviewToolbarPanel.add(ttlTextField);
        valuePreviewToolbarPanel.add(ttlButton);

        this.add(valuePreviewToolbarPanel, BorderLayout.NORTH);
    }

    @NotNull
    private JButton createTTLButton(JBTextField ttlTextField) {
        JButton ttlButton = new JButton("Set new TTL");
        ttlButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = ttlTextField.getText();
                new ConfirmDialog(
                        project,
                        "Confirm",
                        String.format("Are you sure you want to set TTL to %s?", text),
                        actionEvent -> {
                            try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                                int newTtl = Integer.parseInt(text);
                                if (newTtl < 0) {
                                    newTtl = -1;
                                }
                                jedis.expire(key, newTtl);
                                ttl = (long) newTtl;
                                ttlTextField.setText(ttl.toString());
                            } catch (NumberFormatException exception) {
                                ErrorDialog.show("Wrong TTL format for input: " + text);
                            }
                        }).show();
            }
        });
        return ttlButton;
    }

    @NotNull
    private JButton createDeleteButton() {
        JButton deleteButton = new JButton("Delete");
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ConfirmDialog confirm = new ConfirmDialog(
                        project,
                        "Confirm",
                        "Are you sure you want to delete this key?",
                        actionEvent -> {
                            DefaultTreeModel treeModel = keyTreeDisplayPanel.getTreeModel();
                            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
                            DefaultMutableTreeNode deletedNode = deleteNode(root, key);
                            if (deletedNode != null) {
                                treeModel.reload(deletedNode);
                            }
                            parent.removeValueDisplayPanel();
                        });
                confirm.show();
            }
        });
        return deleteButton;
    }

    /**
     * 根据Key删除节点
     *
     * @param key
     */
    private DefaultMutableTreeNode deleteNode(DefaultMutableTreeNode node, String key) {
        if (node.isLeaf()) {
            Object userObject = node.getUserObject();
            if (userObject instanceof KeyInfo) {
                KeyInfo keyInfo = (KeyInfo) userObject;
                String nodeKey = keyInfo.getKey();
                if (key.equals(nodeKey)) {
                    keyInfo.setDel(true);
                    node.setUserObject(userObject);
                    redisPoolManager.del(key, dbInfo.getIndex());
                    return node;
                }
            }
            return null;
        } else {
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
                DefaultMutableTreeNode node1 = deleteNode(childNode, key);
                if (node1 != null) {
                    return node1;
                }
            }
        }

        return null;
    }

    /**
     * 初始化value预览区
     */
    private void initValuePreviewPanel() {
        DefaultTableModel tableModel;
        final int valueColumnIndex;
        final int fieldOrScoreColumnIndex;
        switch (typeEnum) {
            case String:
                tableModel = null;
                valueColumnIndex = -1;
                fieldOrScoreColumnIndex = 0;
                break;

            case List:
                List<String> listValue = value.getListValue();
                tableModel = new DefaultTableModel(to2DimensionalArray(listValue), new String[]{"Row", "Value"});
                valueColumnIndex = 1;
                fieldOrScoreColumnIndex = 0;
                break;

            case Set:
                List<String> setValue = value.getSetValue();
                tableModel = new DefaultTableModel(to2DimensionalArray(setValue), new String[]{"Row", "Value"});
                valueColumnIndex = 1;
                fieldOrScoreColumnIndex = 0;
                break;

            case Zset:
                List<Tuple> zsetValue = value.getZsetValue();
                tableModel = new DefaultTableModel(zsetValueListTo2DimensionalArray(zsetValue), new String[]{"Row", "Score", "Value"});
                valueColumnIndex = 2;
                fieldOrScoreColumnIndex = 1;
                break;

            case Hash:
                List<Map.Entry<String, String>> hashValue = value.getHashValue();
                tableModel = new DefaultTableModel(hashValueListTo2DimensionalArray(hashValue), new String[]{"Row", "Field", "Value"});
                valueColumnIndex = 2;
                fieldOrScoreColumnIndex = 1;
                break;

            default:
                return;
        }

        JBTextArea fieldTextArea = new JBTextArea();
        fieldTextArea.setLineWrap(true);
        fieldTextArea.setAutoscrolls(true);

        valueTextArea = createValueTextArea(project, PlainTextLanguage.INSTANCE, "");

        JPanel viewAsAndSavePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        viewAsAndSavePanel.add(new JLabel("View as:"));
        JPanel valuePreviewAndFunctionPanel = new JPanel(new BorderLayout());
        JComboBox<ValueFormatEnum> valueFormatComboBox = new JComboBox<>(ValueFormatEnum.values());
        // View as 功能
        valueFormatComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (ItemEvent.SELECTED == e.getStateChange()) {
                    ValueFormatEnum formatEnum = (ValueFormatEnum) e.getItem();
                    valueTextArea = formatValue(project, valuePreviewAndFunctionPanel, formatEnum, valueTextArea);
                }
            }
        });

        JButton saveValueButton = createSaveValueButton(fieldTextArea);
        viewAsAndSavePanel.add(valueFormatComboBox);
        viewAsAndSavePanel.add(saveValueButton);

        JBLabel valueSizeLabel = new JBLabel();
        updateValueSize(valueSizeLabel);
        /**
         * value功能区
         */
        JPanel valueFunctionPanel = new JPanel(new BorderLayout());
        valueFunctionPanel.add(valueSizeLabel, BorderLayout.WEST);
        valueFunctionPanel.add(viewAsAndSavePanel, BorderLayout.AFTER_LINE_ENDS);

        /**
         * value视图区
         */

        // value size/view as and value preview
        valuePreviewAndFunctionPanel.add(valueFunctionPanel, BorderLayout.NORTH);
        valuePreviewAndFunctionPanel.add(valueTextArea, BorderLayout.CENTER);

        JPanel innerPreviewPanel = new JPanel(new BorderLayout());

        // 需要表格预览
        if (tableModel != null) {
            JBTable valueTable = createValueTable(tableModel, valueColumnIndex, fieldOrScoreColumnIndex, fieldTextArea, valueSizeLabel);

            // 分页panel
            JPanel pagePanel = createPagePanel();

            JButton addRowButton = createAddRowButton();
            JButton deleteRowButton = createDeleteRowButton();

            JPanel rowButtonPanel = new JPanel(new BorderLayout());
            rowButtonPanel.add(addRowButton, BorderLayout.NORTH);
            rowButtonPanel.add(deleteRowButton, BorderLayout.SOUTH);

            JPanel valueTableButtonPanel = new JPanel(new BorderLayout());
            valueTableButtonPanel.add(rowButtonPanel, BorderLayout.NORTH);
            valueTableButtonPanel.add(pagePanel, BorderLayout.SOUTH);

            JBScrollPane valueTableScrollPane = new JBScrollPane(valueTable);
            /**
             * value内部预览区
             */
            JPanel valueInnerPreviewPanel = new JPanel(new BorderLayout());
            valueInnerPreviewPanel.add(valueTableScrollPane, BorderLayout.CENTER);
            valueInnerPreviewPanel.add(valueTableButtonPanel, BorderLayout.AFTER_LINE_ENDS);

            innerPreviewPanel.add(valueInnerPreviewPanel, BorderLayout.CENTER);

            JBSplitter valuePreviewSplitter = new JBSplitter(true, 0.35f);
            valuePreviewSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
            valuePreviewSplitter.setFirstComponent(innerPreviewPanel);

            if (typeEnum == RedisValueTypeEnum.Hash) {
                JLabel fieldLabel = new JLabel("Field:");
                JBScrollPane fieldScrollPane = new JBScrollPane(fieldTextArea);
                JPanel fieldPanel = new JPanel(new BorderLayout());
                fieldPanel.add(fieldLabel, BorderLayout.NORTH);
                fieldPanel.add(fieldScrollPane, BorderLayout.CENTER);

                JBSplitter keyValueSplitter = new JBSplitter(true, 0.1f);
                keyValueSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
                keyValueSplitter.setFirstComponent(fieldPanel);
                keyValueSplitter.setSecondComponent(valuePreviewAndFunctionPanel);

                valuePreviewSplitter.setSecondComponent(keyValueSplitter);
            } else if (typeEnum == RedisValueTypeEnum.Zset) {
                JLabel fieldLabel = new JLabel("Score:");
                JBScrollPane fieldScrollPane = new JBScrollPane(fieldTextArea);
                JPanel fieldPanel = new JPanel(new BorderLayout());
                fieldPanel.add(fieldLabel, BorderLayout.NORTH);
                fieldPanel.add(fieldScrollPane, BorderLayout.CENTER);
                fieldTextArea.setDocument(new DoubleDocument());

                JBSplitter keyValueSplitter = new JBSplitter(true, 0.1f);
                keyValueSplitter.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
                keyValueSplitter.setFirstComponent(fieldPanel);
                keyValueSplitter.setSecondComponent(valuePreviewAndFunctionPanel);

                valuePreviewSplitter.setSecondComponent(keyValueSplitter);
            } else {
                valuePreviewSplitter.setSecondComponent(valuePreviewAndFunctionPanel);
            }

            this.add(valuePreviewSplitter, BorderLayout.CENTER);
        } else {
            updateSelected(-1, value.getStringValue(), null);
            valueTextArea.setText(getSelectedValue());
            updateValueSize(valueSizeLabel);

            /**
             * value预览区
             */
            JPanel valuePreviewPanel = new JPanel(new BorderLayout());
            valuePreviewPanel.add(innerPreviewPanel, BorderLayout.NORTH);
            valuePreviewPanel.add(valuePreviewAndFunctionPanel, BorderLayout.CENTER);
            this.add(valuePreviewPanel, BorderLayout.CENTER);
        }
    }

    @NotNull
    private JPanel createPagePanel() {
        JPanel pagePanel = new JPanel();
        pagePanel.setLayout(new VFlowLayout());
        JBLabel pageSizeLabel = new JBLabel(String.format("Page %s of %s", pageIndex, getPageCount()));
        pageSizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pageSizeLabel.setBorder(new EmptyBorder(0, 10, 0, 0));
        pagePanel.add(pageSizeLabel);
        JBLabel sizeLabel = new JBLabel(String.format("Size: %s", total));
        sizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeLabel.setBorder(new EmptyBorder(0, 10, 0, 0));
        pagePanel.add(sizeLabel);

        JPanel pageButtonPanel = new JPanel();
        JButton prevPageButton = new JButton(AllIcons.Actions.Play_back);
        JButton nextPageButton = new JButton(AllIcons.Actions.Play_forward);

        if (pageIndex <= 1) {
            prevPageButton.setEnabled(false);
        }
        prevPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pageIndex <= 1) {
                    return;
                }

                updatePageDataOnPrevButtonClicked();
                doReload();
                nextPageButton.setEnabled(true);

                if (pageIndex <= 1) {
                    prevPageButton.setEnabled(false);
                }
            }
        });

        if (pageIndex >= getPageCount()) {
            nextPageButton.setEnabled(false);
        }
        nextPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pageIndex >= getPageCount()) {
                    return;
                }

                updatePageDataOnNextButtonClicked();
                doReload();
                prevPageButton.setEnabled(true);
                if (pageIndex >= getPageCount()) {
                    nextPageButton.setEnabled(false);
                }
            }
        });
        pageButtonPanel.add(prevPageButton);
        pageButtonPanel.add(nextPageButton);

        pagePanel.add(pageButtonPanel);
        return pagePanel;
    }

    @NotNull
    private JBTable createValueTable(DefaultTableModel tableModel, int valueColumnIndex, int fieldOrScoreColumnIndex, JBTextArea fieldTextArea, JBLabel valueSizeLabel) {
        JBTable valueTable = new JBTable(tableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // 只能选中一行
        valueTable.setRowSelectionAllowed(true);
        valueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 设置第一列的最大宽度
        TableColumn column = valueTable.getColumnModel().getColumn(0);
        column.setMaxWidth(150);

        DefaultTableCellRenderer tableCellRenderer = new DefaultTableCellRenderer();
        tableCellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        // 数据局中
        valueTable.setDefaultRenderer(Object.class, tableCellRenderer);
        // 表头居中
        valueTable.getTableHeader().setDefaultRenderer(tableCellRenderer);

        // value视图区展示选中的行
        valueTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = valueTable.getSelectedRow();
                    updateSelected(selectedRow,
                            tableModel.getValueAt(selectedRow, valueColumnIndex).toString(),
                            tableModel.getValueAt(selectedRow, fieldOrScoreColumnIndex).toString());
                    fieldTextArea.setText(getSelectedFieldOrScore());
                    valueTextArea.setText(getSelectedValue());
                    valueTextArea.updateUI();
                    updateValueSize(valueSizeLabel);
                }
            }
        });

        // 单击表头排序
        valueTable.setAutoCreateRowSorter(true);

        return valueTable;
    }

    @NotNull
    private JButton createDeleteRowButton() {
        JButton deleteRowButton = new JButton("Delete row");
        deleteRowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        deleteRowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getSelectedIndex() < 0) {
                    ErrorDialog.show("Please select a row!");
                    return;
                }
                new ConfirmDialog(
                        project,
                        "Confirm",
                        "Do you really want to remove this row?",
                        actionEvent -> {
                            try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                                if (!jedis.exists(key)) {
                                    ErrorDialog.show(String.format("No such key: %s", key));
                                } else {
                                    switch (typeEnum) {
                                        case List:
                                            jedis.lset(key, getSelectedIndex(), REMOVED_VALUE);
                                            jedis.lrem(key, 0, REMOVED_VALUE);
                                            break;

                                        case Set:
                                            jedis.srem(key, getSelectedValue());
                                            break;

                                        case Zset:
                                            jedis.zrem(key, getSelectedValue());
                                            break;

                                        case Hash:
                                            jedis.hdel(key, getSelectedFieldOrScore());
                                            break;

                                        default:
                                    }
                                    doReload();
                                }
                            } catch (Exception exception) {
                                log.error("Failed to save value", exception);
                            }
                        }).show();
            }
        });
        return deleteRowButton;
    }

    @NotNull
    private JButton createAddRowButton() {
        JButton addRowButton = new JButton("Add row");
        addRowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addRowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AddRowDialog addRowDialog = new AddRowDialog(project, typeEnum);
                addRowDialog.setCustomOkAction(actionEvent -> {
                    try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                        if (!jedis.exists(key)) {
                            ErrorDialog.show(String.format("No such key: %s", key));
                            addRowDialog.close(OK_EXIT_CODE);
                        } else {
                            switch (typeEnum) {
                                case List:
                                    jedis.lpush(key, addRowDialog.getValue());
                                    break;

                                case Set:
                                    jedis.sadd(key, addRowDialog.getValue());
                                    break;

                                case Zset:
                                    jedis.zadd(key, Double.parseDouble(addRowDialog.getScoreOrField()), addRowDialog.getValue());
                                    break;

                                case Hash:
                                    jedis.hset(key, addRowDialog.getScoreOrField(), addRowDialog.getValue());
                                    break;

                                default:
                            }
                            addRowDialog.close(OK_EXIT_CODE);
                            doReload();
                        }
                    } catch (Exception exception) {
                        log.error("Failed to save value", exception);
                    }
                });
                addRowDialog.show();
            }
        });
        return addRowButton;
    }

    @NotNull
    private JButton createSaveValueButton(JBTextArea fieldTextArea) {
        JButton saveValueButton = new JButton("Save");
        saveValueButton.setEnabled(true);
        saveValueButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedRow == -1 && typeEnum != RedisValueTypeEnum.String) {
                    return;
                }

                String newField = fieldTextArea.getText();
                if (typeEnum == RedisValueTypeEnum.Hash) {
                    if (StringUtils.isEmpty(newField)) {
                        ErrorDialog.show("Please enter a valid Field!");
                        return;
                    }
                }

                if (typeEnum == RedisValueTypeEnum.Hash) {
                    if (StringUtils.isEmpty(newField)) {
                        ErrorDialog.show("Please enter a valid Score!");
                        return;
                    }
                }

                String newValue = valueTextArea.getText();
                if (StringUtils.isEmpty(newValue)) {
                    ErrorDialog.show("Please enter a valid Value!");
                    return;
                }

                new ConfirmDialog(
                        project,
                        "Confirm",
                        "Do you really want to save this?",
                        actionEvent -> {
                            saveNewValue(newField, newValue);
                        }).show();
            }
        });
        return saveValueButton;
    }

    private void saveNewValue(String newFieldOrScore, String newValue) {
        switch (typeEnum) {
            case String:
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    jedis.set(key, newValue);
                }
                break;

            case List:
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    int selectedIndex = getSelectedIndex();
                    if (selectedIndex >= 0) {
                        jedis.lset(key, selectedIndex, newValue);
                    }
                }
                break;

            case Set:
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    if (selectedRow >= 0 && !getSelectedValue().equals(newValue)) {
                        jedis.srem(key, getSelectedValue());
                        jedis.sadd(key, newValue);
                    }
                }
                break;

            case Zset:
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    if (selectedRow >= 0 && StringUtils.isNotEmpty(newFieldOrScore)) {
                        jedis.zrem(key, getSelectedValue());
                        jedis.zadd(key, Double.parseDouble(newFieldOrScore), newValue);
                    }
                }
                break;

            case Hash:
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    if (selectedRow >= 0 && StringUtils.isNotEmpty(getSelectedFieldOrScore())) {
                        jedis.hdel(key, getSelectedFieldOrScore());
                        jedis.hset(key, newFieldOrScore, newValue);
                    }
                }
                break;

            default:
                return;
        }

        // 重新加载
        doReload();

    }

    @NotNull
    private JButton createRenameButton(JBTextField keyTextField) {
        JButton renameButton = new JButton("Rename key");
        renameButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String newKey = keyTextField.getText();
                ConfirmDialog confirmDialog = new ConfirmDialog(
                        project,
                        "Confirm",
                        String.format("Do you want to rename \"%s\" to \"%s\"?", key, newKey),
                        actionEvent -> {
                            try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                                final Long renamenx = jedis.renamenx(key, newKey);
                                if (renamenx == 0) {
                                    ErrorDialog.show(String.format("\"%s\" already exists!", newKey));
                                } else {
                                    key = newKey;
                                    keyTreeDisplayPanel.renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
                                }
                            } catch (Exception exception) {
                                ErrorDialog.show(exception.getMessage());
                            }
                        }
                );
                confirmDialog.show();
            }
        });
        return renameButton;
    }

    @NotNull
    private JButton createReloadValueButton() {
        JButton reloadButton = new JButton("Reload value");
        reloadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doReload();
            }
        });
        return reloadButton;
    }

    private void doReload() {
        init(project, parent, keyTreeDisplayPanel, key, redisPoolManager, dbInfo, loadingDecorator);
        updateSelected(-1, "", "");
        this.updateUI();
    }

    /**
     * 更新value size标签
     *
     * @param valueSizeLabel
     */
    private void updateValueSize(JLabel valueSizeLabel) {
        valueSizeLabel.setText("Value size: " + getSelectedValue().getBytes(StandardCharsets.UTF_8).length + "bytes");
    }

    /**
     * list 转为二维数组
     *
     * @param list
     * @return
     */
    private Object[][] to2DimensionalArray(List<String> list) {
        Object[][] result = new Object[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            result[i][0] = (pageIndex - 1) * pageSize + i;
            result[i][1] = list.get(i);
        }
        return result;
    }

    /**
     * zset value 转为2维数组
     *
     * @param list
     * @return
     */
    private Object[][] zsetValueListTo2DimensionalArray(List<Tuple> list) {
        Object[][] result = new Object[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            result[i][0] = (pageIndex - 1) * pageSize + i;
            Tuple tuple = list.get(i);
            result[i][1] = BigDecimal.valueOf(tuple.getScore()).toPlainString();
            result[i][2] = tuple.getElement();
        }
        return result;
    }

    /**
     * hash value 转为2维数组
     *
     * @param list
     * @return
     */
    private Object[][] hashValueListTo2DimensionalArray(List<Map.Entry<String, String>> list) {
        Object[][] result = new Object[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            result[i][0] = (pageIndex - 1) * pageSize + i;
            Map.Entry<String, String> entry = list.get(i);
            result[i][1] = entry.getKey();
            result[i][2] = entry.getValue();
        }
        return result;
    }

    private String getSelectedFieldOrScore() {
        return this.selectedFieldOrScore;
    }

    private String getSelectedValue() {
        return this.selectedValue;
    }

    private void updateSelected(int selectedRow, String selectedValue, String selectedFieldOrScore) {
        this.selectedRow = selectedRow;
        this.selectedValue = selectedValue;
        this.selectedFieldOrScore = selectedFieldOrScore;
    }

    private int getSelectedIndex() {
        return (pageIndex - 1) * pageSize + selectedRow;
    }

    public String getKey() {
        return this.key;
    }

    private synchronized void updatePageDataOnPrevButtonClicked() {
        pageIndex--;
    }

    private synchronized void updatePageDataOnNextButtonClicked() {
        pageIndex++;
    }

    /**
     * 计算页数
     *
     * @return
     */
    private long getPageCount() {
        long result = total / pageSize;
        long mod = total % pageSize;
        return mod > 0 ? result + 1 : result;
    }

    private class Value {
        private final Object valueData;

        public Value(Object valueData) {
            this.valueData = valueData;
        }

        public String getStringValue() {
            return valueData.toString();
        }

        public List<String> getListValue() {
            return (List<String>) valueData;
        }

        public List<String> getSetValue() {
            return getListValue();
        }

        public List<Tuple> getZsetValue() {
            return (List<Tuple>) valueData;
        }

        public List<Map.Entry<String, String>> getHashValue() {
            return (List<Map.Entry<String, String>>) valueData;
        }
    }
}
