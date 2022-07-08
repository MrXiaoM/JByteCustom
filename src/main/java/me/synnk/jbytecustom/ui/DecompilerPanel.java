package me.synnk.jbytecustom.ui;

import me.synnk.jbytecustom.utils.ErrorDisplay;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

import java.awt.*;
import java.io.IOException;

public class DecompilerPanel extends RSyntaxTextArea {

    public DecompilerPanel() {
        this.setSyntaxEditingStyle("text/java");
        this.setCodeFoldingEnabled(true);
        this.setAntiAliasingEnabled(true);
        this.setFont(new Font("Arial", Font.PLAIN, 12));
        this.setEditable(false);

        // change theme to java
        try {
            Theme theme;
            theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/eclipse.xml"));

            theme.apply(this);
        } catch (IOException e1) {
            new ErrorDisplay(e1);
        }
    }
}
