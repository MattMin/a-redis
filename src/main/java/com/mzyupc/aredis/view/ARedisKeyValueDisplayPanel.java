package com.mzyupc.aredis.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.KeyInfo;
import org.apache.commons.lang.StringUtils;

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

    public static final Integer DEFAULT_PAGE_SIZE = 10000;
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
        this.keyTreeDisplayPanel = new KeyTreeDisplayPanel(
                project,
                this,
                splitterContainer,
                dbInfo,
                redisPoolManager,
                this::renderValueDisplayPanel);
        keyTreeDisplayPanel.renderKeyTree(this.getKeyFilter(), this.getGroupSymbol());
    }

    /**
     * 创建一个Key搜索框
     *
     * @return
     */
    private JPanel createSearchBox() {
        searchTextField = new SearchTextField();
        searchTextField.setText(DEFAULT_FILTER);
        searchTextField.addKeyboardListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                keyFilter = searchTextField.getText();
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 根据输入的filter, 重新渲染keyTree
                    if (StringUtils.isEmpty(keyFilter)) {
                        keyFilter = DEFAULT_FILTER;
                        searchTextField.setText(keyFilter);
                    } else {
                        searchTextField.addCurrentTextToHistory();
                    }
                    keyTreeDisplayPanel.renderKeyTree(getKeyFilter(), getGroupSymbol());
                }
            }
        });

        JPanel searchBoxPanel = new JPanel();
        searchBoxPanel.add(new Label("Filter:"));
        searchBoxPanel.add(searchTextField);
        return searchBoxPanel;
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

        groupByPanel.add(new Label("Group by:"));
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

        LoadingDecorator loadingDecorator = new LoadingDecorator(valueDisplayScrollPanel, this, 0);
        splitterContainer.setSecondComponent(loadingDecorator.getComponent());
        valueDisplayPanel.init(project, this, keyTreeDisplayPanel, key, redisPoolManager, dbInfo, loadingDecorator);
    }

    private void createUIComponents() {
        keyToolBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        splitterContainer = new JBSplitter(false, 0.35f);
        removeValueDisplayPanel();
        splitterContainer.setDividerWidth(2);
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
