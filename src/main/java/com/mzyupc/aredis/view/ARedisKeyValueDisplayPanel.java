package com.mzyupc.aredis.view;

import com.alibaba.fastjson.JSON;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.action.AddAction;
import com.mzyupc.aredis.action.ClearAction;
import com.mzyupc.aredis.action.DeleteAction;
import com.mzyupc.aredis.action.RefreshAction;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.view.dialog.NewKeyDialog;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.KeyInfo;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;
import static com.mzyupc.aredis.utils.JTreeUtil.getTreeExpander;

/**
 * @author mzyupc@163.com
 * <p>
 * key-value展示
 */
public class ARedisKeyValueDisplayPanel extends JPanel implements Disposable {
    private static final String DEFAULT_FILTER = "*";
    private static final String DEFAULT_GROUP_SYMBOL = ":";
    private static final Integer DEFAULT_PAGE_SIZE = 500;
    private static final String REMOVED_KEY_PREFIX = "(Removed) ";


    private Project project;
    private JPanel formPanel;
    private JBSplitter splitterContainer;
    /**
     * key展示区域 包括key工具栏区域, key树状图区域
     */
    private JPanel keyDisplayPanel;
    private LoadingDecorator keyDisplayLoadingDecorator;

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
    private JBScrollPane keyTreeScrollPane;
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


    public ARedisKeyValueDisplayPanel(Project project, ConnectionInfo connectionInfo, DbInfo dbInfo, RedisPoolMgr redisPoolMgr) {
        this.project = project;
        this.connectionInfo = connectionInfo;
        this.dbInfo = dbInfo;
        this.redisPoolMgr = redisPoolMgr;

        initPanel();
    }

    private void initPanel() {
        ApplicationManager.getApplication().invokeLater(this::initKeyToolBarPanel);
        ApplicationManager.getApplication().invokeLater(this::initKeyTreePanel);
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
        actions.addSeparator();
        actions.add(createClearAction());
        // 展开, 折叠
        actions.addSeparator();
        actions.add(actionManager.createExpandAllAction(getTreeExpander(keyTree), keyTree));
        actions.add(actionManager.createCollapseAllAction(getTreeExpander(keyTree), keyTree));
        actionToolbar = ActionManager.getInstance()
                .createActionToolbar(ActionPlaces.TOOLBAR, actions, true);
        actionToolbar.setTargetComponent(keyDisplayPanel);

        // key过滤器
        JPanel searchTextField = createSearchBox();

        // key分组器
        JPanel groupTextField = createGroupByPanel();

        keyToolBarPanel.add(searchTextField);
        keyToolBarPanel.add(groupTextField);
        keyToolBarPanel.add(actionToolbar.getComponent());
    }

    private void initKeyTreePanel() {
        renderKeyTree();
    }

    /**
     * todo 渲染keyTree
     */
    @SneakyThrows
    private void renderKeyTree() {
        if (dbInfo.getKeyCount() > 10000) {
            keyDisplayLoadingDecorator.startLoading(true);
        } else {
            keyDisplayLoadingDecorator.startLoading(false);
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            Long dbSize = redisPoolMgr.dbSize(dbInfo.getIndex());
            dbInfo.setKeyCount(dbSize);
            root.setUserObject(dbInfo);
            treeModel = new DefaultTreeModel(root);

            // redis 查询前pageSize个key
            // todo
            List<String> scan = redisPoolMgr.scan(cursor, keyFilter, pageSize, dbInfo.getIndex());
            if (!CollectionUtils.isEmpty(scan)) {
                for (String key : scan) {
                    DefaultMutableTreeNode keyNode = new DefaultMutableTreeNode(KeyInfo.builder()
                            .key(key)
                            .del(false)
                            .build());
                    root.add(keyNode);
                }
            }

            keyTree.setModel(treeModel);
            keyTree.setScrollsOnExpand(true);
            keyTreeScrollPane = new JBScrollPane(keyTree);
            keyDisplayPanel.removeAll();
            keyDisplayPanel.add(keyTreeScrollPane, BorderLayout.CENTER);
            keyDisplayPanel.updateUI();
        });

        keyDisplayLoadingDecorator.stopLoading();
    }

    /**
     * 清空Key
     *
     * @return
     */
    private ClearAction createClearAction() {
        ClearAction clearAction = new ClearAction();
        clearAction.setAction(e -> {
            // todo
            ConfirmDialog confirmDialog = new ConfirmDialog(
                    project,
                    "Confirm",
                    "Are you sure you want to delete all the keys of the currently selected DB?",
                    actionEvent -> {
                        try (Jedis jedis = redisPoolMgr.getJedis(dbInfo.getIndex())) {
                            jedis.flushDB();
                        }
                        renderKeyTree();
                    });
            confirmDialog.show();
        });
        return clearAction;
    }

    /**
     * 添加key
     *
     * @return
     */
    private AddAction createAddAction() {
        AddAction addAction = new AddAction();
        addAction.setAction(e -> {
            NewKeyDialog newKeyDialog = new NewKeyDialog(project);
            newKeyDialog.setCustomOkAction(actionEvent -> {
                try {
                    String key = newKeyDialog.getKeyTextField().getText();
                    if (StringUtils.isEmpty(key)) {
                        ErrorDialog.show("Key can not be empty");
                    }
                    String valueString;
                    // 判断数据类型, 并存入
                    switch (newKeyDialog.getSelectedType()) {
                        case String:
                            valueString = newKeyDialog.getStringValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            }
                            redisPoolMgr.set(key, valueString, 0, dbInfo.getIndex());
                            break;

                        case List:
                            valueString = newKeyDialog.getListValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            }
                            try {
                                List<String> strings = JSON.parseArray(valueString, String.class);
                                redisPoolMgr.lpush(key, strings.toArray(new String[]{}), dbInfo.getIndex());
                            } catch (Exception exception) {
                                redisPoolMgr.lpush(key, new String[]{valueString}, dbInfo.getIndex());
                            }
                            break;

                        case Set:
                            valueString = newKeyDialog.getSetValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            }
                            try {
                                List<String> strings = JSON.parseArray(valueString, String.class);
                                redisPoolMgr.sadd(key, dbInfo.getIndex(), strings.toArray(new String[]{}));
                            } catch (Exception exception) {
                                redisPoolMgr.sadd(key, dbInfo.getIndex(), valueString);
                            }
                            break;

                        case Zset:
                            valueString = newKeyDialog.getZsetValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            }
                            String score = newKeyDialog.getScoreTextField().getText();
                            try (Jedis jedis = redisPoolMgr.getJedis(dbInfo.getIndex())) {
                                jedis.zadd(key, Double.parseDouble(score), valueString);
                            }
                            break;

                        default:
                            // Hash
                            valueString = newKeyDialog.getHashValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            }
                            String field = newKeyDialog.getFieldTextField().getText();
                            if (StringUtils.isEmpty(field)) {
                                ErrorDialog.show("Field can not be empty");
                            }
                            redisPoolMgr.hset(key, field, valueString, dbInfo.getIndex());
                    }
                    // 关闭对话框
                    newKeyDialog.close(OK_EXIT_CODE);
                    // 重新渲染keyTree
                    renderKeyTree();
                } catch (Exception exp) {
                    ErrorDialog.show(exp.getMessage() + "");
                }
            });
            newKeyDialog.show();
        });
        return addAction;
    }

    /**
     * 删除key
     *
     * @return
     */
    private DeleteAction createDeleteAction() {
        DeleteAction deleteAction = new DeleteAction();
        deleteAction.setAction(e -> {
            TreePath[] selectionPaths = keyTree.getSelectionPaths();
            // 根节点
            if (selectionPaths != null && selectionPaths.length == 1 && selectionPaths[0].getPathCount() == 1) {
                return;
            }
            ConfirmDialog confirmDialog = new ConfirmDialog(
                    project,
                    "Confirm",
                    "Are you sure you want to delete these keys?",
                    actionEvent -> {
                        // 删除选中的key, 如果选中的是上层
                        if (selectionPaths != null) {
                            for (TreePath selectionPath : selectionPaths) {
                                if (selectionPath.getPathCount() > 1) {
                                    DefaultMutableTreeNode selectNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                                    List<String> keys = new ArrayList<>();
                                    findDeleteKeys(selectNode, keys);
                                    if (CollectionUtils.isNotEmpty(keys)) {
                                        try (Jedis jedis = redisPoolMgr.getJedis(dbInfo.getIndex())) {
                                            jedis.del(keys.toArray(new String[]{}));
                                            dbInfo.setKeyCount(dbInfo.getKeyCount() - keys.size());
                                        }
                                    }
                                }
                            }
                            treeModel.reload();
                        }
                    });
            confirmDialog.show();
        });
        return deleteAction;
    }


    private void findDeleteKeys(DefaultMutableTreeNode treeNode, List<String> keys) {
        if (treeNode.isLeaf()) {
            KeyInfo keyInfo = (KeyInfo) treeNode.getUserObject();
            if (!keyInfo.isDel()) {
                keyInfo.setDel(true);
                treeNode.setUserObject(keyInfo);
                keys.add(keyInfo.getKey());
            }
        } else {
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
                findDeleteKeys(child, keys);
            }
        }
    }

    /**
     * 重新加载keyTree
     *
     * @return
     */
    private RefreshAction createRefreshAction() {
        RefreshAction refreshAction = new RefreshAction();
        refreshAction.setAction(e -> {
            renderKeyTree();
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
                    if (StringUtils.isEmpty(keyFilter)) {
                        keyFilter = DEFAULT_FILTER;
                        searchTextField.setText(keyFilter);
                    } else {
                        searchTextField.addCurrentTextToHistory();
                    }

                    renderKeyTree();
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

        JBTextField groupText = new JBTextField(DEFAULT_GROUP_SYMBOL);
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
                    if (StringUtils.isEmpty(groupSymbol)) {
                        groupSymbol = DEFAULT_GROUP_SYMBOL;
                        groupText.setText(groupSymbol);
                    }
                    renderKeyTree();

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

        keyToolBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        keyTree = new Tree();

        keyDisplayPanel = new JPanel(new BorderLayout());
        keyDisplayPanel.setMinimumSize(new Dimension(100, 100));

        keyDisplayLoadingDecorator = new LoadingDecorator(keyDisplayPanel, this, 0);

        valueDisplayPanel = new JPanel();
        valueDisplayPanel.setMinimumSize(new Dimension(100, 100));

        splitterContainer = new JBSplitter(false, 0.35f);
        splitterContainer.setFirstComponent(keyDisplayLoadingDecorator.getComponent());
        splitterContainer.setSecondComponent(valueDisplayPanel);
        splitterContainer.setDividerWidth(2);
        splitterContainer.setShowDividerControls(true);
        splitterContainer.setSplitterProportionKey("aRedis.keyValue.splitter");

        formPanel.add(keyToolBarPanel, BorderLayout.NORTH);
        formPanel.add(splitterContainer, BorderLayout.CENTER);
    }

    @Override
    public void dispose() {

    }
}
