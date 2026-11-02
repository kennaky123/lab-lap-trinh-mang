package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;
import mail.common.MailConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao diện Quản trị Mail Server (SMTP + POP3 + CSDL)
 */
public class MailServerGUI extends JFrame implements MailServer.MailServerListener {
    private MailServer server;

    private JTextField smtpPortField;
    private JTextField pop3PortField;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel accountCountLabel;
    private JLabel emailCountLabel;

    private JTextArea logArea;
    private DefaultTableModel accountTableModel;
    private DefaultTableModel emailTableModel;
    private DefaultTableModel logTableModel;

    public MailServerGUI() {
        setTitle("MÁY CHỦ MAIL SERVER (SMTP 2525 & POP3 1110) - QUẢN TRỊ CSDL");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(245, 247, 250));

        // 1. TOP HEADER & DASHBOARD
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("BẢNG ĐIỀU KHIỂN MAIL SERVER (SMTP & POP3)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(217, 48, 37)); // Màu đỏ phong cách Gmail

        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ctrlPanel.setOpaque(false);

        ctrlPanel.add(new JLabel("SMTP Port:"));
        smtpPortField = new JTextField(String.valueOf(MailConfig.LOCAL_SMTP_PORT), 4);
        ctrlPanel.add(smtpPortField);

        ctrlPanel.add(new JLabel("POP3 Port:"));
        pop3PortField = new JTextField(String.valueOf(MailConfig.LOCAL_POP3_PORT), 4);
        ctrlPanel.add(pop3PortField);

        startBtn = new JButton("Khởi Động Server");
        startBtn.setBackground(new Color(40, 167, 69));
        startBtn.setForeground(Color.WHITE);
        startBtn.setContentAreaFilled(false);
        startBtn.setOpaque(true);
        startBtn.setFocusPainted(false);
        startBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        startBtn.addActionListener(e -> startServer());
        ctrlPanel.add(startBtn);

        stopBtn = new JButton("Dừng Server");
        stopBtn.setBackground(new Color(220, 53, 69));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setContentAreaFilled(false);
        stopBtn.setOpaque(true);
        stopBtn.setFocusPainted(false);
        stopBtn.setEnabled(false);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        stopBtn.addActionListener(e -> stopServer());
        ctrlPanel.add(stopBtn);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(ctrlPanel, BorderLayout.EAST);

        // Stats Cards Bar
        JPanel statsBar = new JPanel(new GridLayout(1, 3, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái Máy Chủ", "Đang Dừng", new Color(108, 117, 125));
        accountCountLabel = createStatCard("Tài Khoản Hộp Thư", "0 Tài Khoản", new Color(0, 123, 255));
        emailCountLabel = createStatCard("Tổng Thư Trong CSDL", "0 Thư", new Color(217, 48, 37));

        statsBar.add(statusLabel);
        statsBar.add(accountCountLabel);
        statsBar.add(emailCountLabel);

        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.setOpaque(false);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 2. TABS CONTENT
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Tab 1: Console Logs
        JPanel logTab = new JPanel(new BorderLayout(8, 8));
        logTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(28, 30, 36));
        logArea.setForeground(new Color(225, 230, 240));
        JScrollPane logScroll = new JScrollPane(logArea);

        JPanel logBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = new JButton("Xóa Màn Hình Log");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logBtnBar.add(clearLogBtn);

        logTab.add(logScroll, BorderLayout.CENTER);
        logTab.add(logBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Nhật Ký Giao Thức (SMTP / POP3)  ", logTab);

        // Tab 2: Quản lý CSDL Tài khoản
        JPanel accTab = new JPanel(new BorderLayout(8, 8));
        accTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        accountTableModel = new DefaultTableModel(new String[]{"Email Hộp Thư", "Tên Hiển Thị", "Mật Khẩu", "Trạng Thái"}, 0);
        JTable accTable = new JTable(accountTableModel);
        accTable.setRowHeight(26);
        accTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        accTab.add(new JScrollPane(accTable), BorderLayout.CENTER);

        JPanel accActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton addAccBtn = new JButton("+ Thêm Tài Khoản Mới");
        addAccBtn.addActionListener(e -> showAddAccountDialog());
        JButton delAccBtn = new JButton("Xóa Tài Khoản");
        delAccBtn.addActionListener(e -> deleteSelectedAccount(accTable));
        JButton refreshAccBtn = new JButton("Làm Mới");
        refreshAccBtn.addActionListener(e -> loadAccountsToTable());

        accActions.add(addAccBtn);
        accActions.add(delAccBtn);
        accActions.add(refreshAccBtn);
        accTab.add(accActions, BorderLayout.SOUTH);
        tabbedPane.addTab("  CSDL Tài Khoản Email  ", accTab);

        // Tab 3: CSDL Thư Điện Tử
        JPanel emailTab = new JPanel(new BorderLayout(8, 8));
        emailTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        emailTableModel = new DefaultTableModel(new String[]{"Thời Gian", "Người Gửi (From)", "Người Nhận (To)", "Tiêu Đề (Subject)", "Kích Thước", "Đính Kèm"}, 0);
        JTable emailTable = new JTable(emailTableModel);
        emailTable.setRowHeight(24);
        emailTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        emailTab.add(new JScrollPane(emailTable), BorderLayout.CENTER);

        JPanel emailBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshEmailBtn = new JButton("Làm Mới Danh Sách Thư CSDL");
        refreshEmailBtn.addActionListener(e -> loadEmailsToTable());
        emailBar.add(refreshEmailBtn);
        emailTab.add(emailBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  CSDL Thư Điện Tử (Stored Emails)  ", emailTab);

        // Tab 4: CSDL Lịch Sử Giao Dịch
        JPanel logDbTab = new JPanel(new BorderLayout(8, 8));
        logDbTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        logTableModel = new DefaultTableModel(new String[]{"Thời Gian", "Giao Thức", "Tài Khoản", "Hành Động", "Chi Tiết", "Kết Quả"}, 0);
        JTable logDbTable = new JTable(logTableModel);
        logDbTable.setRowHeight(24);
        logDbTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        logDbTab.add(new JScrollPane(logDbTable), BorderLayout.CENTER);

        JPanel logDbBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshLogDbBtn = new JButton("Làm Mới Lịch Sử Giao Dịch");
        refreshLogDbBtn.addActionListener(e -> loadLogsToTable());
        logDbBar.add(refreshLogDbBtn);
        logDbTab.add(logDbBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  CSDL Lịch Sử Giao Dịch (Audit Log)  ", logDbTab);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);

        loadAccountsToTable();
        loadEmailsToTable();
        loadLogsToTable();
        onStatsUpdated(
                MailDatabaseManager.getInstance().getAllAccounts().size(),
                MailDatabaseManager.getInstance().getTotalEmailCount()
        );
    }

    private JLabel createStatCard(String title, String value, Color color) {
        JLabel card = new JLabel("<html><div style='text-align: center; padding: 6px;'>"
                + "<span style='font-size: 10px; color: #555555;'>" + title + "</span><br>"
                + "<b style='font-size: 13px; color: " + toHex(color) + ";'>" + value + "</b>"
                + "</div></html>", SwingConstants.CENTER);
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(new Color(225, 230, 235), 1));
        return card;
    }

    private String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private void startServer() {
        int smtpPort, pop3Port;
        try {
            smtpPort = Integer.parseInt(smtpPortField.getText().trim());
            pop3Port = Integer.parseInt(pop3PortField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng phải là số nguyên!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new MailServer(smtpPort, pop3Port);
        server.addListener(this);

        if (server.start()) {
            int actualSmtp = server.getSmtpPort();
            int actualPop3 = server.getPop3Port();
            smtpPortField.setText(String.valueOf(actualSmtp));
            pop3PortField.setText(String.valueOf(actualPop3));
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            smtpPortField.setEditable(false);
            pop3PortField.setEditable(false);
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #28a745;'>Đang Chạy (SMTP " + actualSmtp + " / POP3 " + actualPop3 + ")</b></div></html>");
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            smtpPortField.setEditable(true);
            pop3PortField.setEditable(true);
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #dc3545;'>Đã Dừng</b></div></html>");
        }
    }

    private void loadAccountsToTable() {
        accountTableModel.setRowCount(0);
        List<MailAccount> list = MailDatabaseManager.getInstance().getAllAccounts();
        for (MailAccount a : list) {
            accountTableModel.addRow(new Object[]{a.getEmail(), a.getDisplayName(), a.getPassword(), a.isEnabled() ? "Hoạt Động" : "Khóa"});
        }
    }

    private void loadEmailsToTable() {
        emailTableModel.setRowCount(0);
        List<MailAccount> accounts = MailDatabaseManager.getInstance().getAllAccounts();
        for (MailAccount acc : accounts) {
            List<EmailMessage> list = MailDatabaseManager.getInstance().getEmailsForRecipient(acc.getEmail());
            for (EmailMessage m : list) {
                String attach = m.getAttachmentName() != null ? m.getAttachmentName() : "Không";
                emailTableModel.addRow(new Object[]{
                        m.getFormattedDate(), m.getFrom(), m.getTo(), m.getSubject(), m.getApproximateSize() + " B", attach
                });
            }
        }
    }

    private void loadLogsToTable() {
        logTableModel.setRowCount(0);
        List<MailDatabaseManager.MailLogEntry> logs = MailDatabaseManager.getInstance().getLogs(300);
        for (MailDatabaseManager.MailLogEntry l : logs) {
            logTableModel.addRow(new Object[]{
                    l.getFormattedTime(), l.protocol, l.user, l.action, l.detail, l.success ? "Thành Công" : "Thất Bại"
            });
        }
    }

    private void showAddAccountDialog() {
        JTextField emailF = new JTextField("user@local.com");
        JTextField nameF = new JTextField("Người Dùng");
        JPasswordField passF = new JPasswordField("123456");

        JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
        p.add(new JLabel("Địa chỉ Email:"));
        p.add(emailF);
        p.add(new JLabel("Tên hiển thị:"));
        p.add(nameF);
        p.add(new JLabel("Mật khẩu:"));
        p.add(passF);

        int res = JOptionPane.showConfirmDialog(this, p, "Tạo Tài Khoản Email Mới", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String email = emailF.getText().trim();
            String name = nameF.getText().trim();
            String pass = new String(passF.getPassword());
            if (!email.isEmpty() && !pass.isEmpty()) {
                MailDatabaseManager.getInstance().addAccount(new MailAccount(email, pass, name));
                loadAccountsToTable();
                if (server != null) server.notifyStats();
            }
        }
    }

    private void deleteSelectedAccount(JTable table) {
        int r = table.getSelectedRow();
        if (r == -1) return;
        String email = (String) accountTableModel.getValueAt(r, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa tài khoản " + email + "?", "Xác Nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            MailDatabaseManager.getInstance().deleteAccount(email);
            loadAccountsToTable();
            if (server != null) server.notifyStats();
        }
    }

    @Override
    public void onLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
            loadAccountsToTable();
            loadEmailsToTable();
            loadLogsToTable();
        });
    }

    @Override
    public void onStatsUpdated(int totalAccounts, int totalEmails) {
        SwingUtilities.invokeLater(() -> {
            accountCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Tài Khoản Hộp Thư</span><br>"
                    + "<b style='font-size: 13px; color: #007bff;'>" + totalAccounts + " Tài Khoản</b></div></html>");
            emailCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Tổng Thư Trong CSDL</span><br>"
                    + "<b style='font-size: 13px; color: #d93025;'>" + totalEmails + " Thư</b></div></html>");
            loadAccountsToTable();
            loadEmailsToTable();
            loadLogsToTable();
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MailServerGUI gui = new MailServerGUI();
            gui.setVisible(true);
            gui.startServer();
        });
    }
}

