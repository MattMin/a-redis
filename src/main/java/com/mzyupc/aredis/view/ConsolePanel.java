package com.mzyupc.aredis.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.action.CustomAction;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.DateUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * @author mzyupc@163.com
 */
public class ConsolePanel extends JPanel implements Disposable {
    private static final String EXECUTE_ACTION = "aredis.console.execute";
    private static final int COLLAPSED_RESULT_LINES = 8;
    private static final int COLLAPSED_RESULT_MAX_LENGTH = 600;
    private static final int CARD_ARC = 14;
    private static final Color CARD_BACKGROUND = new JBColor(new Color(245, 247, 250), new Color(60, 63, 65));
    private static final Color CARD_BORDER = new JBColor(new Color(221, 226, 230), new Color(83, 86, 88));
    private static final Color ERROR_CARD_BACKGROUND = new JBColor(new Color(255, 236, 236), new Color(83, 49, 49));
    private static final Color ERROR_CARD_BORDER = new JBColor(new Color(220, 94, 94), new Color(163, 93, 93));
    private static final Color ERROR_HEADER_COLOR = new JBColor(new Color(168, 33, 33), new Color(255, 166, 166));
    private static final Color ERROR_RESULT_COLOR = new JBColor(new Color(143, 43, 43), new Color(255, 180, 180));
    private static final Color ERROR_BADGE_BACKGROUND = new JBColor(new Color(220, 53, 69), new Color(183, 68, 83));
    private static final Color ERROR_BADGE_FOREGROUND = new JBColor(Color.WHITE, Color.WHITE);

    private final ConnectionInfo connectionInfo;
    private final RedisPoolManager redisPoolManager;
    private final JPanel resultContainer;
    private final JBScrollPane resultScrollPane;
    private final JBTextArea inputArea;
    private final JBLabel dbLabel;
    private final List<String> commandHistory = new LinkedList<>();
    private volatile int currentDb;
    private int historyIndex = -1;
    private String editingCommand = "";

    public ConsolePanel(ConnectionInfo connectionInfo, RedisPoolManager redisPoolManager, int initialDb) {
        this.connectionInfo = connectionInfo;
        this.redisPoolManager = redisPoolManager;
        this.currentDb = initialDb;
        this.resultContainer = createResultContainer();
        this.resultScrollPane = createResultScrollPane();
        this.inputArea = createInputArea();
        this.dbLabel = new JBLabel();
        this.setLayout(new BorderLayout());
        init();
    }

    private void init() {
        updateDbLabel();
        installInputListener();
        installResultResizeListener();

        DefaultActionGroup actions = new DefaultActionGroup();
        actions.add(createClearAction());
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, actions, false);
        actionToolbar.setTargetComponent(inputArea);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(dbLabel, BorderLayout.WEST);
        headerPanel.add(actionToolbar.getComponent(), BorderLayout.EAST);

        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.emptyTop(6)
        ));
        inputScrollPane.setPreferredSize(JBUI.size(-1, 120));

        JPanel inputPanel = new JPanel(new BorderLayout(0, 4));
        inputPanel.add(headerPanel, BorderLayout.NORTH);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        this.add(resultScrollPane, BorderLayout.CENTER);
        this.add(inputPanel, BorderLayout.SOUTH);
    }

    private void installResultResizeListener() {
        resultScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resultContainer.revalidate();
                resultContainer.repaint();
            }
        });
    }

    private void installInputListener() {
        InputMap inputMap = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputArea.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), EXECUTE_ACTION);
        inputMap.put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");

        actionMap.put(EXECUTE_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String command = normalizeCommand(inputArea.getText());
                if (StringUtils.isBlank(command)) {
                    clearInput();
                    resetHistoryCursor();
                    return;
                }

                rememberCommand(command);
                clearInput();
                resetHistoryCursor();
                executeCommand(command);
            }
        });

        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP && isCaretAtFirstLine()) {
                    showPreviousHistory();
                    e.consume();
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN && isCaretAtLastLine()) {
                    showNextHistory();
                    e.consume();
                }
            }
        });
    }

    private JPanel createResultContainer() {
        JPanel panel = new JPanel();
        panel.setLayout(new VerticalLayout(JBUI.scale(6)));
        panel.setBorder(JBUI.Borders.empty(10));
        panel.setBackground(JBColor.PanelBackground);
        return panel;
    }

    private JBScrollPane createResultScrollPane() {
        JBScrollPane scrollPane = new JBScrollPane(resultContainer);
        scrollPane.setBorder(JBUI.Borders.empty());
        scrollPane.getViewport().setBackground(JBColor.PanelBackground);
        scrollPane.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
        return scrollPane;
    }

    private JBTextArea createInputArea() {
        JBTextArea textArea = new JBTextArea();
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(JBUI.insets(8));
        textArea.setRows(5);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setToolTipText("Press Enter to run, Shift+Enter for a new line");
        return textArea;
    }

    private String normalizeCommand(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r", "").replace("\n", "").trim();
    }

    private void clearInput() {
        inputArea.setText("");
    }

    private void resetHistoryCursor() {
        historyIndex = -1;
        editingCommand = "";
    }

    private void rememberCommand(String command) {
        if (commandHistory.isEmpty() || !command.equals(commandHistory.get(commandHistory.size() - 1))) {
            commandHistory.add(command);
        }
    }

    private void showPreviousHistory() {
        if (commandHistory.isEmpty() || !isCaretAtFirstLine()) {
            return;
        }
        if (historyIndex == -1) {
            editingCommand = inputArea.getText();
            historyIndex = commandHistory.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }
        applyHistoryText(commandHistory.get(historyIndex));
    }

    private void showNextHistory() {
        if (!isCaretAtLastLine()) {
            return;
        }
        if (historyIndex == -1) {
            return;
        }
        if (historyIndex < commandHistory.size() - 1) {
            historyIndex++;
            applyHistoryText(commandHistory.get(historyIndex));
            return;
        }
        historyIndex = -1;
        applyHistoryText(editingCommand);
    }

    private void applyHistoryText(String text) {
        inputArea.setText(StringUtils.defaultString(text));
        inputArea.setCaretPosition(inputArea.getDocument().getLength());
    }

    private boolean isCaretAtFirstLine() {
        return getCaretLine() == 0;
    }

    private boolean isCaretAtLastLine() {
        return getCaretLine() == inputArea.getLineCount() - 1;
    }

    private int getCaretLine() {
        try {
            return inputArea.getLineOfOffset(inputArea.getCaretPosition());
        } catch (BadLocationException e) {
            return 0;
        }
    }

    private void executeCommand(String commandText) {
        int executingDb = currentDb;
        ThreadPoolManager.execute(() -> {
            String[] split = commandText.split("\\s");
            List<String> result = redisPoolManager.execRedisCommand(executingDb, split[0], assembleArgs(split));
            boolean selectSuccess = "select".equalsIgnoreCase(split[0])
                    && split.length > 1
                    && result != null
                    && result.stream().anyMatch(item -> "OK".equalsIgnoreCase(StringUtils.trim(item)));

            int nextDb = executingDb;
            if (selectSuccess) {
                try {
                    nextDb = Integer.parseInt(split[1]);
                } catch (NumberFormatException ignore) {
                }
            }

            int finalNextDb = nextDb;
            ApplicationManager.getApplication().invokeLater(() -> {
                appendExecutionLog(commandText, result);
                if (finalNextDb != currentDb) {
                    currentDb = finalNextDb;
                    updateDbLabel();
                }
            });
        });
    }

    private void updateDbLabel() {
        dbLabel.setBorder(JBUI.Borders.empty(1, 8, 1, 0));
        dbLabel.setFont(dbLabel.getFont().deriveFont(Font.PLAIN, 12f));
        dbLabel.setText(String.format("%s-DB%s", connectionInfo.getName(), currentDb));
    }

    private void appendExecutionLog(String commandText, List<String> result) {
        JPanel card = createExecutionCard(commandText, result);
        resultContainer.add(card);
        resultContainer.revalidate();
        resultContainer.repaint();
        scrollResultToBottom();
    }

    private JPanel createExecutionCard(String commandText, List<String> result) {
        boolean error = hasErrorResult(result);
        String headerText = String.format("%s  %s", DateUtils.formatDate(new Date(), "yyyy-MM-dd HH:mm:ss"), commandText);
        String fullResultText = buildResultText(result);
        boolean collapsible = isCollapsibleResult(fullResultText);
        String collapsedResultText = collapsible ? buildCollapsedResultText(fullResultText) : fullResultText;
        boolean[] collapsed = new boolean[]{false};

        JBTextArea headerTextArea = createCardTextArea(headerText, new Font(Font.MONOSPACED, error ? Font.BOLD : Font.PLAIN, 12), error ? ERROR_HEADER_COLOR : JBColor.GRAY);
        JBTextArea resultTextArea = createCardTextArea(fullResultText, new Font(Font.MONOSPACED, Font.PLAIN, 13), error ? ERROR_RESULT_COLOR : JBColor.foreground());

        JPanel leftPanel = new JPanel(new BorderLayout(0, 4));
        leftPanel.setOpaque(false);
        leftPanel.add(headerTextArea, BorderLayout.NORTH);
        if (error) {
            leftPanel.add(createErrorBadge(), BorderLayout.SOUTH);
        }

        JPanel cardActions = createCardActionsPanel();
        JButton copyButton = createCardActionButton(AllIcons.Actions.Copy, "Copy...");
        copyButton.addActionListener(e -> showCopyPopup(copyButton, headerText, commandText, fullResultText));
        JButton rerunButton = createCardActionButton(AllIcons.Actions.Refresh, "Re-run command");
        rerunButton.addActionListener(e -> rerunCommand(commandText));
        cardActions.add(copyButton);
        cardActions.add(rerunButton);

        JPanel headerPanel = new JPanel(new BorderLayout(6, 0));
        headerPanel.setOpaque(false);
        headerPanel.add(leftPanel, BorderLayout.CENTER);
        headerPanel.add(cardActions, BorderLayout.EAST);

        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D graphics2D = (Graphics2D) g.create();
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(getBackground());
                graphics2D.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), JBUI.scale(CARD_ARC), JBUI.scale(CARD_ARC)));
                graphics2D.dispose();
                super.paintComponent(g);
            }

            @Override
            public Dimension getPreferredSize() {
                Insets insets = getInsets();
                int maxWidth = getAvailableCardMaxWidth();
                int actionWidth = cardActions.getPreferredSize().width;
                int naturalHeaderWidth = estimateTextWidth(headerTextArea.getText(), headerTextArea.getFont()) + actionWidth;
                int naturalResultWidth = estimateTextWidth(resultTextArea.getText(), resultTextArea.getFont());
                int preferredWidth = Math.min(maxWidth,
                        Math.max(JBUI.scale(120), Math.max(naturalHeaderWidth, naturalResultWidth) + insets.left + insets.right));

                int innerWidth = Math.max(JBUI.scale(96), preferredWidth - insets.left - insets.right);
                int headerTextWidth = Math.max(JBUI.scale(72), innerWidth - actionWidth - JBUI.scale(6));

                headerTextArea.setSize(headerTextWidth, Short.MAX_VALUE);
                resultTextArea.setSize(innerWidth, Short.MAX_VALUE);

                Dimension headerSize = headerPanel.getPreferredSize();
                Dimension resultSize = resultTextArea.getPreferredSize();
                return new Dimension(preferredWidth, insets.top + headerSize.height + JBUI.scale(8) + resultSize.height + insets.bottom);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(getAvailableCardMaxWidth(), Integer.MAX_VALUE);
            }
        };
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOpaque(false);
        card.setBackground(error ? ERROR_CARD_BACKGROUND : CARD_BACKGROUND);
        card.setBorder(createCardBorder(error));

        if (collapsible) {
            JButton toggleButton = createCardActionButton(AllIcons.General.ArrowUp, "Collapse result");
            toggleButton.addActionListener(e -> {
                collapsed[0] = !collapsed[0];
                resultTextArea.setText(collapsed[0] ? collapsedResultText : fullResultText);
                ((JButton) e.getSource()).setToolTipText(collapsed[0] ? "Expand result" : "Collapse result");
                ((JButton) e.getSource()).setIcon(collapsed[0] ? AllIcons.General.ArrowDown : AllIcons.General.ArrowUp);
                card.revalidate();
                card.repaint();
                scrollResultToBottom();
            });
            cardActions.add(toggleButton);
        }

        card.add(headerPanel, BorderLayout.NORTH);
        card.add(resultTextArea, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(JBUI.Borders.emptyBottom(6));
        wrapper.add(card);
        return wrapper;
    }

    private JBTextArea createCardTextArea(String text, Font font, Color foreground) {
        JBTextArea textArea = new JBTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(JBUI.Borders.empty());
        textArea.setFont(font);
        textArea.setForeground(foreground);
        textArea.setFocusable(true);
        textArea.setEnabled(true);
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        return textArea;
    }

    private JBLabel createErrorBadge() {
        JBLabel badge = new JBLabel("ERROR", AllIcons.General.BalloonError, SwingConstants.LEFT);
        badge.setOpaque(true);
        badge.setBackground(ERROR_BADGE_BACKGROUND);
        badge.setForeground(ERROR_BADGE_FOREGROUND);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        badge.setBorder(JBUI.Borders.empty(2, 6));
        return badge;
    }

    private JPanel createCardActionsPanel() {
        JPanel cardActions = new JPanel();
        cardActions.setLayout(new BoxLayout(cardActions, BoxLayout.X_AXIS));
        cardActions.setOpaque(false);
        return cardActions;
    }

    private JButton createCardActionButton(Icon icon, String toolTipText) {
        JButton button = new JButton(icon);
        button.setFocusable(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(JBUI.Borders.empty(1));
        button.setMargin(JBUI.emptyInsets());
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(toolTipText);
        button.setBorderPainted(false);
        return button;
    }

    private void showCopyPopup(Component invoker, String headerText, String commandText, String resultText) {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(createCopyPopupAction("Copy Result", resultText));
        group.add(createCopyPopupAction("Copy Command", commandText));
        group.add(createCopyPopupAction("Copy All", headerText + "\n" + resultText));
        ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, group);
        popupMenu.getComponent().show(invoker, 0, invoker.getHeight());
    }

    private AnAction createCopyPopupAction(String text, String value) {
        return new AnAction(text, text, AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                copyResult(value);
            }
        };
    }

    private Border createCardBorder(boolean error) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(error ? ERROR_CARD_BORDER : CARD_BORDER, JBUI.scale(CARD_ARC)),
                JBUI.Borders.empty(10, 12, 10, 12)
        );
    }

    private int getAvailableCardMaxWidth() {
        int viewportWidth = resultScrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            return JBUI.scale(720);
        }
        return Math.max(JBUI.scale(140), Math.min(JBUI.scale(720), viewportWidth - JBUI.scale(24)));
    }

    private int estimateTextWidth(String text, Font font) {
        FontMetrics fontMetrics = getFontMetrics(font);
        int maxLineWidth = 0;
        String[] lines = StringUtils.defaultString(text).split("\\r?\\n", -1);
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, fontMetrics.stringWidth(line));
        }
        return maxLineWidth;
    }

    private String buildResultText(List<String> result) {
        if (result == null || result.isEmpty()) {
            return "(empty)";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(result.get(i) == null ? "null" : result.get(i));
        }
        return builder.toString();
    }

    private boolean isCollapsibleResult(String resultText) {
        if (StringUtils.isBlank(resultText)) {
            return false;
        }
        String[] lines = resultText.split("\\r?\\n");
        return lines.length > COLLAPSED_RESULT_LINES || resultText.length() > COLLAPSED_RESULT_MAX_LENGTH;
    }

    private String buildCollapsedResultText(String fullResultText) {
        if (StringUtils.isBlank(fullResultText)) {
            return fullResultText;
        }
        String[] lines = fullResultText.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        int visibleLines = Math.min(lines.length, COLLAPSED_RESULT_LINES);
        for (int i = 0; i < visibleLines; i++) {
            if (i > 0) {
                builder.append("\n");
            }
            builder.append(lines[i]);
        }
        if (visibleLines < lines.length || fullResultText.length() > COLLAPSED_RESULT_MAX_LENGTH) {
            builder.append("\n...");
        }
        if (builder.length() > COLLAPSED_RESULT_MAX_LENGTH) {
            return builder.substring(0, COLLAPSED_RESULT_MAX_LENGTH) + "...";
        }
        return builder.toString();
    }

    private boolean hasErrorResult(List<String> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        for (String line : result) {
            if (isErrorLine(line)) {
                return true;
            }
        }
        return false;
    }

    private boolean isErrorLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String normalized = line.trim().toUpperCase();
        return normalized.startsWith("ERR")
                || normalized.startsWith("WRONG")
                || normalized.startsWith("NOAUTH")
                || normalized.startsWith("NOPERM")
                || normalized.startsWith("READONLY")
                || normalized.contains("EXCEPTION");
    }

    private void copyResult(String resultText) {
        CopyPasteManager.getInstance().setContents(new StringSelection(resultText));
    }

    private void rerunCommand(String commandText) {
        rememberCommand(commandText);
        resetHistoryCursor();
        executeCommand(commandText);
    }

    private void scrollResultToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar verticalScrollBar = resultScrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
    }

    private String[] assembleArgs(String[] split) {
        List<String> result = Lists.newArrayList();
        boolean inString = false;
        boolean doubleQuote = false;
        StringBuilder arg = new StringBuilder();
        for (int i = 1; i < split.length; i++) {
            String s = split[i];
            if (s.matches("^[\"'].*[\"']$")) {
                result.add(s.replaceAll("[\"']", ""));
                continue;
            }
            if (!StringUtils.isEmpty(arg.toString())) {
                arg.append(" ");
            }

            if (s.startsWith("\"") && !inString) {
                arg.append(s.replace("\"", ""));
                inString = true;
                doubleQuote = true;
                continue;
            }
            if (s.startsWith("'") && !inString) {
                arg.append(s.replace("'", ""));
                inString = true;
                doubleQuote = false;
                continue;
            }
            if (s.endsWith("\"") && inString && doubleQuote) {
                arg.append(s.replace("\"", ""));
                inString = false;
                result.add(arg.toString());
                arg = new StringBuilder();
                continue;
            }
            if (s.endsWith("'") && inString && !doubleQuote) {
                arg.append(s.replace("'", ""));
                inString = false;
                result.add(arg.toString());
                arg = new StringBuilder();
                continue;
            }
            if (inString) {
                arg.append(s);
                continue;
            }
            result.add(s);
        }
        return result.toArray(new String[0]);
    }

    private AnAction createClearAction() {
        return new CustomAction("Clear console", "Clear console", AllIcons.Actions.GC) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
                resultContainer.removeAll();
                resultContainer.revalidate();
                resultContainer.repaint();
                clearInput();
                updateDbLabel();
            }
        };
    }

    public JComponent getPreferredFocusedComponent() {
        return inputArea;
    }

    @Override
    public void dispose() {
    }

    private static class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int arc;

        private RoundedBorder(Color color, int arc) {
            this.color = color;
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D graphics2D = (Graphics2D) g.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics2D.setColor(color);
            graphics2D.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            graphics2D.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return JBUI.insets(1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 1, 1);
            return insets;
        }
    }
}
