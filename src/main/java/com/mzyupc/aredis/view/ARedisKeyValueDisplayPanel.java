package com.mzyupc.aredis.view;

import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.action.AddAction;
import com.mzyupc.aredis.action.ClearAction;
import com.mzyupc.aredis.action.DeleteAction;
import com.mzyupc.aredis.action.RefreshAction;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import org.apache.commons.collections.CollectionUtils;
import redis.clients.jedis.ScanParams;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import static com.mzyupc.aredis.utils.JTreeUtil.getTreeExpander;

/**
 * @author mzyupc@163.com
 * <p>
 * key-value展示
 */
public class ARedisKeyValueDisplayPanel extends JPanel {
    private static final String DEFAULT_FILTER = "*";
    private static final String DEFAULT_GROUP_SYMBOL = ":";
    private static final Integer DEFAULT_PAGE_SIZE = 500;

    private JPanel formPanel;
    private JBSplitter splitterContainer;
    /**
     * key展示区域 包括key工具栏区域, key树状图区域
     */
    private JPanel keyDisplayPanel;

    private JPanel valueDisplayPanel;
    private JPanel valuePreviewPanel;
    private JPanel valueFunctionPanel;
    private JPanel valueViewPanel;

    private JPanel keyToolBarPanel;
    private ActionToolbar actionToolbar;

    private ConnectionInfo connectionInfo;
    private DbInfo dbInfo;
    private RedisPoolMgr redisPoolMgr;

    private Tree keyTree;
    private ScrollPane keyTreeScrollPane;
    private DefaultTreeModel treeModel;
    /**
     * 用来给Key分组的符号
     */
    private String groupSymbol = DEFAULT_GROUP_SYMBOL;

    /**
     * key过滤表达式
     */
    private String keyFilter = DEFAULT_FILTER;
    /**
     * 每次查询KEY的数量
     */
    private Integer pageSize = DEFAULT_PAGE_SIZE;
    /**
     * redis scan 初始游标
     */
    private String cursor = ScanParams.SCAN_POINTER_START;


    public ARedisKeyValueDisplayPanel(ConnectionInfo connectionInfo, DbInfo dbInfo, RedisPoolMgr redisPoolMgr) {
        this.connectionInfo = connectionInfo;
        this.dbInfo = dbInfo;
        this.redisPoolMgr = redisPoolMgr;

        initPanel();
    }

    private void initPanel() {
        initKeyToolBarPanel();
        initKeyTreePanel();


    }

    /**
     * 初始化Key工具栏
     */
    private void initKeyToolBarPanel() {
        // toolbar
        final CommonActionsManager actionManager = CommonActionsManager.getInstance();
        final DefaultActionGroup actions = new DefaultActionGroup();

        // 刷新
        actions.add(createRefreshAction());
        // 增加key
        actions.add(createAddAction());
        // 删除key
        actions.add(createDeleteAction());
        // 清空key
        actions.add(createClearAction());
        // 展开, 折叠
        actions.addSeparator();
        actions.add(actionManager.createExpandAllAction(getTreeExpander(keyTree), keyTree));
        actions.add(actionManager.createCollapseAllAction(getTreeExpander(keyTree), keyTree));
        actionToolbar = ActionManager.getInstance()
                .createActionToolbar(ActionPlaces.TOOLBAR, actions, true);
        actionToolbar.setTargetComponent(keyDisplayPanel);


        JPanel toolbar1 = new JPanel();
        toolbar1.setLayout(new FlowLayout(FlowLayout.LEFT));
        // key过滤器
        JPanel searchTextField = createSearchBox();
        toolbar1.add(searchTextField);

        // key分组器
        JPanel groupTextField = createGroupByPanel();
        toolbar1.add(groupTextField);

        keyToolBarPanel = new JPanel(new VFlowLayout());
        keyToolBarPanel.add(actionToolbar.getComponent());
        keyToolBarPanel.add(toolbar1);

        keyDisplayPanel.add(keyToolBarPanel, BorderLayout.NORTH);
    }

    private void initKeyTreePanel() {
        // todo
        renderKeyTree();
//        ScrollPane scrollPane = new ScrollPane();
//        scrollPane.add(keyTree);
//        keyDisplayPanel.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * todo 渲染keyTree
     */
    private void renderKeyTree() {

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        root.setUserObject(dbInfo);
        treeModel = new DefaultTreeModel(root);

        // redis 查询前pageSize个key
        List<String> scan = redisPoolMgr.scan(cursor, keyFilter, pageSize, dbInfo.getIndex());
        if (!CollectionUtils.isEmpty(scan)) {
            for (String key : scan) {
                DefaultMutableTreeNode keyNode = new DefaultMutableTreeNode(key);
                root.add(keyNode);
            }
        }
        keyTree.setModel(treeModel);
        keyTree.setBorder(new LineBorder(JBColor.RED, 1));
        keyTree.setScrollsOnExpand(true);
        treeModel.reload();
        keyTreeScrollPane.removeAll();
        keyTreeScrollPane.add(keyTree);
        keyDisplayPanel.add(keyTreeScrollPane, BorderLayout.CENTER);
    }

    private ClearAction createClearAction() {
        ClearAction clearAction = new ClearAction();
        clearAction.setAction(e -> {
            // todo
            System.out.println("clear action performed");
        });
        return clearAction;
    }

    private AddAction createAddAction() {
        AddAction addAction = new AddAction();
        addAction.setAction(e -> {
            // todo
            System.out.println("add action performed");
        });
        return addAction;
    }

    private DeleteAction createDeleteAction() {
        DeleteAction deleteAction = new DeleteAction();
        deleteAction.setAction(e -> {
            // todo
            System.out.println("delete action performed");
        });
        return deleteAction;
    }

    private RefreshAction createRefreshAction() {
        RefreshAction refreshAction = new RefreshAction();
        refreshAction.setAction(e -> {
            // todo
            System.out.println("refresh action performed");
        });
        return refreshAction;
    }

    /**
     * 创建一个Key搜索框
     *
     * @return
     */
    private JPanel createSearchBox() {
        JPanel searchBoxPanel = new JPanel();

        SearchTextField searchTextField = new SearchTextField();
        searchTextField.setText(DEFAULT_FILTER);
        searchTextField.addKeyboardListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 调用搜索功能
                    // todo 根据输入的key, 重新渲染keyTree
                    keyFilter = searchTextField.getText();
                    renderKeyTree();
                    searchTextField.addCurrentTextToHistory();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

        });
        searchBoxPanel.add(new Label("Filter:"));
        searchBoxPanel.add(searchTextField);
        return searchBoxPanel;
    }

    private JPanel createGroupByPanel() {
        JPanel groupByPanel = new JPanel();
        groupByPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

        JTextField groupText = new JTextField(DEFAULT_GROUP_SYMBOL);
        groupText.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    // 调用分组功能
                    // todo 根据输入的key, 重新渲染keyTree
                    groupSymbol = groupText.getText();
                    renderKeyTree();
                    System.out.println("enter pressed");
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        groupByPanel.add(new Label("Group by:"));
        groupByPanel.add(groupText);
        return groupByPanel;
    }

    private void createUIComponents() {
        formPanel = new JPanel(new BorderLayout());
        this.setLayout(new BorderLayout());
        this.add(formPanel);

        keyDisplayPanel = new JPanel(new BorderLayout());
        keyDisplayPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        keyDisplayPanel.setMinimumSize(new Dimension(200, 500));

        keyTree = new Tree();

        keyTreeScrollPane = new ScrollPane();

        valueDisplayPanel = new JPanel();

        splitterContainer = new JBSplitter(false, 0.3f);
        formPanel.add(splitterContainer, BorderLayout.CENTER);

        splitterContainer.setFirstComponent(keyDisplayPanel);
        splitterContainer.setSecondComponent(valueDisplayPanel);
        splitterContainer.setDoubleBuffered(true);
        splitterContainer.setDividerWidth(2);
        splitterContainer.setShowDividerControls(true);
        splitterContainer.setDividerPositionStrategy(Splitter.DividerPositionStrategy.KEEP_FIRST_SIZE);
        splitterContainer.setSplitterProportionKey("aRedis.keyValue.splitter");

    }
}
