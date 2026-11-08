package chatapp.ui;

import chatapp.client.AudioPlayer;
import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Hiển thị tin nhắn dạng Bong Bóng Chat (Chat Bubble) kiểu Zalo / Facebook Messenger:
 * - Hiển thị ảnh thu nhỏ (Thumbnail Preview) trực tiếp trong bong bóng chat
 * - Hiển thị card video chuyên nghiệp và nút phát video
 * - Khắc phục triệt để lỗi font và icon ô vuông trên Windows
 */
public class ChatBubblePanel extends JPanel {
    private final Message message;
    private final boolean isMe;

    public ChatBubblePanel(Message message, boolean isMe) {
        this.message = message;
        this.isMe = isMe;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(4, 10, 4, 10));

        if (message.getType() == MessageType.SYSTEM_NOTIFICATION) {
            renderSystemNotification();
        } else {
            renderUserMessage();
        }
    }

    private void renderSystemNotification() {
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerPanel.setOpaque(false);

        JLabel label = new JLabel(message.getContent() + "  (" + message.getFormattedTime() + ")");
        label.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        label.setForeground(new Color(110, 115, 125));
        label.setOpaque(true);
        label.setBackground(new Color(235, 238, 242));
        label.setBorder(new EmptyBorder(3, 10, 3, 10));

        centerPanel.add(label);
        add(centerPanel, BorderLayout.CENTER);
    }

    private void renderUserMessage() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        // 1. Tên người gửi & Thời gian
        JPanel metaPanel = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 0));
        metaPanel.setOpaque(false);

        if (!isMe) {
            JLabel senderLabel = new JLabel(message.getSender());
            senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            senderLabel.setForeground(new Color(24, 119, 242));
            metaPanel.add(senderLabel);
        }

        JLabel timeLabel = new JLabel(message.getFormattedTime());
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(new Color(150, 155, 165));
        metaPanel.add(timeLabel);

        wrapper.add(metaPanel);

        // 2. Nội dung bong bóng (Bubble)
        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isMe) {
                    g2.setColor(new Color(0, 132, 255)); // Xanh Messenger / Zalo
                } else {
                    g2.setColor(new Color(230, 233, 239)); // Xám sáng
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        Color textColor = isMe ? Color.WHITE : new Color(20, 25, 35);
        String fileName = message.getFileName() != null ? message.getFileName().toLowerCase() : "";
        boolean isImage = message.getType() == MessageType.IMAGE_MESSAGE 
                || fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif");
        boolean isVideo = message.getType() == MessageType.VIDEO_MESSAGE 
                || fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".mkv") || fileName.endsWith(".mov");

        if (isImage) {
            // ==================== HIỂN THỊ HÌNH ẢNH TRỰC TIẾP ====================
            ImageIcon icon = null;
            if (message.getFileData() != null) {
                icon = new ImageIcon(message.getFileData());
            } else {
                File f = new File(Config.DOWNLOADS_DIR, message.getFileName());
                if (f.exists()) {
                    icon = new ImageIcon(f.getAbsolutePath());
                }
            }

            if (icon != null && icon.getIconWidth() > 0) {
                // Thu nhỏ tỷ lệ ảnh cho vừa vặn bong bóng chat
                int originalW = icon.getIconWidth();
                int originalH = icon.getIconHeight();
                int maxW = 240;
                int maxH = 200;
                int targetW = originalW;
                int targetH = originalH;

                if (originalW > maxW || originalH > maxH) {
                    double scale = Math.min((double) maxW / originalW, (double) maxH / originalH);
                    targetW = (int) (originalW * scale);
                    targetH = (int) (originalH * scale);
                }

                Image scaledImg = icon.getImage().getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                JLabel imageLabel = new JLabel(new ImageIcon(scaledImg));
                imageLabel.setBorder(BorderFactory.createLineBorder(new Color(200, 205, 215), 1));
                imageLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                imageLabel.setToolTipText("Nhấp để mở xem ảnh kích thước gốc");
                imageLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        openFileLocally();
                    }
                });
                bubble.add(imageLabel);
            }

            JLabel imgNameLabel = new JLabel("Hình ảnh: " + message.getFileName() + " (" + message.getFormattedFileSize() + ")");
            imgNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            imgNameLabel.setForeground(textColor);
            bubble.add(Box.createVerticalStrut(4));
            bubble.add(imgNameLabel);

            JButton openImgBtn = new JButton("Mở Xem Ảnh Gốc");
            openImgBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            openImgBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openImgBtn.addActionListener(e -> openFileLocally());
            bubble.add(Box.createVerticalStrut(4));
            bubble.add(openImgBtn);

        } else if (isVideo) {
            // ==================== HIỂN THỊ VIDEO ====================
            JLabel vidLabel = new JLabel("Video: " + message.getFileName() + " (" + message.getFormattedFileSize() + ")");
            vidLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            vidLabel.setForeground(textColor);
            bubble.add(vidLabel);

            JButton openVidBtn = new JButton("Phát / Mở Video");
            openVidBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            openVidBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openVidBtn.addActionListener(e -> openFileLocally());
            bubble.add(Box.createVerticalStrut(6));
            bubble.add(openVidBtn);

        } else if (message.getType() == MessageType.AUDIO_MESSAGE) {
            // ==================== PHÁT ÂM THANH ====================
            JButton playBtn = new JButton("▶ Nghe Voice (" + message.getFormattedFileSize() + ")");
            playBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
            playBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            playBtn.setFocusPainted(false);
            playBtn.setContentAreaFilled(false);
            playBtn.setOpaque(true);
            playBtn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            if (isMe) {
                playBtn.setBackground(new Color(0, 92, 190));
                playBtn.setForeground(Color.WHITE);
            } else {
                playBtn.setBackground(Color.WHITE);
                playBtn.setForeground(new Color(0, 132, 255));
            }

            playBtn.addActionListener(e -> {
                if (message.getFileData() != null) {
                    AudioPlayer.play(message.getFileData());
                } else {
                    File localFile = new File(Config.DOWNLOADS_DIR, message.getFileName());
                    if (localFile.exists()) {
                        AudioPlayer.playFile(localFile);
                    }
                }
            });

            bubble.add(playBtn);

        } else if (message.getType() == MessageType.FILE_TRANSFER) {
            // ==================== TỆP TIN ĐÍNH KÈM ====================
            JLabel fileLabel = new JLabel("Tệp: " + message.getFileName() + " (" + message.getFormattedFileSize() + ")");
            fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            fileLabel.setForeground(textColor);
            bubble.add(fileLabel);

            JButton openFileBtn = new JButton("Mở Tệp Tin");
            openFileBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            openFileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            openFileBtn.addActionListener(e -> openFileLocally());
            bubble.add(Box.createVerticalStrut(4));
            bubble.add(openFileBtn);

        } else {
            // ==================== TIN NHẮN VĂN BẢN / EMOJI ====================
            JTextArea textArea = new JTextArea(message.getContent());
            textArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            textArea.setForeground(textColor);
            textArea.setOpaque(false);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(null);

            int maxWidth = 380;
            FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
            int textWidth = fm.stringWidth(message.getContent());
            if (textWidth < maxWidth && !message.getContent().contains("\n")) {
                textArea.setColumns(Math.max(2, message.getContent().length() + 1));
            } else {
                textArea.setSize(new Dimension(maxWidth, Short.MAX_VALUE));
            }

            bubble.add(textArea);
        }

        // Căn lề Trái hoặc Phải
        JPanel alignPanel = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        alignPanel.setOpaque(false);
        alignPanel.add(bubble);

        wrapper.add(alignPanel);

        if (isMe) {
            add(wrapper, BorderLayout.EAST);
        } else {
            add(wrapper, BorderLayout.WEST);
        }
    }

    /**
     * Mở file đính kèm bằng ứng dụng mặc định của hệ điều hành
     */
    private void openFileLocally() {
        try {
            File dir = new File(Config.DOWNLOADS_DIR);
            if (!dir.exists() && new File("lap-trinh-mang/chat-swing-app/downloads").exists()) {
                dir = new File("lap-trinh-mang/chat-swing-app/downloads");
            } else if (!dir.exists()) {
                dir.mkdirs();
            }

            File targetFile = new File(dir, message.getFileName());
            if (!targetFile.exists() && message.getFileData() != null) {
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(message.getFileData());
                }
            }

            if (targetFile.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(targetFile);
                } else {
                    JOptionPane.showMessageDialog(this, "Đã lưu tại: " + targetFile.getAbsolutePath(), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Không tìm thấy file trên đĩa!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không thể mở file: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
