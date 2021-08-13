package com.mzyupc.aredis.view;

import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

/**
 * @author mzyupc@163.com
 *
 * key-value展示
 */
public class ARedisKeyValueDisplayPanel extends JPanel implements Disposable {
    private JPanel container;
    private JPanel keyDisplayPanel;
    private JPanel keyTreeToolBarPanel;
    private JPanel keyTreePanel;
    private JPanel valueDisplayPanel;
    private JPanel valuePreviewPanel;
    private JPanel valueFunctionPanel;
    private JPanel valueViewPanel;

    public ARedisKeyValueDisplayPanel() {
//        container.add(new JLabel("TTTTest"));
        container.setPreferredSize(new Dimension(500, 500));
        this.add(container);
    }

    @Override
    public void dispose() {

    }
}
