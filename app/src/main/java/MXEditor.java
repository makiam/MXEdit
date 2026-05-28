import mx.MaterialXEditor;

import javax.swing.*;


public class MXEditor {
    public static void main(String... args) {
        SwingUtilities.invokeLater(() -> new MaterialXEditor().setVisible(true));
    }
}
