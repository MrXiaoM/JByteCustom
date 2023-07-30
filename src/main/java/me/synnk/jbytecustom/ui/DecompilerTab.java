package me.synnk.jbytecustom.ui;

import me.synnk.jbytecustom.CustomRPC;
import me.synnk.jbytecustom.JByteCustom;
import me.synnk.jbytecustom.decompiler.*;
import me.synnk.jbytecustom.discord.Discord;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class DecompilerTab extends JPanel {
    private static final File tempDir = new File(System.getProperty("java.io.tmpdir"));
    private static final File userDir = new File(System.getProperty("user.dir"));
    public static String decomp = null;
    public static Decompilers decompiler = Decompilers.CFR;
    public static DecompilerPanel dp;
    private final JLabel label;
    private final JByteCustom jbm;

    static {
        dp = new DecompilerPanel();
    }

    public DecompilerTab(JByteCustom jbm) {
        this.jbm = jbm;
        this.label = new JLabel(decompiler + " Decompiler");
        jbm.setDP(dp);
        this.setLayout(new BorderLayout(0, 0));
        JPanel lpad = new JPanel();
        lpad.setBorder(new EmptyBorder(1, 5, 0, 1));
        lpad.setLayout(new GridLayout());
        lpad.add(label);
        JPanel rs = new JPanel();
        rs.setLayout(new GridLayout(1, 5));
        for (int i = 0; i < 3; i++)
            rs.add(new JPanel());
        JComboBox<Decompilers> decompilerCombo = new JComboBox<Decompilers>(Decompilers.values());
        decompilerCombo.setSize(100, decompilerCombo.getHeight());

        Discord.currentDecompiler = decompiler.getName() + " " + decompiler.getVersion();
        Discord.updateDecompiler(Discord.currentDecompiler);

        decompilerCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                decompiler = (Decompilers) decompilerCombo.getSelectedItem();
                assert decompiler != null;
                label.setText(decompiler.getName() + " " + decompiler.getVersion());

                Discord.currentDecompiler = decompiler.getName() + " " + decompiler.getVersion();
                Discord.updateDecompiler(Discord.currentDecompiler);

                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });
        decompilerCombo.setPreferredSize(new Dimension(500, 10));
        rs.add(decompilerCombo);

        JButton reload = new JButton(JByteCustom.res.getResource("reload"));
        reload.setSize(100, reload.getHeight());
        reload.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });

        JButton extract = new JButton("Extract");
        extract.setSize(100, extract.getHeight());
        extract.addActionListener(e -> {
            jbm.extractFile(
                    System.getProperty("user.home") + File.separator + "Documents",
                    DecompilerOutput.decompiledClassName + ".java",
                    DecompilerOutput.decompiledClass, // content
                    DecompilerOutput.decompiledClassName, // suggest name
                    "java"); // extensions

        });
        rs.add(reload);
        lpad.add(rs);
        rs.add(extract);
        this.add(lpad, BorderLayout.NORTH);
        JScrollPane scp = new RTextScrollPane(dp);
        scp.getVerticalScrollBar().setUnitIncrement(16);
        this.add(scp, BorderLayout.CENTER);
    }

    public void decompile(ClassNode cn, MethodNode mn, boolean deleteCache) {
        if (cn == null) {
            return;
        }
        Decompiler d = null;

        dp.setEditable(false);

        switch (decompiler) {
            case PROCYON:
                d = new ProcyonDecompiler(jbm, dp);
                break;
            case FERNFLOWER:
                d = new FernflowerDecompiler(jbm, dp);
                break;
            case CFR:
                d = new CFRDecompiler(jbm, dp);
                break;
            case KRAKATAU:
                d = new KrakatauDecompiler(jbm, dp);
                break;
            case KOFFEE:
                d = new KoffeeDecompiler(jbm, dp);
                break;
        }
        d.setNode(cn, mn);

        if (deleteCache) {
            d.deleteCache();
        }
        d.start();
    }
}