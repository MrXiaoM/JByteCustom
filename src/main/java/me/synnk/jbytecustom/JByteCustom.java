package me.synnk.jbytecustom;

import com.sun.tools.attach.VirtualMachine;
import me.lpk.util.ASMUtils;
import me.lpk.util.OpUtils;
import me.synnk.jbytecustom.discord.Discord;
import me.synnk.jbytecustom.logging.Logging;
import me.synnk.jbytecustom.plugin.Plugin;
import me.synnk.jbytecustom.plugin.PluginManager;
import me.synnk.jbytecustom.res.LanguageRes;
import me.synnk.jbytecustom.res.Options;
import me.synnk.jbytecustom.ui.*;
import me.synnk.jbytecustom.ui.graph.ControlFlowPanel;
import me.synnk.jbytecustom.ui.lists.LVPList;
import me.synnk.jbytecustom.ui.lists.MyCodeList;
import me.synnk.jbytecustom.ui.lists.SearchList;
import me.synnk.jbytecustom.ui.lists.TCBList;
import me.synnk.jbytecustom.ui.tree.SortedTreeNode;
import me.synnk.jbytecustom.utils.ErrorDisplay;
import me.synnk.jbytecustom.utils.FileUtils;
import me.synnk.jbytecustom.utils.asm.FrameGen;
import me.synnk.jbytecustom.utils.attach.RuntimeJarArchive;
import me.synnk.jbytecustom.utils.gui.LookUtils;
import me.synnk.jbytecustom.utils.task.AttachTask;
import me.synnk.jbytecustom.utils.task.RetransformTask;
import me.synnk.jbytecustom.utils.task.SaveTask;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;

public class JByteCustom extends JFrame {
    public final static String version = "1.1.5";
    private static final String jbytemod = "JByteCustom v" + version;


    Dimension size = Toolkit.getDefaultToolkit().getScreenSize(); // screen
    public static File workingDir = new File(".");
    public static String configPath = "jbytemod.cfg";
    public static Logging LOGGER;
    public static LanguageRes res;
    public static Options ops;
    public static String lastEditFile = "";
    public static HashMap<ClassNode, MethodNode> lastSelectedTreeEntries = new LinkedHashMap<>();
    public static JByteCustom instance;
    public static Color border;
    private static boolean lafInit;
    private static JarArchive file;
    private static Instrumentation agentInstrumentation;

    static {
        try {
            System.loadLibrary("attach");
        } catch (Throwable ex) {
        }
    }

    private JPanel contentPane;
    private ClassTree jarTree;
    private MyCodeList clist;
    private PageEndPanel pp;
    private SearchList slist;
    private DecompilerPanel dp;
    private TCBList tcblist;
    private MyTabbedPane tabbedPane;
    private InfoPanel sp;
    private LVPList lvplist;
    private ControlFlowPanel cfp;
    private MyMenuBar myMenuBar;
    private ClassNode currentNode;
    private MethodNode currentMethod;
    private PluginManager pluginManager;
    private File filePath;

    /**
     * Create the frame.
     * @throws Exception
     */
    public JByteCustom(boolean agent) throws Exception {
        if (ops.get("use_rt").getBoolean()) {
            new FrameGen().start();
        }
        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (JOptionPane.showConfirmDialog(JByteCustom.this, res.getResource("exit_warn"), res.getResource("is_sure"),
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    if (agent) {
                        dispose();
                    } else {
                        Discord.discordRPC.Discord_Shutdown();
                        Runtime.getRuntime().exit(0);
                    }
                }
            }
        });
        if (border == null) {
            border = new Color(146, 151, 161);
        }

        int width = (int)size.getWidth(); // X
        int height = (int)size.getHeight(); // Y

        System.out.println("Screen Size: " + width + "x" + height);
        this.setBounds(width/8, 0, 1024, 640);

        this.setTitle(jbytemod);
        this.setJMenuBar(myMenuBar = new MyMenuBar(this, agent));
        this.jarTree = new ClassTree(this);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(5, 5));
        this.setContentPane(contentPane);
        this.setTCBList(new TCBList());
        this.setLVPList(new LVPList());
        JPanel border = new JPanel();

        border.setLayout(new GridLayout());
        JSplitPane splitPane = new MySplitPane(this, jarTree);
        JPanel b2 = new JPanel();
        b2.setBorder(new EmptyBorder(5, 0, 5, 0));
        b2.setLayout(new GridLayout());
        b2.add(splitPane);
        border.add(b2);
        contentPane.add(border, BorderLayout.CENTER);
        contentPane.add(pp = new PageEndPanel(), BorderLayout.PAGE_END);
        contentPane.add(new MyToolBar(this), BorderLayout.PAGE_START);
        if (file != null) {
            this.refreshTree();
        }
    }

    public static void agentmain(String agentArgs, Instrumentation ins) throws Exception {
        if (!ins.isRedefineClassesSupported()) {
            JOptionPane.showMessageDialog(null, "Class redefinition is disabled, cannot attach!");
            return;
        }
        agentInstrumentation = ins;
        workingDir = new File(agentArgs);
        initialize();
        if (!lafInit) {
            LookUtils.setLAF();
            lafInit = true;
        }
        JByteCustom.file = new RuntimeJarArchive(ins);
        JByteCustom frame = new JByteCustom(true);
        frame.setTitleSuffix("Agent");
        instance = frame;
        frame.setVisible(true);
    }

    public static void initialize() {
        LOGGER = new Logging();
        res = new LanguageRes();
        ops = new Options();
        Discord.init();
        try {
            System.setProperty("file.encoding", "UTF-8");
            Field charset = Charset.class.getDeclaredField("defaultCharset");
            charset.setAccessible(true);
            charset.set(null, null);
        } catch (Throwable t) {
            JByteCustom.LOGGER.err("Failed to set encoding to UTF-8 (" + t.getMessage() + ")");
        }
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        options.addOption("f", "file", true, "File to open");
        options.addOption("d", "dir", true, "Working directory");
        options.addOption("c", "config", true, "Config file name");
        options.addOption("?", "help", false, "Prints this help");

        CommandLineParser parser = new DefaultParser();
        CommandLine line;

        try {
            line = parser.parse(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            e.printStackTrace();
            throw new RuntimeException("An error occurred while parsing the commandline ");
        }
        if (line.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(jbytemod, options);
            return;
        }
        if (line.hasOption("d")) {
            workingDir = new File(line.getOptionValue("d"));
            if (!(workingDir.exists() && workingDir.isDirectory())) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp(jbytemod, options);
                return;
            }
            JByteCustom.LOGGER.err("Specified working dir set");
        }
        if (line.hasOption("c")) {
            configPath = line.getOptionValue("c");
        }
        initialize();
        EventQueue.invokeLater(new Runnable() {

            public void run() {
                try {
                    if (!lafInit) {
                        LookUtils.setLAF();
                        lafInit = true;
                    }
                    JByteCustom frame = new JByteCustom(false);
                    instance = frame;
                    frame.setVisible(true);
                    if (line.hasOption("f")) {
                        File input = new File(line.getOptionValue("f"));
                        if (FileUtils.exists(input) && FileUtils.isType(input, ".jar", ".class")) {
                            frame.loadFile(input);
                            JByteCustom.LOGGER.log("Specified file loaded");
                        } else {
                            JByteCustom.LOGGER.err("Specified file not found");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void resetLAF() {
        lafInit = false;
    }

    public static void restartGUI() {
        instance.dispose();
        instance = null;
        System.gc();
        JByteCustom.main(new String[0]);
    }

    public void applyChangesAgent() {
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        new RetransformTask(this, agentInstrumentation, file).execute();
    }

    public void attachTo(VirtualMachine vm) throws Exception {
        new AttachTask(this, vm).execute();
    }

    public void changeUI(String clazz) {
        LookUtils.changeLAF(clazz);
    }

    public ControlFlowPanel getCFP() {
        return this.cfp;
    }

    public void setCFP(ControlFlowPanel cfp) {
        this.cfp = cfp;
    }

    public MyCodeList getCodeList() {
        return clist;
    }

    public void setCodeList(MyCodeList list) {
        this.clist = list;
    }

    public MethodNode getCurrentMethod() {
        return currentMethod;
    }

    public ClassNode getCurrentNode() {
        return currentNode;
    }

    public JarArchive getFile() {
        return file;
    }

    public File getFilePath() {
        return filePath;
    }

    public ClassTree getJarTree() {
        return jarTree;
    }

    public LVPList getLVPList() {
        return lvplist;
    }

    private void setLVPList(LVPList lvp) {
        this.lvplist = lvp;
    }

    public MyMenuBar getMyMenuBar() {
        return myMenuBar;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public void setPluginManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public PageEndPanel getPP() {
        return pp;
    }

    public SearchList getSearchList() {
        return slist;
    }

    public MyTabbedPane getTabbedPane() {
        return tabbedPane;
    }

    public void setTabbedPane(MyTabbedPane tp) {
        this.tabbedPane = tp;
    }

    public TCBList getTCBList() {
        return tcblist;
    }

    public void setTCBList(TCBList tcb) {
        this.tcblist = tcb;
    }

    /**
     * Load .jar or .class file
     */
    public void loadFile(File input) {
        this.filePath = input;
        String ap = input.getAbsolutePath();
        if (ap.endsWith(".jar")) {
            try {
                file = new JarArchive(this, input);
                this.setTitleSuffix(input.getName());
            } catch (Throwable e) {
                new ErrorDisplay(e);
            }
        } else if (ap.endsWith(".class")) {
            try {
                file = new JarArchive(ASMUtils.getNode(Files.readAllBytes(input.toPath())));
                this.setTitleSuffix(input.getName());
                this.refreshTree();
            } catch (Throwable e) {
                new ErrorDisplay(e);
            }
        } else {
            new ErrorDisplay(new UnsupportedOperationException(res.getResource("jar_warn")));
        }
        for (Plugin p : pluginManager.getPlugins()) {
            p.loadFile(file.getClasses());
        }
    }

    public void refreshAgentClasses() {
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        this.refreshTree();
    }

    // RESET
    public void resetWorkspace() {

    }

    public void refreshTree() {
        LOGGER.log("Building tree..");
        this.jarTree.refreshTree(file);
    }

    public void saveFile(File output) {
        try {
            new SaveTask(this, output, file).execute();
        } catch (Throwable t) {
            new ErrorDisplay(t);
        }
    }

    public void selectClass(ClassNode cn) {
        if (ops.get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        this.currentNode = cn;
        this.currentMethod = null;
        sp.selectClass(cn);
        clist.loadFields(cn);
        tabbedPane.selectClass(cn);
        lastSelectedTreeEntries.put(cn, null);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    private boolean selectEntry(MethodNode mn, DefaultTreeModel tm, SortedTreeNode node) {
        for (int i = 0; i < tm.getChildCount(node); i++) {
            SortedTreeNode child = (SortedTreeNode) tm.getChild(node, i);
            if (child.getMn() != null && child.getMn().equals(mn)) {
                TreePath tp = new TreePath(tm.getPathToRoot(child));
                jarTree.setSelectionPath(tp);
                jarTree.scrollPathToVisible(tp);
                return true;
            }
            if (!child.isLeaf()) {
                if (selectEntry(mn, tm, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        if (ops.get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        OpUtils.clearLabelCache();
        this.currentNode = cn;
        this.currentMethod = mn;
        sp.selectMethod(cn, mn);
        if (!clist.loadInstructions(mn)) {
            clist.setSelectedIndex(-1);
        }
        tcblist.addNodes(cn, mn);
        lvplist.addNodes(cn, mn);
        cfp.setNode(mn);
        dp.setText("");
        tabbedPane.selectMethod(cn, mn);
        lastSelectedTreeEntries.put(cn, mn);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    public void setDP(DecompilerPanel dp) {
        this.dp = dp;
    }

    public void setSearchlist(SearchList searchList) {
        this.slist = searchList;
    }

    public void setSP(InfoPanel sp) {
        this.sp = sp;
    }

    private void setTitleSuffix(String suffix) {
        this.setTitle(jbytemod + " - " + suffix);
    }

    @Override
    public void setVisible(boolean b) {
        this.setPluginManager(new PluginManager(this));
        this.myMenuBar.addPluginMenu(pluginManager.getPlugins());
        super.setVisible(b);
    }

    public void treeSelection(ClassNode cn, MethodNode mn) {
        //selection may take some time
        new Thread(() -> {
            DefaultTreeModel tm = (DefaultTreeModel) jarTree.getModel();
            if (this.selectEntry(mn, tm, (SortedTreeNode) tm.getRoot())) {
                jarTree.repaint();
            }
        }).start();
    }

}