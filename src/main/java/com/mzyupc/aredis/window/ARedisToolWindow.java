package com.mzyupc.aredis.window;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.Tree;
import com.mzyupc.aredis.dialog.ConnectionSettingsDialog;
import com.mzyupc.aredis.dialog.RemoveConnectionDialog;
import com.mzyupc.aredis.layout.VFlowLayout;
import com.mzyupc.aredis.utils.JTreeUtil;
import com.mzyupc.aredis.utils.PropertyUtil;
import com.mzyupc.aredis.vo.ConnectionInfo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import static com.mzyupc.aredis.utils.ConnectionListUtil.*;

/**
 * @author mzyupc@163.com
 */
@Getter
public class ARedisToolWindow {

    private Project project;
    private JPanel aRedisWindowContent;
    private JPanel connectionPanel;
    private PropertyUtil propertyUtil;
    private Tree connectionTree;
    private DefaultMutableTreeNode connectionTreeRoot;
    private DefaultTreeModel connectionTreeModel;

    public ARedisToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.propertyUtil = PropertyUtil.getInstance(project);
        initConnections();
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
        // panel内的元素垂直布局
//        BoxLayout boxLayout = new BoxLayout(connectionPanel, BoxLayout.Y_AXIS);
        connectionPanel.setLayout(new VFlowLayout());

        connectionTreeRoot = new DefaultMutableTreeNode();
        connectionTree = new Tree();
        connectionTree.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 默认展开
//        tree.expandPath(new TreePath(root));
        connectionTree.setBorder(new LineBorder(Color.RED, 1));

        // 右键菜单
        JPopupMenu popupMenu = createConnectionPopupMenu();
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
                            addDbs2Connection(connectionNode);
                            // 刷新节点
                            connectionTreeModel.reload(connectionNode);
                            // 展开当前连接
                            JTreeUtil.expandAll(connectionTree, new TreePath(new Object[]{connectionTreeRoot, connectionNode}));
                        }
                    }

                    // 双击DB事件
                    if (path.length == 3) {
                        // todo 打开编辑器
                        DefaultMutableTreeNode connectionNode = (DefaultMutableTreeNode) path[1];
                        DefaultMutableTreeNode dbNode = (DefaultMutableTreeNode) path[2];

                    }
                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 获取右键点击所在connectionNodede路径
                    TreePath pathForLocation = connectionTree.getSelectionPath();
                    if (pathForLocation != null && pathForLocation.getPathCount() == 2) {
                        popupMenu.show(connectionTree, x, y);
                    }
                }
            }
        });

        // 添加连接按钮
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(connectionTree);
        decorator.setAddAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                // 弹出连接配置窗口
                ConnectionSettingsDialog connectionSettingsDialog = new ConnectionSettingsDialog(project, null, connectionTree);
                connectionSettingsDialog.show();
            }
        });

        // 移除连接按钮
        decorator.setRemoveAction(new AnActionButtonRunnable() {
            @Override
            public void run(AnActionButton anActionButton) {
                // 弹出删除确认对话框
                RemoveConnectionDialog removeConnectionDialog = new RemoveConnectionDialog(project, connectionTree, connectionRedisMap);
                removeConnectionDialog.show();
            }
        });

        JPanel panel = decorator.createPanel();
        connectionPanel.add(panel);
        connectionPanel.add(connectionTree);
    }

    /**
     * 创建一个连接右键菜单
     *
     * @return
     */
    @NotNull
    private JPopupMenu createConnectionPopupMenu() {
        // 连接的右键菜单
        JMenuItem reloadServerItem = new JMenuItem("Reload Server");
        reloadServerItem.setIcon(AllIcons.Actions.Refresh);
        reloadServerItem.addActionListener(e -> {
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

        });

        JMenuItem editItem = new JMenuItem("Edit");
        editItem.setIcon(AllIcons.Actions.Edit);
        editItem.addActionListener(e -> {
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

        JMenuItem duplicateItem = new JMenuItem("Duplicate");
        duplicateItem.setIcon(AllIcons.Actions.Copy);
        duplicateItem.addActionListener(e -> {
            // todo 复制按钮功能
            TreePath selectionPath = connectionTree.getSelectionPath();
            if (selectionPath == null || selectionPath.getPathCount() != 2) {
                return;
            }

            ConnectionInfo newConnectionInfo = duplicateConnections(connectionTree);
            propertyUtil.saveConnection(newConnectionInfo);

        });

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.setIcon(AllIcons.Actions.DeleteTagHover);
        deleteItem.addActionListener(e -> {
            // 弹出删除确认对话框
            RemoveConnectionDialog removeConnectionDialog = new RemoveConnectionDialog(project, connectionTree, connectionRedisMap);
            removeConnectionDialog.show();
        });

        JMenuItem openConsoleItem = new JMenuItem("Open Console");
        openConsoleItem.setIcon(AllIcons.Debugger.Console);
        openConsoleItem.addActionListener(e -> {
            // todo 打开控制台的功能
        });
        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.setPreferredSize(new Dimension(200, 160));
        popupMenu.add(reloadServerItem);
        popupMenu.add(editItem);
        popupMenu.add(duplicateItem);
        popupMenu.add(deleteItem);
        popupMenu.add(openConsoleItem);
        return popupMenu;
    }

}
