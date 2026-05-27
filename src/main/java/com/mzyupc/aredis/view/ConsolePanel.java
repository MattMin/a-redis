package com.mzyupc.aredis.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * @author mzyupc@163.com
 */
public class ConsolePanel extends JPanel implements Disposable {
    private static final String EXECUTE_ACTION = "aredis.console.execute";
    private static final String FOCUS_SEARCH_ACTION = "aredis.console.focusSearch";
    private static final DateTimeFormatter COMMAND_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int COLLAPSED_RESULT_LINES = 8;
    private static final int COLLAPSED_RESULT_MAX_LENGTH = 600;
    private static final int CARD_ARC = 14;
    private static final int CARD_MAX_WIDTH = 1960;
    private static final int CARD_MIN_WIDTH = 320;
    private static final int CARD_ACTION_BUTTON_SIZE = 24;
    private static final int CARD_ACTION_GAP = 2;
    private static final int SEARCH_FIELD_WIDTH = 260;
    private static final Color CARD_BACKGROUND = new JBColor(new Color(245, 247, 250), new Color(60, 63, 65));
    private static final Color CARD_BORDER = new JBColor(new Color(221, 226, 230), new Color(83, 86, 88));
    private static final Color SELECTED_CARD_BORDER = new JBColor(new Color(74, 126, 255), new Color(104, 151, 255));
    private static final Color ERROR_CARD_BACKGROUND = new JBColor(new Color(255, 236, 236), new Color(83, 49, 49));
    private static final Color ERROR_CARD_BORDER = new JBColor(new Color(220, 94, 94), new Color(163, 93, 93));
    private static final Color ERROR_HEADER_COLOR = new JBColor(new Color(168, 33, 33), new Color(255, 166, 166));
    private static final Color ERROR_RESULT_COLOR = new JBColor(new Color(143, 43, 43), new Color(255, 180, 180));
    private static final Color ERROR_BADGE_BACKGROUND = new JBColor(new Color(220, 53, 69), new Color(183, 68, 83));
    private static final Color ERROR_BADGE_FOREGROUND = new JBColor(Color.WHITE, Color.WHITE);
    private static final Highlighter.HighlightPainter SEARCH_MATCH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new JBColor(new Color(255, 236, 153), new Color(105, 88, 41)));
    private static final Highlighter.HighlightPainter CURRENT_SEARCH_MATCH_PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(new JBColor(new Color(255, 196, 77), new Color(142, 98, 32)));

    private final ConnectionInfo connectionInfo;
    private final RedisPoolManager redisPoolManager;
    private final JPanel resultContainer;
    private final JBScrollPane resultScrollPane;
    private final JBTextArea inputArea;
    private final JBLabel dbLabel;
    private final List<String> commandHistory = new LinkedList<>();
    private final List<ExecutionCard> executionCards = new ArrayList<>();
    private final List<SearchMatch> searchMatches = new ArrayList<>();
    private SearchTextField searchTextField;
    private JComboBox<SearchScope> searchScopeComboBox;
    private JBLabel searchStatusLabel;
    private JButton previousSearchButton;
    private JButton nextSearchButton;
    private ExecutionCard selectedSearchCard;
    private int currentSearchMatchIndex = -1;
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

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(dbLabel, BorderLayout.WEST);
        headerPanel.add(createClearButton(), BorderLayout.EAST);

        JBScrollPane inputScrollPane = new JBScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0),
                JBUI.Borders.emptyTop(6)
        ));
        inputScrollPane.setPreferredSize(JBUI.size(-1, 120));

        JPanel inputPanel = new JPanel(new BorderLayout(0, 4));
        inputPanel.add(headerPanel, BorderLayout.NORTH);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        JPanel resultPanel = new JPanel(new BorderLayout());
        resultPanel.add(createSearchPanel(), BorderLayout.NORTH);
        resultPanel.add(resultScrollPane, BorderLayout.CENTER);

        this.add(resultPanel, BorderLayout.CENTER);
        this.add(inputPanel, BorderLayout.SOUTH);

        installSearchShortcut();
    }

    private JPanel createSearchPanel() {
        searchTextField = new SearchTextField();
        searchTextField.setToolTipText("Search console output");
        searchTextField.setPreferredSize(JBUI.size(SEARCH_FIELD_WIDTH, searchTextField.getPreferredSize().height));
        searchTextField.addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateSearchMatches(false);
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateSearchMatches(false);
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateSearchMatches(false);
            }
        });
        searchTextField.addKeyboardListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    moveSearchMatch(e.isShiftDown() ? -1 : 1);
                }
            }
        });

        searchScopeComboBox = new ComboBox<>(SearchScope.values());
        searchScopeComboBox.setFocusable(false);
        searchScopeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                updateSearchMatches(false);
            }
        });

        previousSearchButton = createCardActionButton(AllIcons.Actions.FindAndShowPrevMatches, "Previous match");
        previousSearchButton.addActionListener(e -> moveSearchMatch(-1));
        nextSearchButton = createCardActionButton(AllIcons.Actions.FindAndShowNextMatches, "Next match");
        nextSearchButton.addActionListener(e -> moveSearchMatch(1));
        searchStatusLabel = new JBLabel();
        searchStatusLabel.setForeground(JBColor.GRAY);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4)));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
                JBUI.Borders.emptyLeft(8)
        ));
        searchPanel.add(new JBLabel("Search:"));
        searchPanel.add(searchTextField);
        searchPanel.add(searchScopeComboBox);
        searchPanel.add(previousSearchButton);
        searchPanel.add(nextSearchButton);
        searchPanel.add(searchStatusLabel);
        updateSearchStatus();
        return searchPanel;
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

    private void installSearchShortcut() {
        KeyStroke findShortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        InputMap inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = getActionMap();
        inputMap.put(findShortcut, FOCUS_SEARCH_ACTION);
        actionMap.put(FOCUS_SEARCH_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchTextField == null) {
                    return;
                }
                searchTextField.requestFocusInWindow();
                searchTextField.getTextEditor().selectAll();
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

    static String normalizeCommand(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    static ParsedConsoleCommand parseCommand(String text) {
        String normalizedCommand = normalizeCommand(text);
        if (StringUtils.isBlank(normalizedCommand)) {
            return null;
        }

        List<String> tokens = tokenizeCommand(normalizedCommand);
        if (tokens.isEmpty()) {
            return null;
        }

        return new ParsedConsoleCommand(
                normalizedCommand,
                tokens.get(0),
                new ArrayList<>(tokens.subList(1, tokens.size()))
        );
    }

    static List<String> tokenizeCommand(String commandText) {
        if (StringUtils.isBlank(commandText)) {
            return Collections.emptyList();
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        Character quoteChar = null;
        boolean tokenStarted = false;
        for (int i = 0; i < commandText.length(); i++) {
            char currentChar = commandText.charAt(i);
            if (quoteChar != null) {
                if (currentChar == quoteChar) {
                    quoteChar = null;
                } else {
                    currentToken.append(currentChar);
                }
                tokenStarted = true;
                continue;
            }

            if (currentChar == '"' || currentChar == '\'') {
                quoteChar = currentChar;
                tokenStarted = true;
                continue;
            }

            if (Character.isWhitespace(currentChar)) {
                addCommandToken(tokens, currentToken, tokenStarted);
                tokenStarted = false;
                continue;
            }

            currentToken.append(currentChar);
            tokenStarted = true;
        }

        addCommandToken(tokens, currentToken, tokenStarted);
        return tokens;
    }

    private static void addCommandToken(List<String> tokens, StringBuilder currentToken, boolean tokenStarted) {
        if (!tokenStarted) {
            return;
        }
        tokens.add(currentToken.toString());
        currentToken.setLength(0);
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
        ParsedConsoleCommand parsedCommand = parseCommand(commandText);
        if (parsedCommand == null) {
            return;
        }
        int executingDb = currentDb;
        ThreadPoolManager.execute(() -> {
            List<String> result = redisPoolManager.execRedisCommand(
                    executingDb,
                    parsedCommand.getCommand(),
                    parsedCommand.getArgsArray()
            );
            boolean selectSuccess = "select".equalsIgnoreCase(parsedCommand.getCommand())
                    && !parsedCommand.getArgs().isEmpty()
                    && result != null
                    && result.stream().anyMatch(item -> "OK".equalsIgnoreCase(StringUtils.trim(item)));

            int nextDb = executingDb;
            if (selectSuccess) {
                try {
                    nextDb = Integer.parseInt(parsedCommand.getArgs().get(0));
                } catch (NumberFormatException ignore) {
                }
            }

            int finalNextDb = nextDb;
            ApplicationManager.getApplication().invokeLater(() -> {
                appendExecutionLog(parsedCommand.getDisplayText(), result);
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
        ExecutionCard card = createExecutionCard(commandText, result);
        executionCards.add(card);
        resultContainer.add(card.wrapper);
        resultContainer.revalidate();
        resultContainer.repaint();
        updateSearchMatches(true);
        scrollResultToBottom();
    }

    private ExecutionCard createExecutionCard(String commandText, List<String> result) {
        boolean error = hasErrorResult(result);
        String headerText = String.format("%s  %s", LocalDateTime.now().format(COMMAND_TIME_FORMATTER), commandText);
        String fullResultText = buildResultText(result);
        boolean collapsible = isCollapsibleResult(fullResultText);
        String collapsedResultText = collapsible ? buildCollapsedResultText(fullResultText) : fullResultText;
        ExecutionCard[] cardRef = new ExecutionCard[1];

        JBTextArea headerTextArea = createCardTextArea(headerText, new Font(Font.MONOSPACED, error ? Font.BOLD : Font.PLAIN, 12), error ? ERROR_HEADER_COLOR : JBColor.GRAY);
        headerTextArea.setBorder(JBUI.Borders.emptyTop(3));
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
        cardActions.add(Box.createHorizontalStrut(JBUI.scale(CARD_ACTION_GAP)));
        cardActions.add(rerunButton);
        JButton searchBlockButton = createCardActionButton(AllIcons.Actions.Find, "Search this block");
        searchBlockButton.addActionListener(e -> selectSearchCard(cardRef[0], true));

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
                int preferredWidth = getAvailableCardWidth();
                int actionWidth = cardActions.getPreferredSize().width;
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
                return new Dimension(getAvailableCardWidth(), Integer.MAX_VALUE);
            }
        };
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setOpaque(false);
        card.setBackground(error ? ERROR_CARD_BACKGROUND : CARD_BACKGROUND);
        card.setBorder(createCardBorder(error, false));

        JButton toggleButton = null;
        if (collapsible) {
            toggleButton = createCardActionButton(AllIcons.General.ArrowUp, "Collapse result");
            toggleButton.addActionListener(e -> {
                ExecutionCard executionCard = cardRef[0];
                setCardCollapsed(executionCard, !executionCard.collapsed);
                updateSearchMatches(true);
            });
            cardActions.add(Box.createHorizontalStrut(JBUI.scale(CARD_ACTION_GAP)));
            cardActions.add(toggleButton);
        }
        cardActions.add(Box.createHorizontalStrut(JBUI.scale(CARD_ACTION_GAP)));
        cardActions.add(searchBlockButton);

        card.add(headerPanel, BorderLayout.NORTH);
        card.add(resultTextArea, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(JBUI.Borders.emptyBottom(6));
        wrapper.add(card);

        ExecutionCard executionCard = new ExecutionCard(
                wrapper,
                card,
                headerTextArea,
                resultTextArea,
                toggleButton,
                fullResultText,
                collapsedResultText,
                error,
                collapsible);
        cardRef[0] = executionCard;
        installCardSelection(executionCard);
        return executionCard;
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
        Dimension size = JBUI.size(CARD_ACTION_BUTTON_SIZE, CARD_ACTION_BUTTON_SIZE);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setAlignmentY(Component.CENTER_ALIGNMENT);
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

    private Border createCardBorder(boolean error, boolean selected) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(selected ? SELECTED_CARD_BORDER : error ? ERROR_CARD_BORDER : CARD_BORDER, JBUI.scale(CARD_ARC)),
                JBUI.Borders.empty(10, 12, 10, 12)
        );
    }

    private int getAvailableCardWidth() {
        int viewportWidth = resultScrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            return JBUI.scale(CARD_MAX_WIDTH);
        }
        return Math.max(JBUI.scale(CARD_MIN_WIDTH), Math.min(JBUI.scale(CARD_MAX_WIDTH), viewportWidth - JBUI.scale(24)));
    }

    private void installCardSelection(ExecutionCard card) {
        MouseAdapter listener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                selectSearchCard(card, false);
            }
        };
        card.wrapper.addMouseListener(listener);
        card.card.addMouseListener(listener);
    }

    private void selectSearchCard(ExecutionCard card, boolean focusSearch) {
        if (card == null) {
            return;
        }
        updateSelectedSearchCard(card);
        if (searchScopeComboBox != null) {
            searchScopeComboBox.setSelectedItem(SearchScope.SELECTED_BLOCK);
        }
        updateSearchMatches(false);
        if (focusSearch && searchTextField != null) {
            searchTextField.requestFocusInWindow();
        }
    }

    private void updateSelectedSearchCard(ExecutionCard card) {
        if (selectedSearchCard == card) {
            return;
        }
        if (selectedSearchCard != null) {
            selectedSearchCard.card.setBorder(createCardBorder(selectedSearchCard.error, false));
        }
        selectedSearchCard = card;
        selectedSearchCard.card.setBorder(createCardBorder(selectedSearchCard.error, true));
        selectedSearchCard.card.repaint();
    }

    private void setCardCollapsed(ExecutionCard card, boolean collapsed) {
        if (card == null || !card.collapsible) {
            return;
        }
        card.collapsed = collapsed;
        card.resultTextArea.setText(collapsed ? card.collapsedResultText : card.fullResultText);
        card.toggleButton.setToolTipText(collapsed ? "Expand result" : "Collapse result");
        card.toggleButton.setIcon(collapsed ? AllIcons.General.ArrowDown : AllIcons.General.ArrowUp);
        card.card.revalidate();
        card.card.repaint();
    }

    private void updateSearchMatches(boolean preserveCurrentMatch) {
        int previousMatchIndex = currentSearchMatchIndex;
        clearSearchHighlights();
        searchMatches.clear();
        currentSearchMatchIndex = -1;

        String query = getSearchQuery();
        if (StringUtils.isBlank(query)) {
            updateSearchStatus();
            return;
        }

        List<ExecutionCard> cards = getSearchableCards();
        for (ExecutionCard card : cards) {
            expandCollapsedCardForSearch(card, query);
            addSearchMatches(card, card.headerTextArea, query);
            addSearchMatches(card, card.resultTextArea, query);
        }

        if (!searchMatches.isEmpty()) {
            currentSearchMatchIndex = preserveCurrentMatch
                    ? Math.min(Math.max(previousMatchIndex, 0), searchMatches.size() - 1)
                    : 0;
            refreshSearchHighlightStyles();
            if (!preserveCurrentMatch) {
                scrollToSearchMatch(searchMatches.get(currentSearchMatchIndex));
            }
        }
        updateSearchStatus();
    }

    private String getSearchQuery() {
        return searchTextField == null ? StringUtils.EMPTY : StringUtils.trimToEmpty(searchTextField.getText());
    }

    private List<ExecutionCard> getSearchableCards() {
        if (getSearchScope() == SearchScope.SELECTED_BLOCK) {
            List<ExecutionCard> cards = new ArrayList<>();
            if (selectedSearchCard != null) {
                cards.add(selectedSearchCard);
            }
            return cards;
        }
        return executionCards;
    }

    private SearchScope getSearchScope() {
        Object selectedItem = searchScopeComboBox == null ? null : searchScopeComboBox.getSelectedItem();
        return selectedItem instanceof SearchScope ? (SearchScope) selectedItem : SearchScope.ALL_BLOCKS;
    }

    private void expandCollapsedCardForSearch(ExecutionCard card, String query) {
        if (card.collapsed && containsIgnoreCase(card.fullResultText, query)) {
            setCardCollapsed(card, false);
        }
    }

    private boolean containsIgnoreCase(String text, String query) {
        return StringUtils.defaultString(text).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private void addSearchMatches(ExecutionCard card, JTextArea textArea, String query) {
        String text = StringUtils.defaultString(textArea.getText());
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerQuery);
        while (index >= 0) {
            try {
                Object tag = textArea.getHighlighter().addHighlight(index, index + query.length(), SEARCH_MATCH_PAINTER);
                searchMatches.add(new SearchMatch(card, textArea, index, index + query.length(), tag));
            } catch (BadLocationException ignore) {
            }
            index = lowerText.indexOf(lowerQuery, index + Math.max(1, lowerQuery.length()));
        }
    }

    private void clearSearchHighlights() {
        for (ExecutionCard card : executionCards) {
            card.headerTextArea.getHighlighter().removeAllHighlights();
            card.resultTextArea.getHighlighter().removeAllHighlights();
        }
    }

    private void refreshSearchHighlightStyles() {
        for (int i = 0; i < searchMatches.size(); i++) {
            SearchMatch match = searchMatches.get(i);
            if (match.tag != null) {
                match.textArea.getHighlighter().removeHighlight(match.tag);
            }
            try {
                match.tag = match.textArea.getHighlighter().addHighlight(
                        match.startOffset,
                        match.endOffset,
                        i == currentSearchMatchIndex ? CURRENT_SEARCH_MATCH_PAINTER : SEARCH_MATCH_PAINTER);
            } catch (BadLocationException ignore) {
                match.tag = null;
            }
        }
    }

    private void moveSearchMatch(int direction) {
        if (searchMatches.isEmpty()) {
            return;
        }
        currentSearchMatchIndex = (currentSearchMatchIndex + direction + searchMatches.size()) % searchMatches.size();
        refreshSearchHighlightStyles();
        scrollToSearchMatch(searchMatches.get(currentSearchMatchIndex));
        updateSearchStatus();
    }

    private void scrollToSearchMatch(SearchMatch match) {
        updateSelectedSearchCard(match.card);
        try {
            Shape shape = match.textArea.modelToView2D(match.startOffset);
            Rectangle rectangle = shape == null ? null : shape.getBounds();
            if (rectangle != null) {
                rectangle.grow(JBUI.scale(20), JBUI.scale(20));
                match.textArea.scrollRectToVisible(rectangle);
            }
        } catch (BadLocationException ignore) {
        }
    }

    private void updateSearchStatus() {
        boolean hasMatches = !searchMatches.isEmpty();
        if (previousSearchButton != null) {
            previousSearchButton.setEnabled(hasMatches);
        }
        if (nextSearchButton != null) {
            nextSearchButton.setEnabled(hasMatches);
        }
        if (searchStatusLabel == null) {
            return;
        }
        String query = getSearchQuery();
        if (StringUtils.isBlank(query)) {
            searchStatusLabel.setText(StringUtils.EMPTY);
            return;
        }
        if (getSearchScope() == SearchScope.SELECTED_BLOCK && selectedSearchCard == null) {
            searchStatusLabel.setText("Select a block");
            return;
        }
        if (!hasMatches) {
            searchStatusLabel.setText("0 matches");
            return;
        }
        searchStatusLabel.setText(String.format("%s / %s", currentSearchMatchIndex + 1, searchMatches.size()));
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
                || normalized.contains("EXCEPTION")
                || normalized.contains("NO ENUM CONSTANT REDIS.CLIENTS.JEDIS.PROTOCOL.COMMAND");
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

    private JButton createClearButton() {
        JButton clearButton = createCardActionButton(AllIcons.Actions.GC, "Clear console");
        clearButton.addActionListener(e -> clearConsole());
        return clearButton;
    }

    private void clearConsole() {
        resultContainer.removeAll();
        executionCards.clear();
        searchMatches.clear();
        selectedSearchCard = null;
        currentSearchMatchIndex = -1;
        updateSearchStatus();
        resultContainer.revalidate();
        resultContainer.repaint();
        clearInput();
        updateDbLabel();
    }


    public JComponent getPreferredFocusedComponent() {
        return inputArea;
    }

    @Override
    public void dispose() {
    }

    private enum SearchScope {
        ALL_BLOCKS("All blocks"),
        SELECTED_BLOCK("Selected block");

        private final String text;

        SearchScope(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static class ExecutionCard {
        private final JPanel wrapper;
        private final JPanel card;
        private final JBTextArea headerTextArea;
        private final JBTextArea resultTextArea;
        private final JButton toggleButton;
        private final String fullResultText;
        private final String collapsedResultText;
        private final boolean error;
        private final boolean collapsible;
        private boolean collapsed;

        private ExecutionCard(JPanel wrapper,
                              JPanel card,
                              JBTextArea headerTextArea,
                              JBTextArea resultTextArea,
                              JButton toggleButton,
                              String fullResultText,
                              String collapsedResultText,
                              boolean error,
                              boolean collapsible) {
            this.wrapper = wrapper;
            this.card = card;
            this.headerTextArea = headerTextArea;
            this.resultTextArea = resultTextArea;
            this.toggleButton = toggleButton;
            this.fullResultText = fullResultText;
            this.collapsedResultText = collapsedResultText;
            this.error = error;
            this.collapsible = collapsible;
        }
    }

    private static class SearchMatch {
        private final ExecutionCard card;
        private final JTextArea textArea;
        private final int startOffset;
        private final int endOffset;
        private Object tag;

        private SearchMatch(ExecutionCard card, JTextArea textArea, int startOffset, int endOffset, Object tag) {
            this.card = card;
            this.textArea = textArea;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tag = tag;
        }
    }

    static class ParsedConsoleCommand {
        private final String displayText;
        private final String command;
        private final List<String> args;

        private ParsedConsoleCommand(String displayText, String command, List<String> args) {
            this.displayText = displayText;
            this.command = command;
            this.args = args;
        }

        String getDisplayText() {
            return displayText;
        }

        String getCommand() {
            return command;
        }

        List<String> getArgs() {
            return args;
        }

        String[] getArgsArray() {
            return args.toArray(new String[0]);
        }
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
