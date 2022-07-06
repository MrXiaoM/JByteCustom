package me.synnk.jbytecustom.ui;

import me.synnk.jbytecustom.JByteCustom;
import me.synnk.jbytecustom.JarArchive;
import me.synnk.jbytecustom.ui.dialogue.InsnEditDialogue;
import me.synnk.jbytecustom.ui.tree.SortedTreeNode;
import me.synnk.jbytecustom.utils.ErrorDisplay;
import me.synnk.jbytecustom.utils.MethodUtils;
import me.synnk.jbytecustom.utils.asm.FrameGen;
import me.lpk.util.drop.IDropUser;
import me.lpk.util.drop.JarDropHandler;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

public class ClassTree extends JTree implements IDropUser {

    private static ArrayList<Object> expandedNodes = new ArrayList<>();
    private JByteCustom jbm;
    private DefaultTreeModel model;
    private HashMap<String, SortedTreeNode> preloadMap;

    public ClassTree(JByteCustom jam) {
        this.jbm = jam;
        this.setRootVisible(false);
        this.setShowsRootHandles(true);
        this.setCellRenderer(new TreeCellRenderer());
        this.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                SortedTreeNode node = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                if (node == null)
                    return;
                if (node.getCn() != null && node.getMn() != null) {
                    jam.selectMethod(node.getCn(), node.getMn());
                } else if (node.getCn() != null) {
                    jam.selectClass(node.getCn());
                } else {

                }
            }
        });
        this.model = new DefaultTreeModel(new SortedTreeNode(""));
        this.setModel(model);
        this.setTransferHandler(new JarDropHandler(this, 0));
        this.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    public void refreshTree(JarArchive jar) {
        DefaultTreeModel tm = this.model;
        SortedTreeNode root = (SortedTreeNode) tm.getRoot();
        root.removeAllChildren();
        tm.reload();

        preloadMap = new HashMap<>();
        if (jar.getClasses() != null)
            for (ClassNode c : jar.getClasses().values()) {
                String name = c.name;
                String[] path = array_unique(name.split("/"));
                name = String.join("/", path);

                int i = 0;
                int slashIndex = 0;
                SortedTreeNode prev = root;
                while (true) {
                    slashIndex = name.indexOf("/", slashIndex + 1);
                    if (slashIndex == -1) {
                        break;
                    }
                    String p = name.substring(0, slashIndex);
                    if (preloadMap.containsKey(p)) {
                        prev = preloadMap.get(p);
                    } else {
                        try{
                            SortedTreeNode stn = new SortedTreeNode(path[i]);
                            prev.add(stn);
                            prev = stn;
                            preloadMap.put(p, prev);
                        }catch(ArrayIndexOutOfBoundsException ex){
                            JByteCustom.LOGGER.println("Failed to load " + path[i]);
                        }
                    }
                    i++;
                }
                SortedTreeNode clazz = new SortedTreeNode(c);
                prev.add(clazz);
                for (MethodNode m : c.methods) {
                    clazz.add(new SortedTreeNode(c, m));
                }
            }
        boolean sort = JByteCustom.ops.get("sort_methods").getBoolean();
        sort(tm, root, sort);
        tm.reload();
        addListener();
        if (!expandedNodes.isEmpty()) {
            expandSaved(root);
        }
    }


    public static String[] array_unique(String[] ss) {
        // array_unique
        List<String> list =new ArrayList<String>();
        for(String s:ss){
            if(!list.contains(s))			//或者list.indexOf(s)!=-1
                list.add(s);
        }
        return list.toArray(new String[list.size()]);
    }

    public void expandSaved(SortedTreeNode node) {
        TreePath tp = new TreePath(node.getPath());
        if (node.getCn() != null && expandedNodes.contains(node.getCn())) {
            super.expandPath(tp);
        }
        if (expandedNodes.contains(tp.toString())) {
            super.expandPath(tp);
        }
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                SortedTreeNode n = (SortedTreeNode) e.nextElement();
                expandSaved(n);
            }
        }
    }

    @Override
    public void expandPath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        if (stn.getCn() != null) {
            expandedNodes.add(stn.getCn());
        } else {
            expandedNodes.add(path.toString());
        }
        super.expandPath(path);
    }

    @Override
    public void collapsePath(TreePath path) {
        SortedTreeNode stn = (SortedTreeNode) path.getLastPathComponent();
        if (stn.getCn() != null) {
            expandedNodes.remove(stn.getCn());
        } else {
            expandedNodes.remove(path.toString());
        }
        super.collapsePath(path);
    }

    private void addListener() {
        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (SwingUtilities.isRightMouseButton(me)) {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        MethodNode mn = stn.getMn();
                        ClassNode cn = stn.getCn();

                        if (mn != null) {
                            //method selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    new InsnEditDialogue(mn, mn).open();
                                    changedChilds((TreeNode) model.getRoot());
                                }
                            });
                            menu.add(edit);
                            JMenuItem duplicate = new JMenuItem(JByteCustom.res.getResource("duplicate"));
                            duplicate.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode dup = MethodUtils.copy(mn);
                                    String name = JOptionPane.showInputDialog(null, "Duplicated method name ?", "Rename", JOptionPane.QUESTION_MESSAGE);
                                    if(MethodUtils.equalName(cn, name)){
                                        JOptionPane.showMessageDialog(null, "The name is already existed.", "Existed Name!", JOptionPane.WARNING_MESSAGE);
                                        return;
                                    }
                                    dup.name = name;
                                    cn.methods.add(dup);
                                    jbm.getJarTree().refreshTree(jbm.getFile());
                                }
                            });
                            menu.add(duplicate);
                            JMenuItem search = new JMenuItem(JByteCustom.res.getResource("search"));
                            search.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    jbm.getSearchList().searchForFMInsn(cn.name, mn.name, mn.desc, false, false);
                                }
                            });
                            menu.add(search);
                            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_remove"),
                                            JByteCustom.res.getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        cn.methods.remove(mn);
                                        model.removeNodeFromParent(stn);
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenu tools = new JMenu(JByteCustom.res.getResource("tools"));
                            JMenuItem clear = new JMenuItem(JByteCustom.res.getResource("clear"));
                            clear.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_clear"), JByteCustom.res.getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.clear(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(clear);

                            JMenuItem lines = new JMenuItem(JByteCustom.res.getResource("remove_lines"));
                            lines.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_lines"), JByteCustom.res.getResource("confirm"),
                                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeLines(mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(lines);
                            JMenuItem deadcode = new JMenuItem(JByteCustom.res.getResource("remove_dead_code"));
                            deadcode.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_dead_code"),
                                            JByteCustom.res.getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        MethodUtils.removeDeadCode(cn, mn);
                                        jbm.selectMethod(cn, mn);
                                    }
                                }
                            });
                            tools.add(deadcode);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else if (cn != null) {
                            //class selected
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem insert = new JMenuItem(JByteCustom.res.getResource("add_method"));
                            insert.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    MethodNode mn = new MethodNode(1, "", "()V", null, null);
                                    mn.maxLocals = 1;
                                    if (new InsnEditDialogue(mn, mn).open()) {
                                        if (mn.name.isEmpty() || mn.desc.isEmpty()) {
                                            ErrorDisplay.error("Method name / desc cannot be empty");
                                            return;
                                        }
                                        cn.methods.add(mn);
                                        model.insertNodeInto(new SortedTreeNode(cn, mn), stn, 0);
                                    }
                                }
                            });
                            menu.add(insert);

                            JMenuItem edit = new JMenuItem(JByteCustom.res.getResource("edit"));
                            edit.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(edit);
                            JMenu tools = new JMenu(JByteCustom.res.getResource("tools"));
                            JMenuItem frames = new JMenuItem(JByteCustom.res.getResource("generate_frames"));
                            frames.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    FrameGen.regenerateFrames(jbm, cn);
                                }
                            });
                            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_remove"),
                                            JByteCustom.res.getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        jbm.getFile().getClasses().remove(cn.name);
                                        TreeNode parent = stn.getParent();
                                        model.removeNodeFromParent(stn);
                                        while (parent != null && !parent.children().hasMoreElements() && parent != model.getRoot()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            menu.add(remove);
                            tools.add(frames);
                            menu.add(tools);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        } else {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem remove = new JMenuItem(JByteCustom.res.getResource("remove"));
                            remove.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    if (JOptionPane.showConfirmDialog(JByteCustom.instance, JByteCustom.res.getResource("confirm_remove"),
                                            JByteCustom.res.getResource("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                                        TreeNode parent = stn.getParent();
                                        deleteItselfAndChilds(stn);
                                        while (parent != null && !parent.children().hasMoreElements() && parent != model.getRoot()) {
                                            TreeNode par = parent.getParent();
                                            model.removeNodeFromParent((MutableTreeNode) parent);
                                            parent = par;
                                        }
                                    }
                                }
                            });
                            menu.add(remove);
                            JMenuItem add = new JMenuItem(JByteCustom.res.getResource("add"));
                            add.addActionListener(new ActionListener() {
                                public void actionPerformed(ActionEvent e) {
                                    ClassNode cn = new ClassNode();
                                    cn.version = 52;
                                    cn.name = getPath(stn);
                                    cn.superName = "java/lang/Object";
                                    if (new InsnEditDialogue(mn, cn).open()) {
                                        jbm.getFile().getClasses().put(cn.name, cn);
                                        jbm.refreshTree();
                                    }
                                }
                            });
                            menu.add(add);
                            menu.show(ClassTree.this, me.getX(), me.getY());
                        }
                    }
                } else {
                    TreePath tp = ClassTree.this.getPathForLocation(me.getX(), me.getY());
                    if (tp != null && tp.getParentPath() != null) {
                        ClassTree.this.setSelectionPath(tp);
                        if (ClassTree.this.getLastSelectedPathComponent() == null) {
                            return;
                        }
                        SortedTreeNode stn = (SortedTreeNode) ClassTree.this.getLastSelectedPathComponent();
                        if (stn.getMn() == null && stn.getCn() == null) {
                            if (ClassTree.this.isExpanded(tp)) {
                                ClassTree.this.collapsePath(tp);
                            } else {
                                ClassTree.this.expandPath(tp);
                            }
                        }
                    }
                }
            }
        });
    }

    private String getPath(SortedTreeNode stn) {
        String path = "";
        while (stn != null && stn != model.getRoot()) {
            path = stn.toString() + "/" + path;
            stn = (SortedTreeNode) stn.getParent();
        }
        return path;
    }

    private void sort(DefaultTreeModel model, SortedTreeNode node, boolean sm) {
        if (!node.isLeaf() && (sm ? true : (!node.toString().endsWith(".class")))) {
            node.sort();
            for (int i = 0; i < model.getChildCount(node); i++) {
                SortedTreeNode child = ((SortedTreeNode) model.getChild(node, i));
                sort(model, child, sm);
            }
        }
    }

    @Override
    public void preLoadJars(int id) {

    }

    @Override
    public void onJarLoad(int id, File input) {
        jbm.loadFile(input);
    }

    public void refreshMethod(ClassNode cn, MethodNode mn) {
        changedChilds((TreeNode) model.getRoot());
    }

    public void changedChilds(TreeNode node) {
        model.nodeChanged(node);
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                changedChilds(n);
            }
        }
    }

    public void deleteItselfAndChilds(SortedTreeNode node) {
        if (node.getChildCount() >= 0) {
            for (Enumeration<?> e = node.children(); e.hasMoreElements(); ) {
                TreeNode n = (TreeNode) e.nextElement();
                deleteItselfAndChilds((SortedTreeNode) n);
            }
        }
        if (node.getCn() != null)
            jbm.getFile().getClasses().remove(node.getCn().name);
        model.removeNodeFromParent(node);
    }

    public void collapseAll() {
        expandedNodes.clear();
        JByteCustom.instance.refreshTree();
    }
}