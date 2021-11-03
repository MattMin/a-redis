package com.mzyupc.aredis.view;

import com.google.common.collect.Sets;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.action.*;
import com.mzyupc.aredis.utils.JTreeUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolManager;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ConnectionSettingsDialog;
import com.mzyupc.aredis.view.dialog.InfoDialog;
import com.mzyupc.aredis.view.editor.ConsoleFileSystem;
import com.mzyupc.aredis.view.editor.ConsoleVirtualFile;
import com.mzyupc.aredis.view.editor.KeyValueDisplayFileSystem;
import com.mzyupc.aredis.view.editor.KeyValueDisplayVirtualFile;
import com.mzyupc.aredis.view.render.ConnectionTreeCellRenderer;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.mzyupc.aredis.utils.JTreeUtil.expandTree;

/**
 * @author mzyupc@163.com
 */
public class ConnectionManager {
    /**
     * connectionId-redisPoolManager
     */
    private Map<String, RedisPoolManager> connectionRedisMap = new HashMap<>();

    private Project project;

    private PropertyUtil propertyUtil;

    /**
     * connectionId-editor
     */
    private Map<String, CopyOnWriteArraySet<KeyValueDisplayVirtualFile>> connectionDbEditorMap = new HashMap<>();

    private DefaultMutableTreeNode connectionTreeRoot = new DefaultMutableTreeNode();

    private DefaultTreeModel connectionTreeModel = new DefaultTreeModel(connectionTreeRoot);

    private LoadingDecorator connectionTreeLoadingDecorator;

    public ConnectionManager(Project project) {
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
    }

    /**
     * 初始化连接
     */
    public void initConnections(Tree connectionTree) {
        List<ConnectionInfo> connections = propertyUtil.getConnections();
        for (ConnectionInfo connection : connections) {
            if (connection != null && StringUtils.isNotEmpty(connection.getId())) {
                addConnectionToList(connectionTreeModel, connection);
            }
        }

        connectionTree.setModel(connectionTreeModel);
        connectionTree.setRootVisible(false);
    }

    /**
     * 创建连接列表
     *
     * @return
     */
    public Tree createConnectionTree(ARedisToolWindow parent, JPanel connectionPanel) {
        Tree connectionTree = new Tree();
        connectionTreeLoadingDecorator = new LoadingDecorator(new JBScrollPane(connectionTree), parent, 0);
        connectionPanel.add(connectionTreeLoadingDecorator.getComponent(), BorderLayout.CENTER);

        connectionTree.setCellRenderer(new ConnectionTreeCellRenderer());
        connectionTree.setAlignmentX(Component.LEFT_ALIGNMENT);
        ConnectionManager connectionManager = this;
        connectionTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                int x = e.getX();
                int y = e.getY();

                // connectionTree的双击事件
                if (e.getClickCount() == 2) {

                    // 第一个选中的节点路径
                    TreePath selectionPath = connectionTree.getSelectionPath();
                    if (selectionPath == null) {
                        return;
                    }

                    Object[] path = selectionPath.getPath();
                    // 双击connection事件
                    if (path.length == 2) {
                        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
                        if (connectionNode.getChildCount() == 0) {
                            // 添加DB子节点
                            connectionTreeLoadingDecorator.startLoading(false);
                            ApplicationManager.getApplication().invokeLater(() -> {
                                addDbs2Connection(connectionNode);
                                // 刷新节点
                                connectionTreeModel.reload(connectionNode);
                                // 展开当前连接
                                JTreeUtil.expandAll(connectionTree, new TreePath(new Object[]{connectionTreeRoot, connectionNode}), true);
                            });
                        }
                    }

                    // 双击DB事件
                    if (path.length == 3) {
                        // 打开编辑器
                        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
                        DefaultMutableTreeNode dbNode = (DefaultMutableTreeNode) path[2];

                        ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
                        DbInfo dbInfo = (DbInfo) dbNode.getUserObject();
                        connectionTreeLoadingDecorator.startLoading(false);

                        ApplicationManager.getApplication().invokeLater(() -> {
                            String connectionId = connectionInfo.getId();
                            KeyValueDisplayVirtualFile keyValueDisplayVirtualFile = new KeyValueDisplayVirtualFile(connectionInfo.getName() + "-DB" + dbInfo.getIndex(),
                                    project,
                                    connectionInfo,
                                    dbInfo,
                                    connectionRedisMap.get(connectionId),
                                    connectionManager);
                            KeyValueDisplayFileSystem.getInstance(project).openEditor(keyValueDisplayVirtualFile);
                            addEditorToMap(connectionId, keyValueDisplayVirtualFile);
                        });
                    }
                    connectionTreeLoadingDecorator.stopLoading();
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 获取右键点击所在connectionNodede路径
                    TreePath pathForLocation = connectionTree.getSelectionPath();
                    if (pathForLocation != null && pathForLocation.getPathCount() == 2) {
                        createConnectionPopupMenu(connectionTree, connectionTreeModel, connectionTreeLoadingDecorator).getComponent().show(connectionTree, x, y);
                    }
                }
            }
        });
        return connectionTree;
    }

    /**
     * 连接树工具栏
     *
     * @param connectionTree
     * @param connectionPanel
     * @return
     */
    public ActionToolbar createConnectionActionToolbar(Tree connectionTree, JComponent connectionPanel) {
        // 工具栏
        CommonActionsManager actionManager = CommonActionsManager.getInstance();
        DefaultActionGroup actions = new DefaultActionGroup();
        // 增加key
        actions.add(createAddAction(connectionTree));
        // 删除key
        actions.add(createDeleteAction(connectionTree));
        actions.addSeparator();
        // 展开所有
        actions.add(actionManager.createExpandAllAction(getTreeExpander(connectionTree), connectionTree));
        // 折叠所有
        actions.add(actionManager.createCollapseAllAction(getTreeExpander(connectionTree), connectionTree));
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("ToolwindowToolbar", actions, true);
        actionToolbar.setTargetComponent(connectionPanel);
        actionToolbar.adjustTheSameSize(true);
        return actionToolbar;
    }

    private static TreeExpander getTreeExpander(JTree tree) {
        return new TreeExpander() {
            @Override
            public void expandAll() {
                expandTree(tree, true);
            }

            @Override
            public boolean canExpand() {
                return true;
            }

            @Override
            public void collapseAll() {
                DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
                for (int i = 0; i < root.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
                    JTreeUtil.expandAll(tree, new TreePath(child.getPath()), false);
                }
            }

            @Override
            public boolean canCollapse() {
                return true;
            }

        };
    }

    /**
     * 添加DB子节点到connection节点下面
     *
     * @param connectionNode
     */
    private void addDbs2Connection(DefaultMutableTreeNode connectionNode) {
        ConnectionInfo connection = (ConnectionInfo) connectionNode.getUserObject();
        if (connection == null) {
            return;
        }

        RedisPoolManager redisPoolManager = connectionRedisMap.get(connection.getId());
        int dbCount = redisPoolManager.getDbCount();
        propertyUtil.setDbCount(connection.getId(), dbCount);

        // 移除原有节点
        connectionNode.removeAllChildren();
        // 添加新节点
        for (int i = 0; i < dbCount; i++) {
            Long keyCount = redisPoolManager.dbSize(i);
            DbInfo dbInfo = DbInfo.builder()
                    .index(i)
                    .keyCount(keyCount)
                    .connectionId(connection.getId())
                    .build();
            connectionNode.add(new DefaultMutableTreeNode(dbInfo, false));
        }
    }

    /**
     * 添加连接到connectionPanel
     *
     * @param treeModel
     * @param connection
     */
    public void addConnectionToList(DefaultTreeModel treeModel, ConnectionInfo connection) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.add(new DefaultMutableTreeNode(connection));

        // 保存这个连接的redisPool
        RedisPoolManager poolMgr = new RedisPoolManager(connection);
        connectionRedisMap.put(connection.getId(), poolMgr);

        treeModel.reload();
    }

    /**
     * 移除选中的连接
     *
     * @param connectionTree
     */
    public void removeConnectionFromTree(Tree connectionTree) {
        // 从connectionTree移除元素
        java.util.List<ConnectionInfo> connectionInfoList = getSelectedConnectionAndRemove(connectionTree);
        if (CollectionUtils.isEmpty(connectionInfoList)) {
            return;
        }

        for (ConnectionInfo connectionInfo : connectionInfoList) {
            // 关闭redis连接池
            String connectionInfoId = connectionInfo.getId();
            RedisPoolManager redisPoolManager = connectionRedisMap.get(connectionInfoId);

            // 从connectionRedisMap中移除
            connectionRedisMap.remove(connectionInfoId);

            // 从properties中移除
            propertyUtil.removeConnection(connectionInfoId, redisPoolManager);

            // 查询过DB才有
            if (redisPoolManager != null) {
                redisPoolManager.invalidate();
            }

            // 关闭所有KeyValueDisplayPanel
            closeAllEditor(connectionInfoId);
        }
    }


    /**
     * 查询connectionPanel中选中的connectionInfo, 并删除选中的tree
     *
     * @param connectionTree
     * @return
     */
    @Nullable
    private java.util.List<ConnectionInfo> getSelectedConnectionAndRemove(Tree connectionTree) {
        // 查询选中的 connection
        DefaultTreeModel connectionTreeModel = (DefaultTreeModel) connectionTree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) connectionTreeModel.getRoot();

        TreePath[] selectionPaths = connectionTree.getSelectionPaths();
        if (selectionPaths == null || selectionPaths.length == 0) {
            return null;
        }

        List<ConnectionInfo> result = new ArrayList<>();
        for (TreePath selectionPath : selectionPaths) {
            Object[] path = selectionPath.getPath();
            if (path.length != 2) {
                continue;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
            result.add((ConnectionInfo) connectionNode.getUserObject());
            root.remove(connectionNode);
        }

        if (!result.isEmpty()) {
            connectionTreeModel.reload();
        }
        return result;
    }

    /**
     * 复制选中的连接
     *
     * @param connectionTree
     * @return
     */
    private ConnectionInfo duplicateConnections(Tree connectionTree) {
        TreePath selectionPath = connectionTree.getSelectionPath();
        if (selectionPath == null || selectionPath.getPathCount() != 2) {
            return null;
        }
        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
        ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
        ConnectionInfo newConnectionInfo = ConnectionInfo.builder()
                .id(UUID.randomUUID().toString())
                .name(connectionInfo.getName() + "_copy")
                .url(connectionInfo.getUrl())
                .port(connectionInfo.getPort())
                .password(connectionInfo.getPassword())
                .build();
        addConnectionToList((DefaultTreeModel) connectionTree.getModel(), newConnectionInfo);
        return newConnectionInfo;
    }

    /**
     * 关闭tab时移除connection-editor
     */
    public void removeEditor(String connectionId, KeyValueDisplayVirtualFile virtualFile) {
        CopyOnWriteArraySet<KeyValueDisplayVirtualFile> keyValueDisplayVirtualFiles = connectionDbEditorMap.get(connectionId);
        if (CollectionUtils.isEmpty(keyValueDisplayVirtualFiles)) {
            return;
        }
        keyValueDisplayVirtualFiles.remove(virtualFile);
    }

    /**
     * 关闭connection时关闭所有打开的editor
     *
     * @param connectionId
     */
    private void closeAllEditor(String connectionId) {
        CopyOnWriteArraySet<KeyValueDisplayVirtualFile> keyValueDisplayVirtualFiles = connectionDbEditorMap.get(connectionId);
        if (CollectionUtils.isEmpty(keyValueDisplayVirtualFiles)) {
            return;
        }

        Iterator<KeyValueDisplayVirtualFile> iterator = keyValueDisplayVirtualFiles.iterator();
        while (iterator.hasNext()) {
            KeyValueDisplayVirtualFile keyValueDisplayVirtualFile = iterator.next();
            // close editor
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            fileEditorManager.closeFile(keyValueDisplayVirtualFile);
        }

        connectionDbEditorMap.remove(connectionId);
    }

    /**
     * 添加一个connection-editor映射
     *
     * @param connectionId
     * @param virtualFile
     */
    private void addEditorToMap(String connectionId, KeyValueDisplayVirtualFile virtualFile) {
        CopyOnWriteArraySet<KeyValueDisplayVirtualFile> keyValueDisplayVirtualFiles = connectionDbEditorMap.get(connectionId);
        if (keyValueDisplayVirtualFiles == null) {
            keyValueDisplayVirtualFiles = Sets.newCopyOnWriteArraySet();
            keyValueDisplayVirtualFiles.add(virtualFile);
            connectionDbEditorMap.put(connectionId, keyValueDisplayVirtualFiles);
        } else {
            keyValueDisplayVirtualFiles.add(virtualFile);
        }
    }

    /**
     * 创建添加按钮
     *
     * @return
     */
    private AddAction createAddAction(Tree connectionTree) {
        AddAction addAction = new AddAction();
        addAction.setAction(e -> {
            // 弹出连接配置窗口
            ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, null, connectionTree, this);
            connectionSettingsDialog.show();
        });
        return addAction;
    }

    /**
     * 创建移除按钮
     *
     * @return
     */
    private DeleteAction createDeleteAction(Tree connectionTree) {
        DeleteAction deleteAction = new DeleteAction();
        deleteAction.setAction(e -> {
            // 弹出删除确认对话框
            ConfirmDialog removeConnectionDialog = new ConfirmDialog(
                    project,
                    "Confirm",
                    "Are you sure you want to delete these connections?",
                    actionEvent -> {
                        // connection列表中移除
                        removeConnectionFromTree(connectionTree);
                    });
            removeConnectionDialog.show();
        });
        return deleteAction;
    }

    private RefreshAction createRefreshAction(Tree connectionTree, DefaultTreeModel connectionTreeModel, LoadingDecorator connectionTreeLoadingDecorator) {
        RefreshAction refreshAction = new RefreshAction();
        refreshAction.setAction(e -> {
            connectionTreeLoadingDecorator.startLoading(false);
            // reload server function
            TreePath[] selectionPaths = connectionTree.getSelectionPaths();
            if (selectionPaths == null) {
                return;
            }

            for (TreePath selectionPath : selectionPaths) {
                if (selectionPath.getPathCount() != 2) {
                    continue;
                }

                DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
                ApplicationManager.getApplication().invokeLater(() -> {
                    addDbs2Connection(connectionNode);
                    connectionTreeModel.reload(connectionNode);
                });
            }
            connectionTreeLoadingDecorator.stopLoading();
        });
        return refreshAction;
    }

    private EditAction createEditAction(Tree connectionTree) {
        EditAction editAction = new EditAction();
        editAction.setAction(e -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
            // 弹出连接配置窗口
            ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, connectionInfo.getId(), connectionTree, this);
            connectionSettingsDialog.show();
        });
        return editAction;
    }

    private DuplicateAction createDuplicateAction(Tree connectionTree) {
        DuplicateAction duplicateAction = new DuplicateAction();
        duplicateAction.setAction(e -> {
            // 复制按钮功能
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            ConnectionInfo newConnectionInfo = duplicateConnections(connectionTree);
            propertyUtil.saveConnection(newConnectionInfo);
        });
        return duplicateAction;
    }

    private ConsoleAction createConsoleAction(Tree connectionTree) {
        ConsoleAction consoleAction = new ConsoleAction();
        consoleAction.setAction(e -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();

            // test connection
            RedisPoolManager redis = getConnectionRedisMap().get(connectionInfo.getId());
            try (Jedis jedis = redis.getJedis(0)) {
                if (jedis == null) {
                    return;
                }
            }

            // console
            ConsoleVirtualFile consoleVirtualFile = new ConsoleVirtualFile(
                    connectionInfo.getName() + "-Console",
                    project,
                    connectionInfo,
                    connectionRedisMap.get(connectionInfo.getId())
            );
            ConsoleFileSystem.getInstance(project).openEditor(consoleVirtualFile);
        });
        return consoleAction;
    }

    private InfoAction createInfoAction(Tree connectionTree) {
        InfoAction infoAction = new InfoAction();
        infoAction.setAction(anActionEvent -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
            RedisPoolManager redis = getConnectionRedisMap().get(connectionInfo.getId());
            new InfoDialog(project, redis).show();
        });
        return infoAction;
    }

    private CloseAction createCloseAction(Tree connectionTree, DefaultTreeModel connectionTreeModel) {
        CloseAction closeAction = new CloseAction();
        closeAction.setAction(e -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();

            // 关闭连接池
            String connectionId = connectionInfo.getId();
            RedisPoolManager redisPoolManager = connectionRedisMap.get(connectionId);
            if (redisPoolManager != null) {
                redisPoolManager.invalidate();
            }

            // 关闭所有editor
            closeAllEditor(connectionId);

            // 清除connectionNode子节点
            connectionNode.removeAllChildren();
            connectionTreeModel.reload(connectionNode);
        });
        return closeAction;
    }

    /**
     * 创建一个连接右键菜单
     *
     * @return
     */
    private ActionPopupMenu createConnectionPopupMenu(Tree connectionTree, DefaultTreeModel connectionTreeModel, LoadingDecorator connectionTreeLoadingDecorator) {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(createRefreshAction(connectionTree, connectionTreeModel, connectionTreeLoadingDecorator));
        actionGroup.addSeparator();
        actionGroup.add(createEditAction(connectionTree));
        actionGroup.add(createDuplicateAction(connectionTree));
        actionGroup.add(createDeleteAction(connectionTree));
        actionGroup.addSeparator();
        actionGroup.add(createConsoleAction(connectionTree));
        actionGroup.add(createInfoAction(connectionTree));
        actionGroup.addSeparator();
        actionGroup.add(createCloseAction(connectionTree, connectionTreeModel));
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup);
        menu.setTargetComponent(connectionTree);
        return menu;
    }

    public void dispose() {
        for (RedisPoolManager redisPoolManager : connectionRedisMap.values()) {
            if (redisPoolManager != null) {
                redisPoolManager.invalidate();
            }
        }
    }

    public Map<String, RedisPoolManager> getConnectionRedisMap() {
        return connectionRedisMap;
    }

}
