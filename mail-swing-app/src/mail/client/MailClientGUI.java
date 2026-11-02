package mail.client;

import mail.common.EmailMessage;
import mail.common.MailConfig;
import mail.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Giao diện Mail Client phong cách Gmail hiện đại:
 * - Hỗ trợ cả 2 chế độ: Localhost Server (cổng 2525/1110) và Google Gmail thật (cổng 465/995)
 * - Thanh danh mục Gmail (Hộp thư đến, Đã gửi, Soạn thư)
 * - Đọc thư chi tiết và gửi thư kèm tệp đính kèm
 * - Nhật ký lệnh giao thức SMTP / POP3 trực quan
 */
public class MailClientGUI extends JFrame implements SmtpClient.SmtpLogListener, Pop3Client.Pop3LogListener {

    private final SmtpClient smtpClient = new SmtpClient();
    private final Pop3Client pop3Client = new Pop3Client();

    // Cấu hình tài khoản hiện tại
    private boolean isGmailMode = false;
    private String currentUser = "alice@local.com";
    private String currentPass = "123456";
    private String serverHost = "localhost";
    private int smtpPort = MailConfig.LOCAL_SMTP_PORT;
    private int pop3Port = MailConfig.LOCAL_POP3_PORT;

    // Danh sách thư
    private final List<EmailMessage> inboxList = new ArrayList<>();
    private final List<EmailMessage> sentList = new ArrayList<>();
    private String currentFolder = "INBOX"; // "INBOX" hoặc "SENT"

    // UI Components
    private JLabel currentAccountLabel;
    private JComboBox<String> modeComboBox;
    private DefaultTableModel mailTableModel;
    private JTable mailTable;

    // Reading Pane Components
    private JLabel viewSubjectLabel;
    private JLabel viewFromLabel;
    private JLabel viewToLabel;
    private JLabel viewDateLabel;
    private JTextArea viewBodyArea;
    private JButton downloadAttachBtn;
    private EmailMessage selectedMessage;

    // Protocol Log Console
    private JTextArea protocolLogArea;
    private JLabel statusLabel;
    private boolean updatingComboBox = false;
    private long lastKnownEmailsModTime = 0L;

    public MailClientGUI() {
        this("alice@local.com");
    }

    public MailClientGUI(String initialUser) {
        if (initialUser != null && !initialUser.trim().isEmpty()) {
            String clean = initialUser.trim().toLowerCase();
            if (!clean.contains("@")) clean = clean + "@local.com";
            this.currentUser = clean;
        }
        setTitle("GMAIL CLIENT (JAVA SWING) - [" + currentUser + "] - GIAO THỨC SMTP & POP3");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        smtpClient.setLogListener(this);
        pop3Client.setLogListener(this);

        initUI();
        syncModeComboBox();
        refreshInbox();
        startAutoRefreshTimer();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(new Color(246, 248, 252));

        // ==================== 1. TOP HEADER (PHONG CÁCH GMAIL) ====================
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 230, 238)),
                new EmptyBorder(10, 16, 10, 16)
        ));

        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandPanel.setOpaque(false);
        JLabel logoLabel = new JLabel("✉ Gmail");
        logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        logoLabel.setForeground(new Color(234, 67, 53)); // Đỏ Google
        brandPanel.add(logoLabel);

        JLabel subTitle = new JLabel("|  Client Đa Năng (Localhost & Gmail Thật)");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subTitle.setForeground(new Color(110, 115, 125));
        brandPanel.add(subTitle);

        JPanel accountPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        accountPanel.setOpaque(false);

        accountPanel.add(new JLabel("Chế Độ:"));
        modeComboBox = new JComboBox<>(new String[]{
                "Localhost Server (alice@local.com)",
                "Localhost Server (bob@local.com)",
                "Google Gmail Thật (smtp.gmail.com)"
        });
        modeComboBox.addActionListener(e -> switchAccountMode());
        accountPanel.add(modeComboBox);

        currentAccountLabel = new JLabel("Tài khoản: " + currentUser);
        currentAccountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        currentAccountLabel.setForeground(new Color(26, 115, 232));
        accountPanel.add(currentAccountLabel);

        JButton refreshBtn = new JButton("Làm Mới / Nhận Thư");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        refreshBtn.addActionListener(e -> refreshInbox());
        accountPanel.add(refreshBtn);

        headerPanel.add(brandPanel, BorderLayout.WEST);
        headerPanel.add(accountPanel, BorderLayout.EAST);
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // ==================== 2. MAIN BODY (SIDEBAR + SPLIT VIEW) ====================
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);

        // Sidebar bên trái
        JPanel sidebar = createSidebar();
        centerPanel.add(sidebar, BorderLayout.WEST);

        // Khung nội dung chính (Danh sách thư + Đọc thư)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.45);
        splitPane.setBorder(null);

        // Nửa trên: Bảng danh sách thư
        JPanel mailListPanel = createMailListPanel();
        splitPane.setTopComponent(mailListPanel);

        // Nửa dưới: Khung đọc thư chi tiết
        JPanel readingPanel = createReadingPane();
        splitPane.setBottomComponent(readingPanel);

        centerPanel.add(splitPane, BorderLayout.CENTER);
        rootPanel.add(centerPanel, BorderLayout.CENTER);

        // ==================== 3. BOTTOM LOG BAR ====================
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(Color.WHITE);
        bottomContainer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 230, 238)));

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        statusPanel.setOpaque(false);
        statusLabel = new JLabel("Trạng thái: Sẵn sàng");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusPanel.add(statusLabel);

        // Khung log thu gọn
        protocolLogArea = new JTextArea(3, 80);
        protocolLogArea.setEditable(false);
        protocolLogArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        protocolLogArea.setBackground(new Color(30, 32, 38));
        protocolLogArea.setForeground(new Color(210, 215, 225));
        JScrollPane logScroll = new JScrollPane(protocolLogArea);
        logScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        bottomContainer.add(statusPanel, BorderLayout.NORTH);
        bottomContainer.add(logScroll, BorderLayout.CENTER);
        rootPanel.add(bottomContainer, BorderLayout.SOUTH);

        add(rootPanel);
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBackground(new Color(246, 248, 252));
        sidebar.setBorder(new EmptyBorder(16, 12, 16, 12));

        // Nút SOẠN THƯ (Compose) nổi bật phong cách Gmail
        JButton composeBtn = new JButton("+  Soạn Thư Mới");
        composeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        composeBtn.setForeground(new Color(32, 33, 36));
        composeBtn.setBackground(new Color(194, 231, 255)); // Xanh nhạt Google
        composeBtn.setFocusPainted(false);
        composeBtn.setMaximumSize(new Dimension(190, 48));
        composeBtn.setPreferredSize(new Dimension(190, 48));
        composeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        composeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 215, 245), 1),
                new EmptyBorder(8, 16, 8, 16)
        ));
        composeBtn.addActionListener(e -> showComposeDialog());

        sidebar.add(composeBtn);
        sidebar.add(Box.createVerticalStrut(20));

        // Các mục danh mục
        sidebar.add(createNavButton("•  Hộp Thư Đến (Inbox)", () -> {
            currentFolder = "INBOX";
            refreshMailTable();
        }));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("•  Thư Đã Gửi (Sent)", () -> {
            currentFolder = "SENT";
            refreshMailTable();
        }));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("•  Cấu Hình Tài Khoản", this::promptAccountConfig));

        return sidebar;
    }

    private JButton createNavButton(String text, Runnable action) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(new Color(50, 55, 65));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));
        btn.setMaximumSize(new Dimension(190, 36));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> action.run());

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setContentAreaFilled(true);
                btn.setBackground(new Color(230, 235, 245));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setContentAreaFilled(false);
            }
        });
        return btn;
    }

    private JPanel createMailListPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));

        mailTableModel = new DefaultTableModel(new String[]{"Người Gửi / Nhận", "Tiêu Đề Thư", "Thời Gian", "Đính Kèm"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        mailTable = new JTable(mailTableModel);
        mailTable.setRowHeight(28);
        mailTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        mailTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        mailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        mailTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = mailTable.getSelectedRow();
                if (row != -1) {
                    List<EmailMessage> list = "INBOX".equals(currentFolder) ? inboxList : sentList;
                    if (row < list.size()) {
                        displayEmailDetails(list.get(row));
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(mailTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(225, 230, 238), 1));
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createReadingPane() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // Header thư
        JPanel metaPanel = new JPanel(new GridLayout(4, 1, 2, 2));
        metaPanel.setOpaque(false);

        viewSubjectLabel = new JLabel("Chọn một bức thư để đọc nội dung...");
        viewSubjectLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        viewSubjectLabel.setForeground(new Color(32, 33, 36));

        viewFromLabel = new JLabel("");
        viewFromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        viewFromLabel.setForeground(new Color(95, 99, 104));

        viewToLabel = new JLabel("");
        viewToLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        viewToLabel.setForeground(new Color(95, 99, 104));

        viewDateLabel = new JLabel("");
        viewDateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        viewDateLabel.setForeground(new Color(130, 135, 145));

        metaPanel.add(viewSubjectLabel);
        metaPanel.add(viewFromLabel);
        metaPanel.add(viewToLabel);
        metaPanel.add(viewDateLabel);

        // Attachment Button Bar
        downloadAttachBtn = new JButton("📎 Tải tệp đính kèm về máy");
        downloadAttachBtn.setVisible(false);
        downloadAttachBtn.addActionListener(e -> saveAttachmentLocally());

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.add(metaPanel, BorderLayout.CENTER);
        headerWrap.add(downloadAttachBtn, BorderLayout.EAST);

        panel.add(headerWrap, BorderLayout.NORTH);

        // Body thư
        viewBodyArea = new JTextArea();
        viewBodyArea.setEditable(false);
        viewBodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        viewBodyArea.setLineWrap(true);
        viewBodyArea.setWrapStyleWord(true);
        viewBodyArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 235, 240), 1),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JScrollPane bodyScroll = new JScrollPane(viewBodyArea);
        bodyScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        panel.add(bodyScroll, BorderLayout.CENTER);

        return panel;
    }

    private void displayEmailDetails(EmailMessage msg) {
        this.selectedMessage = msg;
        viewSubjectLabel.setText(msg.getSubject());
        viewFromLabel.setText("Từ: " + msg.getFrom());
        viewToLabel.setText("Đến: " + msg.getTo());
        viewDateLabel.setText("Thời gian: " + msg.getFormattedDate());
        viewBodyArea.setText(msg.getBody());

        if (msg.getAttachmentName() != null) {
            downloadAttachBtn.setText("📎 Tải: " + msg.getAttachmentName());
            downloadAttachBtn.setVisible(true);
        } else {
            downloadAttachBtn.setVisible(false);
        }
    }

    private void saveAttachmentLocally() {
        if (selectedMessage == null || selectedMessage.getAttachmentName() == null) return;
        try {
            File dir = new File(MailConfig.ATTACHMENTS_DIR);
            if (!dir.exists()) dir.mkdirs();

            File dest = new File(dir, selectedMessage.getAttachmentName());
            if (selectedMessage.getAttachmentData() != null) {
                Files.write(dest.toPath(), selectedMessage.getAttachmentData());
                JOptionPane.showMessageDialog(this, "Đã lưu tệp tại: " + dest.getAbsolutePath(), "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(dest);
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi lưu tệp: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== NHẬN THƯ (POP3) ====================

    private void refreshInbox() {
        statusLabel.setText("Đang tải thư qua giao thức POP3...");
        new Thread(() -> {
            List<EmailMessage> list;
            if (isGmailMode) {
                list = pop3Client.fetchGmailEmails(currentUser, currentPass, 10);
            } else {
                list = pop3Client.fetchLocalEmails(serverHost, pop3Port, currentUser, currentPass);
            }

            SwingUtilities.invokeLater(() -> {
                inboxList.clear();
                inboxList.addAll(list);
                refreshMailTable();
                statusLabel.setText("Đã nạp xong " + inboxList.size() + " thư qua POP3");
            });
        }).start();
    }

    private void refreshMailTable() {
        mailTableModel.setRowCount(0);
        List<EmailMessage> list = "INBOX".equals(currentFolder) ? inboxList : sentList;
        for (EmailMessage m : list) {
            String contact = "INBOX".equals(currentFolder) ? m.getFrom() : m.getTo();
            String attach = m.getAttachmentName() != null ? "📎 " + m.getAttachmentName() : "-";
            mailTableModel.addRow(new Object[]{contact, m.getSubject(), m.getFormattedDate(), attach});
        }
    }

    // ==================== SOẠN VÀ GỬI THƯ (SMTP) ====================

    private void showComposeDialog() {
        JDialog dialog = new JDialog(this, "Soạn Thư Mới (Giao thức SMTP)", true);
        dialog.setSize(620, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setBackground(Color.WHITE);

        // Fields
        JPanel top = new JPanel(new GridLayout(3, 2, 6, 6));
        top.setOpaque(false);

        String defaultRecipient = "bob@local.com".equalsIgnoreCase(currentUser) ? "alice@local.com" : "bob@local.com";
        JTextField toField = new JTextField(defaultRecipient);
        JTextField subjectField = new JTextField("Thư thử nghiệm từ " + currentUser);
        JLabel attachInfoLabel = new JLabel("Chưa đính kèm tệp");

        final File[] chosenFile = new File[1];

        JButton chooseAttachBtn = new JButton("📎 Đính Kèm Tệp...");
        chooseAttachBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int r = chooser.showOpenDialog(dialog);
            if (r == JFileChooser.APPROVE_OPTION) {
                chosenFile[0] = chooser.getSelectedFile();
                attachInfoLabel.setText("📎 " + chosenFile[0].getName() + " (" + chosenFile[0].length() + " B)");
            }
        });

        top.add(new JLabel("Người nhận (To):"));
        top.add(toField);
        top.add(new JLabel("Tiêu đề (Subject):"));
        top.add(subjectField);
        top.add(chooseAttachBtn);
        top.add(attachInfoLabel);

        panel.add(top, BorderLayout.NORTH);

        // Body
        JTextArea bodyArea = new JTextArea();
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bodyArea.setText("Xin chào,\n\nĐây là bức thư được gửi qua giao thức mạng SMTP.\nNội dung thư được truyền tải theo chuẩn RFC 5321.");
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 225, 230), 1));
        panel.add(bodyScroll, BorderLayout.CENTER);

        // Bottom send button
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnBar.setOpaque(false);

        JButton sendBtn = new JButton("Gửi Thư (Send)");
        sendBtn.setBackground(new Color(26, 115, 232));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        sendBtn.addActionListener(e -> {
            String to = toField.getText().trim();
            String subject = subjectField.getText().trim();
            String body = bodyArea.getText();
            if (to.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập địa chỉ người nhận!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            dialog.dispose();
            statusLabel.setText("Đang gửi thư qua giao thức SMTP...");

            new Thread(() -> {
                String attachName = null;
                byte[] attachData = null;
                if (chosenFile[0] != null && chosenFile[0].exists()) {
                    attachName = chosenFile[0].getName();
                    try {
                        attachData = Files.readAllBytes(chosenFile[0].toPath());
                    } catch (Exception ignored) {}
                }

                boolean ok;
                if (isGmailMode) {
                    ok = smtpClient.sendGmail(currentUser, currentPass, to, subject, body, attachName, attachData);
                } else {
                    ok = smtpClient.sendLocalMail(serverHost, smtpPort, currentUser, to, subject, body, attachName, attachData);
                }

                final String finalAttachName = attachName;
                final byte[] finalAttachData = attachData;
                final boolean success = ok;
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        EmailMessage sentMsg = new EmailMessage(currentUser, to, subject, body);
                        sentMsg.setAttachmentName(finalAttachName);
                        sentMsg.setAttachmentData(finalAttachData);
                        sentList.add(sentMsg);
                        if ("SENT".equals(currentFolder)) {
                            refreshMailTable();
                        }

                        statusLabel.setText("Gửi thư thành công qua SMTP!");
                        JOptionPane.showMessageDialog(this, "Đã gửi thư thành công qua giao thức SMTP!", "Thông Báo", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Gửi thư thất bại!");
                        String errHint = isGmailMode
                                ? "Gửi thư thất bại! Vui lòng kiểm tra địa chỉ Gmail và Mật khẩu ứng dụng (16 ký tự App Password)."
                                : "Gửi thư thất bại! Vui lòng kiểm tra Mail Server đã được khởi động tại " + serverHost + ":" + smtpPort + " chưa.";
                        JOptionPane.showMessageDialog(this, errHint, "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }).start();
        });

        btnBar.add(sendBtn);
        panel.add(btnBar, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void switchAccountMode() {
        if (updatingComboBox) return;
        int idx = modeComboBox.getSelectedIndex();
        if (idx == 0) {
            isGmailMode = false;
            currentUser = "alice@local.com";
            currentPass = "123456";
        } else if (idx == 1) {
            isGmailMode = false;
            currentUser = "bob@local.com";
            currentPass = "123456";
        } else {
            promptGmailLogin();
        }
        currentFolder = "INBOX";
        setTitle("GMAIL CLIENT (JAVA SWING) - [" + currentUser + "] - GIAO THỨC SMTP & POP3");
        currentAccountLabel.setText("Tài khoản: " + currentUser);
        refreshInbox();
    }

    private void syncModeComboBox() {
        updatingComboBox = true;
        try {
            if (isGmailMode) {
                modeComboBox.setSelectedIndex(2);
            } else if ("alice@local.com".equalsIgnoreCase(currentUser)) {
                modeComboBox.setSelectedIndex(0);
            } else if ("bob@local.com".equalsIgnoreCase(currentUser)) {
                modeComboBox.setSelectedIndex(1);
            }
        } finally {
            updatingComboBox = false;
        }
    }

    private void startAutoRefreshTimer() {
        lastKnownEmailsModTime = getEmailsFileModTime();
        Timer timer = new Timer(1500, e -> {
            if (!isGmailMode) {
                long mod = getEmailsFileModTime();
                if (mod > lastKnownEmailsModTime) {
                    lastKnownEmailsModTime = mod;
                    refreshInbox();
                }
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private long getEmailsFileModTime() {
        File f1 = new File(MailConfig.DATA_DIR, "emails.dat");
        if (f1.exists()) return f1.lastModified();
        File f2 = new File("lap-trinh-mang/mail-swing-app/mail_data/emails.dat");
        if (f2.exists()) return f2.lastModified();
        return 0L;
    }

    private void promptGmailLogin() {
        JTextField gmailField = new JTextField("example@gmail.com");
        JPasswordField appPassField = new JPasswordField();

        JPanel p = new JPanel(new GridLayout(3, 1, 4, 4));
        p.add(new JLabel("Nhập địa chỉ Gmail:"));
        p.add(gmailField);
        p.add(new JLabel("Mật khẩu ứng dụng Google (16 ký tự App Password):"));
        p.add(appPassField);

        int r = JOptionPane.showConfirmDialog(this, p, "Đăng Nhập Gmail Thật", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            isGmailMode = true;
            currentUser = gmailField.getText().trim();
            currentPass = new String(appPassField.getPassword()).trim();
        } else {
            syncModeComboBox();
        }
    }

    private void promptAccountConfig() {
        JTextField uField = new JTextField(currentUser);
        JPasswordField pField = new JPasswordField(currentPass);
        JTextField hostF = new JTextField(serverHost);
        JTextField smtpF = new JTextField(String.valueOf(smtpPort));
        JTextField pop3F = new JTextField(String.valueOf(pop3Port));

        JPanel p = new JPanel(new GridLayout(5, 2, 6, 6));
        p.add(new JLabel("Tài khoản Email:"));
        p.add(uField);
        p.add(new JLabel("Mật khẩu:"));
        p.add(pField);
        p.add(new JLabel("Server Host:"));
        p.add(hostF);
        p.add(new JLabel("SMTP Port:"));
        p.add(smtpF);
        p.add(new JLabel("POP3 Port:"));
        p.add(pop3F);

        int r = JOptionPane.showConfirmDialog(this, p, "Cấu Hình Kết Nối Mail", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            String enteredUser = uField.getText().trim().toLowerCase();
            if (!enteredUser.isEmpty() && !enteredUser.contains("@")) {
                enteredUser = enteredUser + "@local.com";
            }
            currentUser = enteredUser;
            currentPass = new String(pField.getPassword());
            serverHost = hostF.getText().trim();
            isGmailMode = currentUser.endsWith("@gmail.com");
            try {
                smtpPort = Integer.parseInt(smtpF.getText().trim());
                pop3Port = Integer.parseInt(pop3F.getText().trim());
            } catch (Exception ignored) {}
            currentFolder = "INBOX";
            setTitle("GMAIL CLIENT (JAVA SWING) - [" + currentUser + "] - GIAO THỨC SMTP & POP3");
            currentAccountLabel.setText("Tài khoản: " + currentUser);
            syncModeComboBox();
            refreshInbox();
        }
    }

    // ==================== PROTOCOL LOG CALLBACKS ====================

    @Override
    public void onCommand(String cmd) {
        SwingUtilities.invokeLater(() -> {
            protocolLogArea.append(">> " + cmd + "\n");
            protocolLogArea.setCaretPosition(protocolLogArea.getDocument().getLength());
        });
    }

    @Override
    public void onResponse(String resp) {
        SwingUtilities.invokeLater(() -> {
            protocolLogArea.append("<< " + resp + "\n");
            protocolLogArea.setCaretPosition(protocolLogArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        final String initialUser = (args != null && args.length > 0) ? args[0] : "alice@local.com";
        SwingUtilities.invokeLater(() -> {
            MailClientGUI gui = new MailClientGUI(initialUser);
            gui.setVisible(true);
        });
    }
}
