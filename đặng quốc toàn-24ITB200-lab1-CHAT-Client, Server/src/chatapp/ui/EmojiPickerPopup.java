package chatapp.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Bảng chọn Icon Biểu Cảm (Emoji Picker) hiện đại cho ứng dụng Chat:
 * - Khắc phục triệt để lỗi rút gọn text thành '...' bằng cách xóa margin/insets mặc định của Windows
 * - Hỗ trợ font Segoe UI Emoji sắc nét
 */
public class EmojiPickerPopup extends JPopupMenu {
    private static final String[] EMOJIS = {
        "😊", "😂", "🤣", "❤️", "👍", "🔥", "🎉", "🚀",
        "😍", "🥰", "😎", "💡", "👏", "😢", "😡", "🎯",
        "💬", "🌟", "⭐", "💯", "🤝", "☕", "💻", "📱",
        "🎵", "🎥", "📂", "🔒", "✨", "🎈", "🐱", "🍕"
    };

    public EmojiPickerPopup(Consumer<String> onSelectEmoji) {
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 210, 220), 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));
        setBackground(Color.WHITE);

        JPanel panel = new JPanel(new GridLayout(4, 8, 4, 4));
        panel.setBackground(Color.WHITE);

        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 18);

        for (String emoji : EMOJIS) {
            JButton btn = new JButton(emoji);
            btn.setFont(emojiFont);
            btn.setMargin(new Insets(0, 0, 0, 0));
            btn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            btn.setPreferredSize(new Dimension(42, 38));
            btn.setHorizontalAlignment(SwingConstants.CENTER);
            btn.setVerticalAlignment(SwingConstants.CENTER);
            btn.setFocusPainted(false);
            btn.setFocusable(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setContentAreaFilled(true);
                    btn.setBackground(new Color(230, 240, 255));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setContentAreaFilled(false);
                }
            });

            btn.addActionListener(e -> {
                onSelectEmoji.accept(emoji);
                setVisible(false);
            });

            panel.add(btn);
        }

        add(panel);
    }
}
