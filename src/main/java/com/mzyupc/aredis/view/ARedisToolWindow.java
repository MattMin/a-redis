package com.mzyupc.aredis.view;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.action.*;
import com.mzyupc.aredis.utils.ConnectionListUtil;
import com.mzyupc.aredis.utils.JTreeUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.utils.RedisPoolMgr;
import com.mzyupc.aredis.view.dialog.ConfirmDialog;
import com.mzyupc.aredis.view.dialog.ConnectionSettingsDialog;
import com.mzyupc.aredis.view.editor.ARedisFileSystem;
import com.mzyupc.aredis.view.editor.ARedisVirtualFile;
import com.mzyupc.aredis.vo.ConnectionInfo;
import com.mzyupc.aredis.vo.DbInfo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.mzyupc.aredis.utils.ConnectionListUtil.*;
import static com.mzyupc.aredis.utils.JTreeUtil.expandTree;

/**
 * @author mzyupc@163.com
 */
@Getter
public class ARedisToolWindow implements Disposable {

    private Project project;
    private JPanel aRedisWindowContent;
    private JPanel connectionPanel;
    private PropertyUtil propertyUtil;
    private Tree connectionTree;
    private DefaultMutableTreeNode connectionTreeRoot;
    private DefaultTreeModel connectionTreeModel;
    private LoadingDecorator connectionTreeLoadingDecorator;

    public ARedisToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        initConnections();
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

    public JPanel getContent() {
        return aRedisWindowContent;
    }

    /**
     * 初始化连接
     */
    private void initConnections() {
        List<ConnectionInfo> connections = propertyUtil.getConnections();
        connectionTreeModel = new DefaultTreeModel(connectionTreeRoot);
        for (ConnectionInfo connection : connections) {
            addConnectionToList(connectionTreeModel, connection);
        }

        connectionTree.setModel(connectionTreeModel);
        this.connectionTree.setRootVisible(false);
    }

    /**
     * 初始化自定义UI组件
     */
    private void createUIComponents() {
        connectionPanel = new JPanel();
        connectionPanel.setLayout(new BorderLayout());
        connectionTreeRoot = new DefaultMutableTreeNode();

        // 连接列表
        connectionTree = createConnectionTree();

        // 工具栏
        CommonActionsManager actionManager = CommonActionsManager.getInstance();
        DefaultActionGroup actions = new DefaultActionGroup();
        // 增加key
        actions.add(createAddAction());
        // 删除key
        actions.add(createDeleteAction());
        actions.addSeparator();
        // 展开所有
        actions.add(actionManager.createExpandAllAction(getTreeExpander(connectionTree), connectionTree));
        // 折叠所有
        actions.add(actionManager.createCollapseAllAction(getTreeExpander(connectionTree), connectionTree));
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, actions, true);
        actionToolbar.setTargetComponent(connectionPanel);
        actionToolbar.adjustTheSameSize(true);

        connectionPanel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
        connectionTreeLoadingDecorator = new LoadingDecorator(new JBScrollPane(connectionTree), this, 0);
        connectionPanel.add(connectionTreeLoadingDecorator.getComponent(), BorderLayout.CENTER);
    }

    /**
     * 创建连接列表
     *
     * @return
     */
    private Tree createConnectionTree() {
        connectionTree = new Tree();
        connectionTree.setAlignmentX(Component.LEFT_ALIGNMENT);
        connectionTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);

                int x = e.getX();
                int y = e.getY();

                // connectionTree的双击事件
                if (e.getClickCount() == 2) {
                    connectionTreeLoadingDecorator.startLoading(false);
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
                            addDbs2Connection(connectionNode);
                            // 刷新节点
                            connectionTreeModel.reload(connectionNode);
                            // 展开当前连接
                            JTreeUtil.expandAll(connectionTree, new TreePath(new Object[]{connectionTreeRoot, connectionNode}), true);
                        }

                    }

                    // 双击DB事件
                    if (path.length == 3) {
                        // 打开编辑器
                        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
                        DefaultMutableTreeNode dbNode = (DefaultMutableTreeNode) path[2];

                        ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
                        DbInfo dbInfo = (DbInfo) dbNode.getUserObject();
                        ARedisFileSystem.getInstance(project).openEditor(new ARedisVirtualFile(connectionInfo.getName() + "-DB" + dbInfo.getIndex(),
                                project,
                                connectionInfo,
                                dbInfo,
                                connectionRedisMap.get(connectionInfo.getId())));
                    }

                    connectionTreeLoadingDecorator.stopLoading();
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 获取右键点击所在connectionNodede路径
                    TreePath pathForLocation = connectionTree.getSelectionPath();
                    if (pathForLocation != null && pathForLocation.getPathCount() == 2) {
                        createConnectionPopupMenu().getComponent().show(connectionTree, x, y);
                    }
                }
            }
        });
        return connectionTree;
    }

    /**
     * 创建添加按钮
     *
     * @return
     */
    private AddAction createAddAction() {
        AddAction addAction = new AddAction();
        addAction.setAction(e -> {
            // 弹出连接配置窗口
            ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, null, connectionTree);
            connectionSettingsDialog.show();
        });
        return addAction;
    }

    /**
     * 创建移除按钮
     *
     * @return
     */
    private DeleteAction createDeleteAction() {
        DeleteAction deleteAction = new DeleteAction();
        deleteAction.setAction(e -> {
            // 弹出删除确认对话框
            ConfirmDialog removeConnectionDialog = new ConfirmDialog(project, "Confirm", "Are you sure you want to delete these connections?");
            removeConnectionDialog.setCustomOkAction(actionEvent -> {
                // connection列表中移除
                ConnectionListUtil.removeConnectionFromTree(connectionTree, connectionRedisMap, propertyUtil);
            });
            removeConnectionDialog.show();
        });
        return deleteAction;
    }

    private RefreshAction createRefreshAction() {
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
                removeAllDbs(connectionNode);
                addDbs2Connection(connectionNode);
                connectionTreeModel.reload(connectionNode);
            }
            connectionTreeLoadingDecorator.stopLoading();
        });
        return refreshAction;
    }

    private EditAction createEditAction() {
        EditAction editAction = new EditAction();
        editAction.setAction(e -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();
            // 弹出连接配置窗口
            ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, connectionInfo.getId(), connectionTree);
            connectionSettingsDialog.show();
        });
        return editAction;
    }

    private DuplicateAction createDuplicateAction() {
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

    private ConsoleAction createConsoleAction() {
        ConsoleAction consoleAction = new ConsoleAction();
        consoleAction.setAction(e -> {
            // todo console
        });
        return consoleAction;
    }

    private CloseAction createCloseAction() {
        CloseAction closeAction = new CloseAction();
        closeAction.setAction(e -> {
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) selectionPath.getPath()[1];
            ConnectionInfo connectionInfo = (ConnectionInfo) connectionNode.getUserObject();

            // 关闭连接池
            RedisPoolMgr redisPoolMgr = connectionRedisMap.get(connectionInfo.getId());
            if (redisPoolMgr != null) {
                redisPoolMgr.invalidate();
            }

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
    @NotNull
    private ActionPopupMenu createConnectionPopupMenu() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(createRefreshAction());
        actionGroup.addSeparator();
        actionGroup.add(createEditAction());
        actionGroup.add(createDuplicateAction());
        actionGroup.add(createDeleteAction());
        actionGroup.addSeparator();
        actionGroup.add(createConsoleAction());
        actionGroup.addSeparator();
        actionGroup.add(createCloseAction());
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup);
        menu.setTargetComponent(connectionTree);
        return menu;
    }

    @Override
    public void dispose() {
        // 关闭所有连接
        for (RedisPoolMgr redisPoolMgr : connectionRedisMap.values()) {
            if (redisPoolMgr != null) {
                redisPoolMgr.invalidate();
            }
        }
    }
}
