package com.mzyupc.aredis.view.dialog;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author mzyupc@163.com
 * @date 2021/8/7 5:33 下午
 * <p>
 * 确认提醒窗口
 */
public class InfoDialog extends DialogWrapper {
    static final String[] SECTIONS = new String[]{
            "server", "clients", "memory", "persistence",
            "stats", "replication", "cpu", "commandstats",
            "cluster", "keyspace"
    };

    private final RedisPoolManager redisPoolManager;
    private JBTabbedPane sectionTabPane;
    private JPanel centerPanel;
    private volatile boolean disposed;

    /**
     * @param project
     */
    public InfoDialog(@NotNull Project project, RedisPoolManager redisPoolManager) {
        super(project);
        this.redisPoolManager = redisPoolManager;
        this.setTitle("Info");
        this.setResizable(true);
        this.setAutoAdjustable(true);
        this.setSize(860, 560);
        this.init();
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        sectionTabPane = new JBTabbedPane(JTabbedPane.LEFT, JTabbedPane.WRAP_TAB_LAYOUT);
        centerPanel = new JPanel(new java.awt.BorderLayout());
        centerPanel.setPreferredSize(new Dimension(860, 520));
        centerPanel.add(sectionTabPane, java.awt.BorderLayout.CENTER);
        setLoadingPlaceholder();
        loadAllSectionsAsync(null);
        return centerPanel;
    }

    private void setLoadingPlaceholder() {
        sectionTabPane.removeAll();
        sectionTabPane.addTab("Loading", createMessageComponent("Loading Redis info..."));
    }

    private void setErrorPlaceholder(String message) {
        sectionTabPane.removeAll();
        sectionTabPane.addTab("Error", createMessageComponent(StringUtils.defaultIfBlank(message, "Failed to load Redis info.")));
    }

    private JComponent createMessageComponent(String message) {
        JTextArea textArea = new JTextArea(message);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(820, 480));
        return scrollPane;
    }

    private void loadAllSectionsAsync(@Nullable String preferredSectionTitle) {
        setLoadingPlaceholder();
        ThreadPoolManager.execute(() -> {
            try (Jedis jedis = redisPoolManager.getJedis(0)) {
                if (jedis == null) {
                    invokeOnDialogUiThread(() -> setErrorPlaceholder("Failed to get Redis connection."));
                    return;
                }

                List<SectionInfo> sections = new ArrayList<>();
                for (String section : SECTIONS) {
                    Optional<SectionInfo> optionalSectionInfo = parseSectionInfo(jedis.info(section));
                    optionalSectionInfo.ifPresent(sections::add);
                }

                invokeOnDialogUiThread(() -> renderSections(sections, preferredSectionTitle));
            } catch (Exception e) {
                invokeOnDialogUiThread(() -> setErrorPlaceholder(e.getMessage()));
            }
        });
    }

    private void invokeOnDialogUiThread(Runnable runnable) {
        if (centerPanel == null) {
            ApplicationManager.getApplication().invokeLater(runnable);
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed) {
                return;
            }
            runnable.run();
        }, ModalityState.stateForComponent(centerPanel));
    }

    private void renderSections(List<SectionInfo> sections, @Nullable String preferredSectionTitle) {
        sectionTabPane.removeAll();
        if (sections.isEmpty()) {
            setErrorPlaceholder("No Redis info available.");
            return;
        }
        int selectedIndex = 0;
        for (int i = 0; i < sections.size(); i++) {
            SectionInfo sectionInfo = sections.get(i);
            sectionTabPane.addTab(sectionInfo.getDisplayTitle(), new JBScrollPane(createInfoTable(sectionInfo)));
            if (preferredSectionTitle != null && preferredSectionTitle.equals(sectionInfo.getDisplayTitle())) {
                selectedIndex = i;
            }
        }
        if (sectionTabPane.getTabCount() > 0) {
            sectionTabPane.setSelectedIndex(selectedIndex);
        }
        centerPanel.revalidate();
        centerPanel.repaint();
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
        String[] lines = info.split("\\r?\\n");
        if (lines.length == 0) {
            return Optional.empty();
        }

        String title = StringUtils.trimToEmpty(lines[0]);
        sectionInfo.setTitle(title);
        sectionInfo.setDisplayTitle(title.startsWith("# ") ? title.substring("# ".length()) : title);
        if (lines.length == 1) {
            sectionInfo.setInfoArray(new String[0][2]);
            return Optional.of(sectionInfo);
        }

        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = StringUtils.trimToEmpty(lines[i]);
            if (StringUtils.isBlank(line) || line.startsWith("#")) {
                continue;
            }
            String[] pair = line.split(":", 2);
            if (pair.length == 2) {
                rows.add(new String[]{pair[0], pair[1]});
            } else {
                rows.add(new String[]{line, ""});
            }
        }
        sectionInfo.setInfoArray(rows.toArray(new String[0][2]));
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
            String title = null;
            int selectedIndex = sectionTabPane.getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < sectionTabPane.getTabCount()) {
                title = sectionTabPane.getTitleAt(selectedIndex);
            }
            loadAllSectionsAsync(title);
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
    private static class SectionInfo {
        private String title;
        private String displayTitle;
        private String[][] infoArray;
    }
}
