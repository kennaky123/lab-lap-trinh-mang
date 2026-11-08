package mail.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Thanh cuộn phẳng hiện đại
 */
public class ModernScrollBarUI extends BasicScrollBarUI {
    private static final int THUMB_SIZE = 8;

    @Override
    protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }

    @Override
    protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }

    private JButton createZeroButton() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension(0, 0));
        return btn;
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {}

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(190, 195, 205));
        g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 6, 6);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        return new Dimension(THUMB_SIZE, THUMB_SIZE);
    }
}

