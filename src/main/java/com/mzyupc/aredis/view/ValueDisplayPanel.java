package com.mzyupc.aredis.view;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.view.dialog.enums.RedisValueTypeEnum;
import com.mzyupc.aredis.view.dialog.enums.ValueFormatEnum;
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
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.nio.charset.StandardCharsets;
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

    private RedisPoolMgr redisPoolMgr;

    /**
     * value内部预览区 选中的value
     */
    private String selectedValue = "";
    /**
     * value内部预览区 选中的行
     */
    private int selectedRow;

    public ValueDisplayPanel(LayoutManager layout) {
        super(layout);
    }

    public static ValueDisplayPanel getInstance() {
        return new ValueDisplayPanel(new BorderLayout());
    }

    /**
     * 初始化
     *
     * @param key
     * @param db
     */
    public void init(String key, int db, RedisPoolMgr redisPoolMgr) {
        this.redisPoolMgr = redisPoolMgr;
        try (Jedis jedis = redisPoolMgr.getJedis(db)) {
            Boolean exists = exists = jedis.exists(key);
            if (exists == null || !exists) {
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
                    this.initWithValue();
                    break;

                case "list":
                    typeEnum = RedisValueTypeEnum.List;
                    List<String> listValue = jedis.lrange(key, 0, 30);
                    value = new Value(listValue);
                    this.initWithValue();
                    break;

                case "set":
                    typeEnum = RedisValueTypeEnum.Set;
                    ScanResult<String> sscanResult = jedis.sscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(sscanResult.getResult());
                    this.initWithValue();
                    break;

                case "zset":
                    typeEnum = RedisValueTypeEnum.Zset;
                    ScanResult<Tuple> zscanResult = jedis.zscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(zscanResult.getResult());
                    this.initWithValue();
                    break;

                case "hash":
                    typeEnum = RedisValueTypeEnum.Hash;
                    ScanResult<Map.Entry<String, String>> hscanResult = jedis.hscan(key, SCAN_POINTER_START, scanParams);
                    value = new Value(hscanResult.getResult());
                    this.initWithValue();
                    break;

                default:
            }
        }
    }

    private void initWithValue() {
        // key 不存在
        if (value == null) {
            return;
        }

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

        JButton renameButton = new JButton("Rename Key");

        JButton reloadButton = new JButton("Reload value");

        JButton deleteButton = new JButton("Delete");

        JBTextField ttlTextField = new JBTextField(ttl + "");

        JButton setNewTtlButton = new JButton("Set new TTL");

        valuePreviewToolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        valuePreviewToolbarPanel.add(new JLabel(typeEnum.name() + ":"));
        valuePreviewToolbarPanel.add(keyTextField);
        valuePreviewToolbarPanel.add(renameButton);
        valuePreviewToolbarPanel.add(reloadButton);
        valuePreviewToolbarPanel.add(deleteButton);
        valuePreviewToolbarPanel.add(new JLabel("TTL:"));
        valuePreviewToolbarPanel.add(ttlTextField);
        valuePreviewToolbarPanel.add(setNewTtlButton);

        this.add(valuePreviewToolbarPanel, BorderLayout.NORTH);
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

    /**
     * 更新value size标签
     * @param valueSizeLabel
     */
    private void updateValueSize (JLabel valueSizeLabel) {
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
     * @param list
     * @return
     */
    private Object[][] zsetValueListTo2DimensionalArray(List<Tuple> list) {
        Object[][] result = new Object[list.size()][3];
        for (int i = 0; i < list.size(); i++) {
            result[i][0] = i;
            Tuple tuple = list.get(i);
            result[i][1] = tuple.getElement();
            result[i][2] = tuple.getScore();
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
}
