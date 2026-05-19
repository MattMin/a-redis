package com.mzyupc.aredis.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.KeyInfo;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * @author mzyupc@163.com
 * <p>
 * key-value展示
 */
public class ARedisKeyValueDisplayPanel extends JPanel implements Disposable {
    public static final String DEFAULT_FILTER = "*";
    public static final String DEFAULT_GROUP_SYMBOL = ":";
    private final DbInfo dbInfo;
    private final RedisPoolManager redisPoolManager;
    /**
     * 每次查询KEY的数量
     */
    private Project project;
    private JPanel formPanel;
    private JBSplitter splitterContainer;
    private JPanel keyToolBarPanel;
    private PropertyUtil propertyUtil;
    /**
     * 用来给Key分组的符号
     */
    private String groupSymbol;
    /**
     * key过滤表达式
     */
    private String keyFilter = DEFAULT_FILTER;

    private SearchTextField searchTextField;

    private KeyTreeDisplayPanel keyTreeDisplayPanel;

    private ValueDisplayPanel valueDisplayPanel;

    public ARedisKeyValueDisplayPanel(Project project, ConnectionInfo connectionInfo, DbInfo dbInfo, RedisPoolManager redisPoolManager) {
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        this.dbInfo = dbInfo;
        this.redisPoolManager = redisPoolManager;

        initPanel();
    }

    @Override
    public void dispose() {
        redisPoolManager.dispose();
    }

    public void removeValueDisplayPanel() {
        JPanel emptyPanel = new JPanel();
        emptyPanel.setMinimumSize(new Dimension(100, 100));
        splitterContainer.setSecondComponent(emptyPanel);
        this.valueDisplayPanel = null;
    }

    private void initPanel() {
        ApplicationManager.getApplication().invokeLater(this::initKeyToolBarPanel);
        ApplicationManager.getApplication().invokeLater(this::initKeyTreePanel);
    }

    /**
     * 初始化Key工具栏
     */
    private void initKeyToolBarPanel() {
        // key过滤器
        JPanel searchTextField = createSearchBox();
        // key分组器
        JPanel groupTextField = createGroupByPanel();

        keyToolBarPanel.add(searchTextField);
        keyToolBarPanel.add(groupTextField);
    }

    private void initKeyTreePanel() {
        try {
            this.keyTreeDisplayPanel = new KeyTreeDisplayPanel(
                    project,
                    this,
                    splitterContainer,
                    dbInfo,
                    redisPoolManager,
                    this::renderValueDisplayPanel);
        } catch (RuntimeException e) {
            if ("exception occurred".equals(e.getMessage())) {
                return;
            }
            throw e;
        }
    }

    /**
     * 创建一个Key搜索框
     *
     * @return
     */
    private JPanel createSearchBox() {
        searchTextField = new SearchTextField();
        // 搜索框默认展示为空，实际查询时由后端补全通配符
        searchTextField.setText(StringUtils.EMPTY);
        searchTextField.setToolTipText("输入 key 关键字即可，搜索时会自动进行模糊匹配");
        // 设置搜索框的宽度
        searchTextField.setPreferredSize(new Dimension(300, searchTextField.getPreferredSize().height));
        // 创建文档监听器
        javax.swing.event.DocumentListener documentListener = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                adjustWidth();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                adjustWidth();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                adjustWidth();
            }
        };
        searchTextField.addDocumentListener(documentListener);

        searchTextField.addKeyboardListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String searchKeyword = StringUtils.trimToEmpty(searchTextField.getText());
                keyFilter = buildKeyFilter(searchKeyword);
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 根据输入的filter, 重新渲染keyTree
                    if (StringUtils.isNotEmpty(searchKeyword)) {
                        searchTextField.addCurrentTextToHistory();
                    }
                    keyTreeDisplayPanel.renderKeyTree(getKeyFilter(), getGroupSymbol(), null);
                }
            }
        });

        JPanel searchBoxPanel = new JPanel();
        searchBoxPanel.add(new JLabel("Filter:"));
        searchBoxPanel.add(searchTextField);
        return searchBoxPanel;
    }

    // 作为类成员方法
    private void adjustWidth() {
        if (searchTextField == null) return;

        JTextField textField = searchTextField.getTextEditor();
        FontMetrics fontMetrics = textField.getFontMetrics(textField.getFont());
        String text = searchTextField.getText();

        // 添加一些额外空间
        int width = fontMetrics.stringWidth(text) + 100;
        // 设置最小宽度
        width = Math.max(width, 300);
        // 设置最大宽度
        width = Math.min(width, 1000);

        // 设置新的首选大小
        searchTextField.setPreferredSize(new Dimension(width, searchTextField.getPreferredSize().height));

        // 重新验证布局
        searchTextField.revalidate();
    }

    private String buildKeyFilter(String searchKeyword) {
        if (StringUtils.isBlank(searchKeyword)) {
            return DEFAULT_FILTER;
        }
        return DEFAULT_FILTER + searchKeyword + DEFAULT_FILTER;
    }

    /**
     * 创建分组panel
     *
     * @return
     */
    private JPanel createGroupByPanel() {
        JPanel groupByPanel = new JPanel();
        groupByPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        JBTextField groupText = new JBTextField(getGroupSymbol());
        groupText.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                groupSymbol = groupText.getText();
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 根据输入的key, 重新渲染keyTree
                    keyTreeDisplayPanel.updateKeyTree(getGroupSymbol());
                }
                // 保存groupSymbol
                propertyUtil.saveGroupSymbol(dbInfo, getGroupSymbol());
            }
        });

        groupByPanel.add(new JLabel("Group by:"));
        groupByPanel.add(groupText);
        return groupByPanel;
    }

    /**
     * 渲染valueDisplayPanel
     *
     * @param keyInfo
     */
    private void renderValueDisplayPanel(KeyInfo keyInfo) {
        // 根据key的不同类型, 组装不同的valueDisplayPanel
        String key = keyInfo.getKey();

        /**
         * value 展示区
         */
        valueDisplayPanel = ValueDisplayPanel.getInstance();
        valueDisplayPanel.setMinimumSize(new Dimension(100, 100));
        JBScrollPane valueDisplayScrollPanel = new JBScrollPane(valueDisplayPanel);
        valueDisplayScrollPanel.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        valueDisplayScrollPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        LoadingDecorator loadingDecorator = new LoadingDecorator(valueDisplayScrollPanel, project, 0);
        splitterContainer.setSecondComponent(loadingDecorator.getComponent());
        valueDisplayPanel.init(project, this, keyTreeDisplayPanel, key, redisPoolManager, dbInfo, loadingDecorator);
    }

    private void createUIComponents() {
        keyToolBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        splitterContainer = new JBSplitter(false, 0.35f);
        removeValueDisplayPanel();
        splitterContainer.setDividerWidth(2);
        splitterContainer.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        splitterContainer.setShowDividerControls(true);
        splitterContainer.setSplitterProportionKey("aRedis.keyValue.splitter");

        formPanel = new JPanel(new BorderLayout());
        formPanel.add(keyToolBarPanel, BorderLayout.NORTH);
        formPanel.add(splitterContainer, BorderLayout.CENTER);

        this.setLayout(new BorderLayout());
        this.add(formPanel);
    }

    public String getGroupSymbol() {
        if (groupSymbol == null) {
            groupSymbol = propertyUtil.getGroupSymbol(dbInfo);
        }
        return groupSymbol;
    }

    public String getKeyFilter() {
        return keyFilter;
    }

    public ValueDisplayPanel getValueDisplayPanel() {
        return valueDisplayPanel;
    }
}
