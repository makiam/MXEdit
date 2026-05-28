package mx;

import javax.swing.*;

public class MaterialXEditor extends javax.swing.JFrame {
    @Override
    protected void frameInit() {
        super.frameInit();
        this.setTitle("Material Editor");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1200, 800);
        this.setLocationRelativeTo(null);

        MaterialXCanvas canvas = new MaterialXCanvas();
        this.add(canvas);

        this.setVisible(true);
        canvas.requestFocusInWindow(); // Ensure keyboard events work immediately
    }
}
