package org.totalnet.bootstrap.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPasswordField;

/**
 * Campo de contraseña que muestra un texto de ayuda gris cuando está vacío.
 */
public class PlaceholderPasswordField extends JPasswordField {

    private String placeholder;

    public PlaceholderPasswordField(int columns) {
        super(columns);
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (placeholder == null || placeholder.isEmpty()
                || getPassword().length > 0 || isFocusOwner()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(150, 150, 150));
            g2.setFont(getFont().deriveFont(Font.ITALIC));
            int x = getInsets().left + 2;
            int y = (getHeight() + getFontMetrics(getFont()).getAscent()
                    - getFontMetrics(getFont()).getDescent()) / 2;
            g2.drawString(placeholder, x, y);
        } finally {
            g2.dispose();
        }
    }
}
