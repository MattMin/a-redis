package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.utils.RedisPoolManager;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
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

    private final RedisPoolManager redisPoolManager;
    private JBTabbedPane sectionTabPane;

    /**
     * @param project
     */
    public InfoDialog(@NotNull Project project, RedisPoolManager redisPoolManager) {
        super(project);
        this.redisPoolManager = redisPoolManager;
        this.setTitle("Info");
        this.setResizable(true);
        this.setAutoAdjustable(true);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        sectionTabPane = new JBTabbedPane(JTabbedPane.LEFT, JTabbedPane.WRAP_TAB_LAYOUT);

        try (Jedis jedis = redisPoolManager.getJedis(0)) {
            if (jedis == null) {
                return sectionTabPane;
            }
            for (String section : SECTIONS) {
                String info = jedis.info(section);
                Optional<SectionInfo> optionalSectionInfo = parseSectionInfo(info);
                if (optionalSectionInfo.isPresent()) {
                    SectionInfo sectionInfo = optionalSectionInfo.get();
                    JBTable infoTable = createInfoTable(sectionInfo);
                    // 使用tab展示
                    sectionTabPane.addTab(sectionInfo.getTitle(), new JBScrollPane(infoTable));
                }
            }
        }

        return sectionTabPane;
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
    protected Action @NotNull [] createActions() {
        CustomOKAction okAction = new CustomOKAction();
        RefreshAction refreshAction = new RefreshAction();
        // 设置默认的焦点按钮
        okAction.putValue(DialogWrapper.DEFAULT_ACTION, true);
        return new Action[]{refreshAction, okAction};
    }

    /**
     * 自定义 ok Action
     */
    private class CustomOKAction extends DialogWrapperAction {
        protected CustomOKAction() {
            super("OK");
        }

        @Override
        protected void doAction(ActionEvent e) {
            close(OK_EXIT_CODE);
        }
    }

    /**
     * 自定义 ok Action
     */
    private class RefreshAction extends DialogWrapperAction {
        protected RefreshAction() {
            super("Refresh");
        }

        @Override
        protected void doAction(ActionEvent e) {
            // 实现刷新功能
            try (Jedis jedis = redisPoolManager.getJedis(0)) {
                if (jedis == null) {
                    return;
                }

                // 选中的title
                int selectedIndex = sectionTabPane.getSelectedIndex();
                String title = sectionTabPane.getTitleAt(selectedIndex);

                String info = jedis.info(title.replaceAll("\\s|#", ""));
                Optional<SectionInfo> optionalSectionInfo = parseSectionInfo(info);
                if (optionalSectionInfo.isPresent()) {
                    JBTable infoTable = createInfoTable(optionalSectionInfo.get());
                    sectionTabPane.setComponentAt(selectedIndex, new JBScrollPane(infoTable));
                }
            }
        }
    }

    @NotNull
    private JBTable createInfoTable(SectionInfo sectionInfo) {
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
        tableCellRenderer.setBorder(new LineBorder(JBColor.RED));
        // 数据局中
        infoTable.setDefaultRenderer(Object.class, tableCellRenderer);
        // 表头居中
        JTableHeader tableHeader = infoTable.getTableHeader();
        tableHeader.setDefaultRenderer(tableCellRenderer);
        // 单击表头排序
        infoTable.setAutoCreateRowSorter(true);
        return infoTable;
    }

    @Getter
    @Setter
    private class SectionInfo {
        private String title;
        private String[][] infoArray;
    }
}

