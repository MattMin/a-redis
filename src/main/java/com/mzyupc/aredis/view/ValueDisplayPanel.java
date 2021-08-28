package com.mzyupc.aredis.view;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
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
import javax.swing.table.DefaultTableModel;
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
 *
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

    private Long ttl;

    private RedisValueTypeEnum typeEnum;

    private RedisPoolMgr redisPoolMgr;

    public ValueDisplayPanel(LayoutManager layout, RedisPoolMgr redisPoolMgr) {
        super(layout);
        this.redisPoolMgr = redisPoolMgr;
    }

    public static ValueDisplayPanel getInstance(RedisPoolMgr redisPoolMgr) {
        return new ValueDisplayPanel(new BorderLayout(), redisPoolMgr);
    }

    public void init(String key, int db) {
        try (Jedis jedis = redisPoolMgr.getJedis(db)) {
            Boolean exists = exists = jedis.exists(key);
            if (exists == null || !exists) {
                return;
            }

            String type = jedis.type(key);

            // 初始化
            ScanParams scanParams = new ScanParams();
            scanParams.count(30);
            scanParams.match("*");

            switch (type) {
                case "string":
                    typeEnum = RedisValueTypeEnum.String;
                    this.initWithString(key, jedis.get(key), jedis.ttl(key));
                    break;

                case "list":
                    typeEnum = RedisValueTypeEnum.List;
                    this.initWithList(key, jedis.lrange(key, 0, 30), jedis.ttl(key));
                    break;

                case "set":
                    typeEnum = RedisValueTypeEnum.Set;
                    ScanResult<String> sscanResult = jedis.sscan(key, SCAN_POINTER_START, scanParams);
                    this.initWithSet(key, sscanResult, jedis.ttl(key));
                    break;

                case "zset":
                    typeEnum = RedisValueTypeEnum.Zset;
                    ScanResult<Tuple> zscanResult = jedis.zscan(key, SCAN_POINTER_START, scanParams);
                    this.initWithZset(key, zscanResult, jedis.ttl(key));
                    break;

                case "hash":
                    typeEnum = RedisValueTypeEnum.Hash;
                    ScanResult<Map.Entry<String, String>> hscanResult = jedis.hscan(key, SCAN_POINTER_START, scanParams);
                    this.initWithHash(key, hscanResult, jedis.ttl(key));
                    break;

                default:
                    return;
            }
        }
    }

    public void initWithString(String key, String value, Long ttl) {
        // key 不存在
        if (value == null) {
            return;
        }

        this.key = key;
        this.ttl = ttl;

        // 初始化value预览工具栏区
        initValuePreviewToolbarPanel();

        // 初始化value预览区
        initValuePreviewPanel(value);
    }

    public void initWithList(String key, List<String> valueList, Long ttl) {
        this.key = key;
        this.ttl = ttl;

        // 初始化value预览工具栏区
        initValuePreviewToolbarPanel();

        // 初始化valueInnerPreviewPanel


        DefaultTableModel tableModel = new DefaultTableModel(to2DimensionalArray(valueList), new String[]{"Row", "Value"});
        JBTable valueTable = new JBTable(tableModel);
        valueTable.setMaximumSize(new Dimension(300, 100));
        JBScrollPane valueTableScrollPane = new JBScrollPane(valueTable);
        valueTableScrollPane.setPreferredSize(new Dimension(300, 200));

        JPanel valueTableButtonPanel = new JPanel(new GridLayout(3, 1));
        valueTableButtonPanel.add(new JButton("Add row"));
        valueTableButtonPanel.add(new JButton("Delete row"));

        valueInnerPreviewPanel = new JPanel(new BorderLayout());
        valueInnerPreviewPanel.add(valueTableScrollPane, BorderLayout.CENTER);
        valueInnerPreviewPanel.add(valueTableButtonPanel, BorderLayout.AFTER_LINE_ENDS);

        // 初始化valueFunctionPanel
        JComboBox<ValueFormatEnum> valueFormatEnumJComboBox = new JComboBox<>(ValueFormatEnum.values());
        JButton saveValueButton = new JButton("Save");
        saveValueButton.setEnabled(false);
        JPanel viewAsAndSavePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        viewAsAndSavePanel.add(new JLabel("View as:"));
        viewAsAndSavePanel.add(valueFormatEnumJComboBox);
        viewAsAndSavePanel.add(saveValueButton);
        valueFunctionPanel = new JPanel(new BorderLayout());
        valueFunctionPanel.add(new JLabel("Value size: " + String.join("", valueList).getBytes(StandardCharsets.UTF_8).length + "bytes"), BorderLayout.WEST);
        valueFunctionPanel.add(viewAsAndSavePanel, BorderLayout.AFTER_LINE_ENDS);

        JPanel innerPreviewAndFunctionPanel = new JPanel(new BorderLayout());
        innerPreviewAndFunctionPanel.add(valueInnerPreviewPanel, BorderLayout.NORTH);
        innerPreviewAndFunctionPanel.add(valueFunctionPanel, BorderLayout.SOUTH);

        JBTextArea valueTextArea = new JBTextArea(valueList.get(0));
        valueTextArea.setRows(5);
        valueTextArea.setLineWrap(true);
        valueTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                saveValueButton.setEnabled(true);
            }
        });
        valueViewPanel = new JBScrollPane(valueTextArea);

        valuePreviewPanel = new JPanel(new BorderLayout());
        valuePreviewPanel.add(innerPreviewAndFunctionPanel, BorderLayout.NORTH);
        valuePreviewPanel.add(valueViewPanel, BorderLayout.CENTER);

        this.add(valuePreviewPanel, BorderLayout.CENTER);

    }
    public void initWithSet(String key, ScanResult<String> scanResult, Long ttl) {
        this.key = key;
    }
    public void initWithZset(String key, ScanResult<Tuple> scanResult, Long ttl) {
        this.key = key;
    }
    public void initWithHash(String key, ScanResult<Map.Entry<String, String>> scanResult, Long ttl) {
        this.key = key;
    }

    private void initValuePreviewToolbarPanel() {
        JBTextField keyTextField = new JBTextField(key);
        keyTextField.setPreferredSize(new Dimension(200,28));
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

    private void initValuePreviewPanel(String value) {
        JComboBox<ValueFormatEnum> valueFormatEnumJComboBox = new JComboBox<>(ValueFormatEnum.values());

        JButton saveValueButton = new JButton("Save");
        saveValueButton.setEnabled(false);

        JPanel viewAsAndSavePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        viewAsAndSavePanel.add(new JLabel("View as:"));
        viewAsAndSavePanel.add(valueFormatEnumJComboBox);
        viewAsAndSavePanel.add(saveValueButton);

        valueFunctionPanel = new JPanel(new BorderLayout());
        valueFunctionPanel.add(new JLabel("Value size: " + value.getBytes(StandardCharsets.UTF_8).length + "bytes"), BorderLayout.WEST);
        valueFunctionPanel.add(viewAsAndSavePanel, BorderLayout.AFTER_LINE_ENDS);

        JBTextArea valueTextArea = new JBTextArea(value);
        valueTextArea.setRows(5);
        valueTextArea.setLineWrap(true);
        valueTextArea.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                saveValueButton.setEnabled(true);
            }
        });
        valueViewPanel = new JBScrollPane(valueTextArea);

        valuePreviewPanel = new JPanel(new BorderLayout());
        valuePreviewPanel.add(valueFunctionPanel, BorderLayout.NORTH);
        valuePreviewPanel.add(valueViewPanel, BorderLayout.CENTER);

        this.add(valuePreviewPanel, BorderLayout.CENTER);
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
}
