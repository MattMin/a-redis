package com.mzyupc.aredis.view;

import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.JBUI;
import com.mzyupc.aredis.action.AddAction;
import com.mzyupc.aredis.action.ClearAction;
import com.mzyupc.aredis.action.DeleteAction;
import com.mzyupc.aredis.action.RefreshAction;
import com.mzyupc.aredis.utils.JSON;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.view.dialog.FlushDbConfirmDialog;
import com.mzyupc.aredis.view.dialog.NewKeyDialog;
import com.mzyupc.aredis.view.render.KeyTreeCellRenderer;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.FragmentedKey;
import com.mzyupc.aredis.vo.KeyInfo;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE;
import static com.mzyupc.aredis.utils.JTreeUtil.getTreeExpander;
import static redis.clients.jedis.params.ScanParams.SCAN_POINTER_START;

/**
 * @author mzyupc@163.com
 * <p>
 * key tree 展示面板
 */
@Getter
public class KeyTreeDisplayPanel extends JPanel {

    private final Tree keyTree;
    private final LoadingDecorator keyDisplayLoadingDecorator;
    private final DbInfo dbInfo;
    private final RedisPoolManager redisPoolManager;
    private final Project project;
    private final ARedisKeyValueDisplayPanel parent;
    /**
     * 每次查询KEY的数量
     */
    private static final int DEFAULT_PAGE_SIZE = 20000;
    private DefaultTreeModel treeModel;
    private JPanel keyDisplayPanel;
    /**
     * 没有分组过的根节点
     */
    private DefaultMutableTreeNode flatRootNode;
    private JBLabel pageSizeLabel;
    private List<String> keys;

    public KeyTreeDisplayPanel(Project project,
                               ARedisKeyValueDisplayPanel parent,
                               JBSplitter splitterContainer,
                               DbInfo dbInfo,
                               RedisPoolManager redisPoolManager,
                               Consumer<KeyInfo> doubleClickKeyAction) {
        this.project = project;
        this.dbInfo = dbInfo;
        this.redisPoolManager = redisPoolManager;
        this.parent = parent;

        keys = redisPoolManager.scanKeys(SCAN_POINTER_START, parent.getKeyFilter(), DEFAULT_PAGE_SIZE, dbInfo.getIndex());
        // exception occurred
        if (keys == null) {
            throw new RuntimeException("exception occurred");
        }

        keyTree = new Tree();
        // 搜索功能
        TreeSpeedSearch.installOn(keyTree, true, new Convertor<TreePath, String>() {
            @Override
            public String convert(final TreePath treePath) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
                if (node.getUserObject() != null) {
                    return node.getUserObject().toString();
                }
                return "";
            }
        });

        // 渲染器
        keyTree.setCellRenderer(new KeyTreeCellRenderer());
        keyTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                int x = e.getX();
                int y = e.getY();

                if (e.getClickCount() == 2) {
                    // 第一个选中的节点路径
                    TreePath selectionPath = keyTree.getSelectionPath();
                    if (selectionPath == null) {
                        return;
                    }
                    Object[] path = selectionPath.getPath();
                    // 双击key事件
                    if (selectionPath.getPathCount() >= 2) {
                        DefaultMutableTreeNode lastNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                        // 双击叶子节点才处理
                        if (lastNode.isLeaf()) {
                            doubleClickKeyAction.accept((KeyInfo) lastNode.getUserObject());
                        }
                    }
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 获取右键点击所在connectionNodede路径
                    TreePath pathForLocation = keyTree.getSelectionPath();
                    if (pathForLocation != null && pathForLocation.getPathCount() > 1) {
                        createKeyTreePopupMenu().getComponent().show(keyTree, x, y);
                    }
                }
            }
        });
        JBScrollPane keyTreeScrollPane = new JBScrollPane(keyTree);
        keyDisplayLoadingDecorator = new LoadingDecorator(keyTreeScrollPane, project, 0);

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
        ActionToolbar actionToolbar = ActionManager.getInstance()
                .createActionToolbar(ActionPlaces.TOOLBAR, actions, true);

        // key分页panel
        JPanel keyPagingPanel = createPagingPanel();

        // key展示区域 包括key工具栏区域, key树状图区域, key分页区
        keyDisplayPanel = new JPanel(new BorderLayout());
        keyDisplayPanel.setMinimumSize(new Dimension(255, 100));
        actionToolbar.setTargetComponent(keyDisplayPanel);
        keyDisplayPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
        keyDisplayPanel.add(keyDisplayLoadingDecorator.getComponent(), BorderLayout.CENTER);
        keyDisplayPanel.add(keyPagingPanel, BorderLayout.SOUTH);

        splitterContainer.setFirstComponent(keyDisplayPanel);

        renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), keys);
    }

    private static void updateTree(DefaultMutableTreeNode parentTargetNode, DefaultMutableTreeNode originalChildNode, String[] explodedKey, KeyInfo key) {
        if (explodedKey.length == 0) {
            addChildren(parentTargetNode, originalChildNode);
            return;
        }
        String keyFragment = explodedKey[0];
        DefaultMutableTreeNode node = findNodeByKey(parentTargetNode, keyFragment);
        if (node == null) {
            if (explodedKey.length == 1) {
                node = new DefaultMutableTreeNode(key);
            } else {
                node = new DefaultMutableTreeNode(FragmentedKey.builder()
                        .fragmentedKey(keyFragment)
                        .build());
            }
        }
        updateTree(node, originalChildNode, Arrays.copyOfRange(explodedKey, 1, explodedKey.length), key);

        parentTargetNode.add(node);
    }

    private static DefaultMutableTreeNode findNodeByKey(DefaultMutableTreeNode parentTargetNode, String keyFragment) {
        for (int i = 0; i < parentTargetNode.getChildCount(); i++) {
            DefaultMutableTreeNode currentChild = (DefaultMutableTreeNode) parentTargetNode.getChildAt(i);
            Object keyObj = currentChild.getUserObject();
            String nodeKey;
            // 中间节点
            if (keyObj instanceof FragmentedKey fragmentedKey) {
                nodeKey = fragmentedKey.getFragmentedKey();
                if (keyFragment.equals(nodeKey)) {
                    return currentChild;
                }
            }
        }
        return null;
    }

    private static void addChildren(DefaultMutableTreeNode parentNode, DefaultMutableTreeNode originalChildNode) {
        Enumeration<TreeNode> children = originalChildNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
            parentNode.add((MutableTreeNode) childNode.clone());
        }
    }

    /**
     * key分页panel
     */
    @NotNull
    private JPanel createPagingPanel() {
        JPanel keyPagingPanel = new JPanel(new BorderLayout());
        pageSizeLabel = new JBLabel("Displayed key count: " + keys.size());
        pageSizeLabel.setBorder(JBUI.Borders.emptyLeft(5));
        keyPagingPanel.add(pageSizeLabel, BorderLayout.NORTH);
        return keyPagingPanel;
    }

    /**
     * 渲染keyTree
     */
    @SneakyThrows
    public void renderKeyTree(String keyFilter, String groupSymbol, List<String> preparedKeys) {
        keyDisplayLoadingDecorator.startLoading(false);
        ReadAction.nonBlocking(() -> {
            try {
                Long dbSize = redisPoolManager.dbSize(dbInfo.getIndex());
                if (dbSize == null) {
                    return null;
                }
                dbInfo.setKeyCount(dbSize);
                flatRootNode = new DefaultMutableTreeNode(dbInfo);

                // redis 查询前pageSize个key
                if (preparedKeys == null) {
                    keys = redisPoolManager.scanKeys(SCAN_POINTER_START, keyFilter, DEFAULT_PAGE_SIZE, dbInfo.getIndex());
                    // exception occurred
                    if (keys == null) {
                        return null;
                    }
                } else {
                    keys = preparedKeys;
                }

                if (CollectionUtils.isNotEmpty(keys)) {
                    for (String key : keys) {
                        DefaultMutableTreeNode keyNode = new DefaultMutableTreeNode(KeyInfo.builder()
                                .key(key)
                                .del(false)
                                .build());
                        flatRootNode.add(keyNode);
                    }
                }

                EventQueue.invokeLater(() -> {
                    updateKeyTree(groupSymbol);
                    updatePageLabel();
                    keyDisplayPanel.updateUI();
                });
            } finally {
                keyDisplayLoadingDecorator.stopLoading();
            }
            return null;
        }).submit(ThreadPoolManager.getExecutor());

    }

    /**
     * 根据groupSymbol更新树节点
     *
     * @param groupSymbol
     */
    public void updateKeyTree(String groupSymbol) {
        if (flatRootNode == null) {
            return;
        }

        if (StringUtils.isEmpty(groupSymbol)) {
            treeModel = new DefaultTreeModel(flatRootNode);

        } else {
            // newRoot没有children
            DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) flatRootNode.clone();
            for (int i = 0; i < flatRootNode.getChildCount(); i++) {
                DefaultMutableTreeNode originalChildNode = (DefaultMutableTreeNode) flatRootNode.getChildAt(i);
                DefaultMutableTreeNode clonedChildNode = (DefaultMutableTreeNode) originalChildNode.clone();
                KeyInfo key = (KeyInfo) clonedChildNode.getUserObject();
                String[] explodedKey = StringUtils.split(key.getKey(), groupSymbol);
                if (explodedKey.length == 1) {
                    addChildren(clonedChildNode, originalChildNode);
                    rootNode.add(clonedChildNode);
                } else {
                    updateTree(rootNode, originalChildNode, explodedKey, key);
                }
            }
            treeModel = new DefaultTreeModel(rootNode);
        }

        keyTree.setModel(treeModel);
        treeModel.reload();
    }

    /**
     * 清空Key
     *
     * @return
     */
    private ClearAction createClearAction() {
        ClearAction clearAction = new ClearAction();
        clearAction.setAction(e -> {
            new FlushDbConfirmDialog(project, actionEvent -> {
                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                    if (jedis != null) {
                        jedis.flushDB();
                    }
                }
                renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
            }).show();
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
                        return;
                    }

                    String valueString;
                    // 判断数据类型, 并存入
                    switch (newKeyDialog.getSelectedType()) {
                        case String:
                            valueString = newKeyDialog.getStringValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            } else {
                                redisPoolManager.set(key, valueString, 0, dbInfo.getIndex());
                                // 关闭对话框
                                newKeyDialog.close(OK_EXIT_CODE);
                                if (newKeyDialog.isReloadSelected()) {
                                    // 重新渲染keyTree
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
                                }
                            }
                            break;

                        case List:
                            valueString = newKeyDialog.getListValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            } else {
                                try {
                                    List<String> strings = JSON.parseArray(valueString, String.class);
                                    redisPoolManager.lpush(key, strings.toArray(new String[]{}), dbInfo.getIndex());
                                } catch (Exception exception) {
                                    redisPoolManager.lpush(key, new String[]{valueString}, dbInfo.getIndex());
                                }
                                // 关闭对话框
                                newKeyDialog.close(OK_EXIT_CODE);
                                if (newKeyDialog.isReloadSelected()) {
                                    // 重新渲染keyTree
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
                                }
                            }
                            break;

                        case Set:
                            valueString = newKeyDialog.getSetValueTextArea().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            } else {
                                try {
                                    List<String> strings = JSON.parseArray(valueString, String.class);
                                    redisPoolManager.sadd(key, dbInfo.getIndex(), strings.toArray(new String[]{}));
                                } catch (Exception exception) {
                                    redisPoolManager.sadd(key, dbInfo.getIndex(), valueString);
                                }
                                // 关闭对话框
                                newKeyDialog.close(OK_EXIT_CODE);
                                if (newKeyDialog.isReloadSelected()) {
                                    // 重新渲染keyTree
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
                                }
                            }
                            break;

                        case Zset:
                            valueString = newKeyDialog.getZsetValueTextArea().getText();
                            String score = newKeyDialog.getScoreTextField().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            } else if (StringUtils.isEmpty(score)) {
                                ErrorDialog.show("Score can not be empty");
                            } else {
                                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                                    if (jedis != null) {
                                        jedis.zadd(key, Double.parseDouble(score), valueString);
                                    }
                                }
                                // 关闭对话框
                                newKeyDialog.close(OK_EXIT_CODE);
                                if (newKeyDialog.isReloadSelected()) {
                                    // 重新渲染keyTree
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
                                }
                            }
                            break;

                        default:
                            valueString = newKeyDialog.getHashValueTextArea().getText();
                            String field = newKeyDialog.getFieldTextField().getText();
                            if (StringUtils.isEmpty(valueString)) {
                                ErrorDialog.show("Value can not be empty");
                            } else if (StringUtils.isEmpty(field)) {
                                ErrorDialog.show("Field can not be empty");
                            } else {
                                redisPoolManager.hset(key, field, valueString, dbInfo.getIndex());
                                // 关闭对话框
                                newKeyDialog.close(OK_EXIT_CODE);
                                if (newKeyDialog.isReloadSelected()) {
                                    // 重新渲染keyTree
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
                                }
                            }
                    }
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
            doDeleteKeys();
        });
        return deleteAction;
    }

    public void doDeleteKeys() {
        TreePath[] selectionPaths = keyTree.getSelectionPaths();

        // 没有选中key或者选中的是根节点
        if (null == selectionPaths || selectionPaths.length == 1 && selectionPaths[0].getPathCount() == 1) {
            ErrorDialog.show("Please select a key");
            return;
        }

        final ValueDisplayPanel valueDisplayPanel = parent.getValueDisplayPanel();
        ConfirmDialog confirmDialog = new ConfirmDialog(
                project,
                "Confirm",
                "Are you sure you want to delete this key?",
                actionEvent -> {
                    // 删除选中的key, 如果选中的是上层
                    for (TreePath selectionPath : selectionPaths) {
                        if (selectionPath.getPathCount() > 1) {
                            DefaultMutableTreeNode selectNode = (DefaultMutableTreeNode) selectionPath.getLastPathComponent();
                            List<String> keys = new ArrayList<>();
                            findDeleteKeys(selectNode, keys, valueDisplayPanel);
                            if (CollectionUtils.isNotEmpty(keys)) {
                                try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                                    if (jedis != null) {
                                        jedis.del(keys.toArray(new String[]{}));
                                        dbInfo.setKeyCount(dbInfo.getKeyCount() - keys.size());
                                    }
                                }
                            }
                        }
                    }
                });
        confirmDialog.show();
    }


    private void findDeleteKeys(DefaultMutableTreeNode treeNode, List<String> keys, ValueDisplayPanel valueDisplayPanel) {
        if (treeNode.isLeaf()) {
            KeyInfo keyInfo = (KeyInfo) treeNode.getUserObject();
            if (!keyInfo.isDel()) {
                keyInfo.setDel(true);
                treeNode.setUserObject(keyInfo);
                keys.add(keyInfo.getKey());

                // 删除key的同时关闭ValueDisplayPanel
                if (valueDisplayPanel != null) {
                    final String key = valueDisplayPanel.getKey();
                    if (keyInfo.getKey().equals(key)) {
                        parent.removeValueDisplayPanel();
                    }
                }

                treeModel.reload(treeNode);
            }
        } else {
            for (int i = 0; i < treeNode.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeNode.getChildAt(i);
                findDeleteKeys(child, keys, valueDisplayPanel);
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
            renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol(), null);
        });
        return refreshAction;
    }

    private void updatePageLabel() {
        pageSizeLabel.setText("Displayed key count: " + keys.size());
    }

    /**
     * 创建一个连接右键菜单
     *
     * @return
     */
    private ActionPopupMenu createKeyTreePopupMenu() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(createDeleteAction());
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup);
        menu.setTargetComponent(keyTree);
        return menu;
    }

}
