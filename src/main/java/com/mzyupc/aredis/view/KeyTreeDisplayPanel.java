package com.mzyupc.aredis.view;

import com.intellij.icons.AllIcons;
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
import com.mzyupc.aredis.action.AddAction;
import com.mzyupc.aredis.action.ClearAction;
import com.mzyupc.aredis.action.DeleteAction;
import com.mzyupc.aredis.action.RefreshAction;
import com.mzyupc.aredis.utils.JSON;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.utils.ThreadPoolManager;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ErrorDialog;
import com.mzyupc.aredis.view.dialog.NewKeyDialog;
import com.mzyupc.aredis.view.render.KeyTreeCellRenderer;
import com.mzyupc.aredis.vo.DbInfo;
import com.mzyupc.aredis.vo.FragmentedKey;
import com.mzyupc.aredis.vo.KeyInfo;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
    private static final int DEFAULT_PAGE_SIZE = 10000;

    private final Tree keyTree;
    private final LoadingDecorator keyDisplayLoadingDecorator;
    private final DbInfo dbInfo;
    private final RedisPoolManager redisPoolManager;
    private final Project project;
    private final ARedisKeyValueDisplayPanel parent;
    /**
     * 每次查询KEY的数量
     */
    private final int pageSize = DEFAULT_PAGE_SIZE;
    private DefaultTreeModel treeModel;
    private JPanel keyDisplayPanel;
    /**
     * 没有分组过的根节点
     */
    private DefaultMutableTreeNode flatRootNode;
    private JBLabel pageSizeLabel;
    private JBLabel pageLabel;
    private int pageIndex = 1;
    private List<String> allKeys;
    private List<String> currentPageKeys = new ArrayList<>();

    private Map<Integer, String> pageIndexScanPointerMap = new HashMap<>();

    public KeyTreeDisplayPanel(Project project, ARedisKeyValueDisplayPanel parent, JBSplitter splitterContainer, DbInfo dbInfo, RedisPoolManager redisPoolManager, Consumer<KeyInfo> doubleClickKeyAction) {
        this.project = project;
        this.dbInfo = dbInfo;
        this.redisPoolManager = redisPoolManager;
        this.parent = parent;
        pageIndexScanPointerMap.put(pageIndex, SCAN_POINTER_START);

        allKeys = Collections.emptyList();

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
        JPanel keyPagingPanel = createPagingPanel(parent);

        // key展示区域 包括key工具栏区域, key树状图区域, key分页区
        keyDisplayPanel = new JPanel(new BorderLayout());
        keyDisplayPanel.setMinimumSize(new Dimension(255, 100));
        actionToolbar.setTargetComponent(keyDisplayPanel);
        keyDisplayPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
        keyDisplayPanel.add(keyDisplayLoadingDecorator.getComponent(), BorderLayout.CENTER);
        keyDisplayPanel.add(keyPagingPanel, BorderLayout.SOUTH);

        splitterContainer.setFirstComponent(keyDisplayPanel);
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
            if (keyObj instanceof FragmentedKey) {
                nodeKey = ((FragmentedKey) keyObj).getFragmentedKey();
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
     *
     * @param parent
     * @return
     */
    @NotNull
    private JPanel createPagingPanel(ARedisKeyValueDisplayPanel parent) {
        JPanel keyPagingPanel = new JPanel(new BorderLayout());

        pageSizeLabel = new JBLabel("Page Size: " + currentPageKeys.size());
        pageSizeLabel.setBorder(new EmptyBorder(0, 5, 0, 0));
        keyPagingPanel.add(pageSizeLabel, BorderLayout.NORTH);
        JPanel pagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pageLabel = new JBLabel(String.format("Page %s of %s", pageIndex, getPageCount()));
        pagePanel.add(pageLabel);

        JButton prevPageButton = new JButton(AllIcons.Actions.Play_back);
        JButton nextPageButton = new JButton(AllIcons.Actions.Play_forward);
        pagePanel.add(prevPageButton);
        pagePanel.add(nextPageButton);
        keyPagingPanel.add(pagePanel, BorderLayout.SOUTH);

        if (pageIndex <= 1) {
            prevPageButton.setEnabled(false);
        }
        prevPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pageIndex <= 1) {
                    return;
                }

                updatePageDataOnPrevButtonClicked();
                renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
                nextPageButton.setEnabled(true);

                if (pageIndex <= 1) {
                    prevPageButton.setEnabled(false);
                }
            }
        });

        if (pageIndex >= getPageCount()) {
            nextPageButton.setEnabled(false);
        }
        nextPageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pageIndex >= getPageCount()) {
                    return;
                }

                updatePageDataOnNextButtonClicked();
                renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
                prevPageButton.setEnabled(true);
                if (pageIndex >= getPageCount()) {
                    nextPageButton.setEnabled(false);
                }
            }
        });
        return keyPagingPanel;
    }

    /**
     * 渲染keyTree
     * <p>
     * TODO
     * <p>
     * 渲染keyTree时判断dbSize，
     * <p>
     * - 如果大于10万则使用 Cursor 分页，需要记录前一页的Cursor和后一页的Cursor，缺点是不能按照key整体排序，有点是快
     * - 如果小于10万，使用现在的方式分页，即先查出所有key再分页
     */
    @SneakyThrows
    public void renderKeyTree(String keyFilter, String groupSymbol) {
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
                allKeys = redisPoolManager.scan(SCAN_POINTER_START, keyFilter, pageSize, dbInfo.getIndex());

                // exception occurred
                if (allKeys == null) {
                    return null;
                }

                if (CollectionUtils.isNotEmpty(allKeys)) {
                    allKeys = allKeys.stream().sorted().collect(Collectors.toList());

                    int size = allKeys.size();
                    int start = (pageIndex - 1) * pageSize;
                    int end = Math.min(start + pageSize, size);
                    currentPageKeys = allKeys.subList(start, end);
                    if (!CollectionUtils.isEmpty(currentPageKeys)) {
                        for (String key : currentPageKeys) {
                            DefaultMutableTreeNode keyNode = new DefaultMutableTreeNode(KeyInfo.builder()
                                    .key(key)
                                    .del(false)
                                    .build());
                            flatRootNode.add(keyNode);
                        }
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
            ConfirmDialog confirmDialog = new ConfirmDialog(
                    project,
                    "Confirm",
                    "Are you sure you want to delete all the keys of the currently selected DB?",
                    actionEvent -> {
                        try (Jedis jedis = redisPoolManager.getJedis(dbInfo.getIndex())) {
                            if (jedis != null) {
                                jedis.flushDB();
                            }
                        }
                        resetPageIndex();
                        renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
                                    resetPageIndex();
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
                                    resetPageIndex();
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
                                    resetPageIndex();
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
                                    resetPageIndex();
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
                                    resetPageIndex();
                                    renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
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
            renderKeyTree(parent.getKeyFilter(), parent.getGroupSymbol());
        });
        return refreshAction;
    }

    private long getPageCount() {
        int total = allKeys.size();
        long result = total / pageSize;
        long mod = total % pageSize;
        return mod > 0 ? result + 1 : result;
    }

    private synchronized void updatePageDataOnPrevButtonClicked() {
        pageIndex--;
    }

    private synchronized void updatePageDataOnNextButtonClicked() {
        pageIndex++;
    }

    public void resetPageIndex() {
        pageIndex = 1;
    }

    private void updatePageLabel() {
        pageLabel.setText(String.format("Page %s of %s", pageIndex, getPageCount()));
        pageSizeLabel.setText("Page Size: " + currentPageKeys.size());
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
