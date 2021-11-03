package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.RedisPoolManager;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Optional;

/**
 * @author mzyupc@163.com
 * @date 2021/8/7 5:33 下午
 * <p>
 * 确认提醒窗口
 */
public class InfoDialog extends DialogWrapper {
    private static final String[] SECTIONS = new String[]{
            "server", "clients", "memory", "persistence",
            "stats", "replication", "cpu", "commandstats",
            "cluster", "keyspace"
    };

    private RedisPoolManager redisPoolManager;

    /**
     * @param project
     */
    public InfoDialog(@NotNull Project project, RedisPoolManager redisPoolManager) {
        super(project);
        this.redisPoolManager = redisPoolManager;
        this.setTitle("Info");
        this.setResizable(true);
        this.setAutoAdjustable(true);
        this.setSize(750, 750);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        JPanel container = new JPanel(new VFlowLayout());

        try (Jedis jedis = redisPoolManager.getJedis(0)) {
            for (String section : SECTIONS) {
                String info = jedis.info(section);
                Optional<SectionInfo> optionalSectionInfo = parseSectionInfo(info);
                if (optionalSectionInfo.isPresent()) {
                    SectionInfo sectionInfo = optionalSectionInfo.get();
                    DefaultTableModel tableModel = new DefaultTableModel(sectionInfo.infoArray, new String[]{"Key", "Value"});
                    JBTable infoTable = new JBTable(tableModel) {
                        @Override
                        public boolean isCellEditable(int row, int column) {
                            return false;
                        }
                    };

                    // 只能选中一行
                    infoTable.setRowSelectionAllowed(true);
                    infoTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

                    DefaultTableCellRenderer tableCellRenderer = new DefaultTableCellRenderer();
                    tableCellRenderer.setHorizontalAlignment(SwingConstants.CENTER);
                    // 数据局中
                    infoTable.setDefaultRenderer(Object.class, tableCellRenderer);
                    // 表头居中
                    infoTable.getTableHeader().setDefaultRenderer(tableCellRenderer);
                    infoTable.setAutoCreateRowSorter(true);
                    infoTable.setDoubleBuffered(true);
                    JPanel titleBorder = new JPanel(new BorderLayout());
                    titleBorder.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
                    titleBorder.add(new JBLabel(sectionInfo.getTitle(), SwingConstants.CENTER), BorderLayout.NORTH);
                    titleBorder.add(infoTable.getTableHeader(), BorderLayout.CENTER);
                    titleBorder.add(infoTable, BorderLayout.SOUTH);
                    container.add(titleBorder);
                }
            }
        }

        return new JBScrollPane(container);
    }

    /**
     * 解析info命令的返回值
     *
     * @param info
     * @return
     */
    private Optional<SectionInfo> parseSectionInfo(String info) {
        if (StringUtils.isBlank(info)) {
            return Optional.empty();
        }
        SectionInfo sectionInfo = new SectionInfo();
        String[] split = info.split("\\r\\n");
        if (split.length == 0) {
            return Optional.empty();
        }

        sectionInfo.setTitle(split[0]);
        if (split.length == 1) {
            return Optional.of(sectionInfo);
        }

        String[][] infoArray = new String[split.length - 1][2];
        for (int i = 1; i < split.length; i++) {
            infoArray[i - 1] = split[i].split(":");
        }
        sectionInfo.setInfoArray(infoArray);
        return Optional.of(sectionInfo);
    }

    /**
     * 覆盖默认的ok/cancel按钮
     *
     * @return
     */
    @NotNull
    @Override
    protected Action[] createActions() {
        CustomOKAction okAction = new CustomOKAction();
        // 设置默认的焦点按钮
        okAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
        return new Action[]{okAction};
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
            close(OK_EXIT_CODE);
        }
    }

    @Getter
    @Setter
    private class SectionInfo {
        private String title;
        private String[][] infoArray;
    }
}

