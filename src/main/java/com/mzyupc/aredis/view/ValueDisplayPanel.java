package com.mzyupc.aredis.view;

import com.intellij.ui.components.JBTextField;
import com.mzyupc.aredis.view.dialog.enums.RedisValueTypeEnum;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.Map;

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
    private JPanel valueViewPanel;

    private String key;

    private Long ttl;

    private RedisValueTypeEnum type;

    public ValueDisplayPanel(LayoutManager layout) {
        super(layout);
    }

    public static ValueDisplayPanel getInstance() {
        return new ValueDisplayPanel(new BorderLayout());
    }

    public void initWithString(String key, String value, Long ttl) {
        this.key = key;
        this.ttl = ttl;

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
        valuePreviewToolbarPanel.add(new JLabel("String:"));
        valuePreviewToolbarPanel.add(keyTextField);
        valuePreviewToolbarPanel.add(renameButton);
        valuePreviewToolbarPanel.add(reloadButton);
        valuePreviewToolbarPanel.add(deleteButton);
        valuePreviewToolbarPanel.add(new JLabel("TTL:"));
        valuePreviewToolbarPanel.add(ttlTextField);
        valuePreviewToolbarPanel.add(setNewTtlButton);

        this.add(valuePreviewToolbarPanel, BorderLayout.NORTH);

    }

    public void initWithList(String key, List<String> valueList, Long ttl) {
        this.key = key;
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

    /**
     * 初始化 value预览工具栏区
     */
    private void initValuePreviewToolbarPanel() {

    }


}
