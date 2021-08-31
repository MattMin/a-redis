package com.mzyupc.aredis.view;

import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.JTreeUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.view.dialog.enums.RedisValueTypeEnum;
import com.mzyupc.aredis.view.dialog.enums.ValueFormatEnum;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.KeyInfo;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static redis.clients.jedis.ScanParams.SCAN_POINTER_START;

/**
 * @author mzyupc@163.com
 * @date 2021/8/26 8:43 下午
 * <p>
 * Value展示区
 */
public class ValueDisplayPanel extends JPanel {

    private Project project;
    /**
     * value预览工具栏区
     */
    private JPanel valuePreviewToolbarPanel;

    /**
     * value预览区
     */
    private JPanel valuePreviewPanel;
    /**
     * value内部预览区
     */
    private JPanel valueInnerPreviewPanel;
    /**
     * value功能区
     */
    private JPanel valueFunctionPanel;
    /**
     * value视图区
     */
    private JBScrollPane valueViewPanel;

    private String key;

    private Value value;

    private Long ttl;

    private RedisValueTypeEnum typeEnum;

    private RedisPoolManager redisPoolManager;

    private DbInfo dbInfo;

    private ARedisKeyValueDisplayPanel parent;

    private KeyTreeDisplayPanel keyTreeDisplayPanel;

    /**
     * value内部预览区 选中的value
     */
    private String selectedValue = "";
    /**
     * value内部预览区 选中的行
     */
    private int selectedRow;

    private ValueDisplayPanel() {
    }

    private ValueDisplayPanel(LayoutManager layout) {
        super(layout);
    }

    public static ValueDisplayPanel getInstance() {
        return new ValueDisplayPanel(new BorderLayout());
    }

    /**
     * 初始化
     *
     * @param keyTreeDisplayPanel
     * @param key
     */
    public void init(Project project, ARedisKeyValueDisplayPanel parent, KeyTreeDisplayPanel keyTreeDisplayPanel, String key, RedisPoolManager redisPoolManager, DbInfo dbInfo) {
        this.redisPoolManager = redisPoolManager;
        this.dbInfo = dbInfo;
        this.project = project;
        this.parent = parent;
        this.keyTreeDisplayPanel = keyTreeDisplayPanel;

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
            scanParams.count(30);
            scanParams.match("*");

            switch (type) {
                case "string":
                    typeEnum = RedisValueTypeEnum.String;
                    String stringValue = jedis.get(key);
                    value = new Value(stringValue);
                    break;

                case "list":
                    typeEnum = RedisValueTypeEnum.List;
                    List<String> listValue = jedis.lrange(key, 0, 30);
                    value = new Value(listValue);
                    break;

                case "set":
                    typeEnum = RedisValueTypeEnum.Set;
                    ScanResult<String> sscanResult = jedis.sscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(sscanResult.getResult());
                    break;

                case "zset":
                    typeEnum = RedisValueTypeEnum.Zset;
                    ScanResult<Tuple> zscanResult = jedis.zscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(zscanResult.getResult());
                    break;

                case "hash":
                    typeEnum = RedisValueTypeEnum.Hash;
                    ScanResult<Map.Entry<String, String>> hscanResult = jedis.hscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(hscanResult.getResult());
                    break;

                default:
                    return;
            }

            this.initWithValue();
        }
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
        ttlTextField.setDocument(new NumberDocument());
        ttlTextField.setText(ttl.toString());

        JButton ttlButton = createTTLButton(ttlTextField);

        valuePreviewToolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
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
                            treeModel.reload();
                            if (deletedNode != null) {
                                dbInfo.setKeyCount(dbInfo.getKeyCount() - 1);
                                TreeNode[] path = deletedNode.getPath();
                                if (path.length > 2) {
                                    JTreeUtil.expandAll(
                                            keyTreeDisplayPanel.getKeyTree(),
                                            new TreePath(Arrays.copyOfRange(path, 0, path.length - 2)),
                                            true);
                                }
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
            KeyInfo userObject = (KeyInfo) node.getUserObject();
            String nodeKey = userObject.getKey();
            if (key.equals(nodeKey)) {
                userObject.setDel(true);
                node.setUserObject(userObject);
                redisPoolManager.del(key, dbInfo.getIndex());
                return node;
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
        switch (typeEnum) {
            case String:
                tableModel = null;
                valueColumnIndex = -1;
                break;

            case List:
                List<String> listValue = value.getListValue();
                tableModel = new DefaultTableModel(to2DimensionalArray(listValue), new String[]{"Row", "Value"});
                valueColumnIndex = 1;
                break;

            case Set:
                List<String> setValue = value.getSetValue();
                tableModel = new DefaultTableModel(to2DimensionalArray(setValue), new String[]{"Row", "Value"});
                valueColumnIndex = 1;
                break;

            case Zset:
                List<Tuple> zsetValue = value.getZsetValue();
                tableModel = new DefaultTableModel(zsetValueListTo2DimensionalArray(zsetValue), new String[]{"Row", "Value", "Score"});
                valueColumnIndex = 1;
                break;

            case Hash:
                List<Map.Entry<String, String>> hashValue = value.getHashValue();
                tableModel = new DefaultTableModel(hashValueListTo2DimensionalArray(hashValue), new String[]{"Row", "Field", "Value"});
                valueColumnIndex = 2;
                break;

            default:
                return;
        }

        JBTextArea valueTextArea = new JBTextArea();
        valueViewPanel = new JBScrollPane(valueTextArea);
        JPanel innerPreviewAndFunctionPanel = new JPanel(new BorderLayout());
        JBLabel valueSizeLabel = new JBLabel();

        // 需要表格预览
        if (tableModel != null) {
            JBTable valueTable = new JBTable(tableModel) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            // 选中单元格而不是一行
            valueTable.setColumnSelectionAllowed(true);
            valueTable.setRowSelectionAllowed(true);

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
                    int selectedRow = valueTable.getSelectedRow();
                    updateSelected(selectedRow, tableModel.getValueAt(selectedRow, valueColumnIndex).toString());
                    valueTextArea.setText(getSelectedValue());
                    updateValueSize(valueSizeLabel);
                }
            });

            JBScrollPane valueTableScrollPane = new JBScrollPane(valueTable);

            JPanel valueTableButtonPanel = new JPanel(new VFlowLayout());
//            BoxLayout boxLayout = new BoxLayout(valueTableButtonPanel, BoxLayout.Y_AXIS);
//            valueTableButtonPanel.setLayout(boxLayout);
            JButton addRowButton = new JButton("Add row");
            addRowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
//            addRowButton.setPreferredSize(new Dimension(100, 27));
            JButton deleteRowButton = new JButton("Delete row");
            deleteRowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            valueTableButtonPanel.add(addRowButton);
            valueTableButtonPanel.add(deleteRowButton);

            valueInnerPreviewPanel = new JPanel(new BorderLayout());
            valueInnerPreviewPanel.add(valueTableScrollPane, BorderLayout.CENTER);
            valueInnerPreviewPanel.add(valueTableButtonPanel, BorderLayout.AFTER_LINE_ENDS);

            innerPreviewAndFunctionPanel.add(valueInnerPreviewPanel, BorderLayout.CENTER);

            JBSplitter valuePreviewSplitter = new JBSplitter(true, 0.35f);
            valuePreviewSplitter.setFirstComponent(innerPreviewAndFunctionPanel);
            valuePreviewSplitter.setSecondComponent(valueViewPanel);
            this.add(valuePreviewSplitter, BorderLayout.CENTER);
        } else {
            updateSelected(0, value.getStringValue());
            valueTextArea.setText(getSelectedValue());
            updateValueSize(valueSizeLabel);

            valuePreviewPanel = new JPanel(new BorderLayout());
            valuePreviewPanel.add(innerPreviewAndFunctionPanel, BorderLayout.NORTH);
            valuePreviewPanel.add(valueViewPanel, BorderLayout.CENTER);
            this.add(valuePreviewPanel, BorderLayout.CENTER);
        }

        // 初始化valueFunctionPanel
        JComboBox<ValueFormatEnum> valueFormatEnumJComboBox = new JComboBox<>(ValueFormatEnum.values());
        JButton saveValueButton = new JButton("Save");
        saveValueButton.setEnabled(false);

        JPanel viewAsAndSavePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        viewAsAndSavePanel.add(new JLabel("View as:"));
        viewAsAndSavePanel.add(valueFormatEnumJComboBox);
        viewAsAndSavePanel.add(saveValueButton);

        valueFunctionPanel = new JPanel(new BorderLayout());
        valueFunctionPanel.add(valueSizeLabel, BorderLayout.WEST);
        valueFunctionPanel.add(viewAsAndSavePanel, BorderLayout.AFTER_LINE_ENDS);
        innerPreviewAndFunctionPanel.add(valueFunctionPanel, BorderLayout.SOUTH);

        valueTextArea.setRows(5);
        valueTextArea.setLineWrap(true);
        valueTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                saveValueButton.setEnabled(true);
            }
        });
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
                init(project, parent, keyTreeDisplayPanel, key, redisPoolManager, dbInfo);
            }
        });
        return reloadButton;
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
            result[i][0] = i;
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
            result[i][0] = i;
            Tuple tuple = list.get(i);
            result[i][1] = tuple.getElement();
            result[i][2] = BigDecimal.valueOf(tuple.getScore()).toPlainString();
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
            result[i][0] = i;
            Map.Entry<String, String> entry = list.get(i);
            result[i][1] = entry.getKey();
            result[i][2] = entry.getValue();
        }
        return result;
    }

    private String getSelectedValue() {
        return this.selectedValue;
    }

    private void updateSelected(int selectedRow, String selectedValue) {
        this.selectedValue = selectedValue;
        this.selectedRow = selectedRow;
    }

    private static class Value {
        private Object valueData;

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

    class NumberDocument extends PlainDocument {
        public NumberDocument() {
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            char[] source = str.toCharArray();
            char[] result = new char[source.length];
            int j = 0;

            for (int i = 0; i < result.length; ++i) {
                if (Character.isDigit(source[i]) || source[i] == '-') {
                    result[j++] = source[i];
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
            }

            super.insertString(offs, new String(result, 0, j), a);
        }
    }
}
