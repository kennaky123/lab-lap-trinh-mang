package mail.server;

import mail.common.EmailMessage;
import mail.common.MailAccount;
import mail.common.MailConfig;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
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

    private JComboBox<String> tbAccountCombo;
    private JTextArea tbInfoArea;
    private JTextArea tbTestResultArea;

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

        // Tab 5: Kết nối Mozilla Thunderbird
        tabbedPane.addTab("  Kết Nối Thunderbird  ", createThunderbirdTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);

        loadAccountsToTable();
        loadEmailsToTable();
        loadLogsToTable();
        refreshThunderbirdInfo();
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
            refreshThunderbirdInfo();
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
            refreshThunderbirdInfo();
        }
    }

    private JPanel createThunderbirdTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topBar.add(new JLabel("Chọn tài khoản dùng trên Thunderbird:"));
        tbAccountCombo = new JComboBox<>();
        tbAccountCombo.setPreferredSize(new Dimension(240, 26));
        tbAccountCombo.addActionListener(e -> refreshThunderbirdInfo());
        topBar.add(tbAccountCombo);

        JButton copyBtn = new JButton("Sao Chép Thông Số");
        copyBtn.addActionListener(e -> {
            if (tbInfoArea != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(tbInfoArea.getText()), null);
                JOptionPane.showMessageDialog(this, "Đã sao chép cấu hình kết nối Thunderbird vào Clipboard!");
            }
        });
        topBar.add(copyBtn);

        JButton testBtn = new JButton("Kiểm Tra Chuẩn Thunderbird (SMTP AUTH & POP3 CAPA/UIDL)");
        testBtn.setBackground(new Color(0, 123, 255));
        testBtn.setForeground(Color.WHITE);
        testBtn.setContentAreaFilled(false);
        testBtn.setOpaque(true);
        testBtn.setFocusPainted(false);
        testBtn.addActionListener(e -> runThunderbirdProtocolTest());
        topBar.add(testBtn);

        JButton launchTbBtn = new JButton("Mở Thunderbird");
        launchTbBtn.addActionListener(e -> launchThunderbird());
        topBar.add(launchTbBtn);

        panel.add(topBar, BorderLayout.NORTH);

        tbInfoArea = new JTextArea();
        tbInfoArea.setEditable(false);
        tbInfoArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        tbInfoArea.setBackground(Color.WHITE);
        tbInfoArea.setBorder(new EmptyBorder(8, 10, 8, 10));

        tbTestResultArea = new JTextArea();
        tbTestResultArea.setEditable(false);
        tbTestResultArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        tbTestResultArea.setBackground(new Color(28, 30, 36));
        tbTestResultArea.setForeground(new Color(180, 240, 190));
        tbTestResultArea.setText("Nhấn 'Kiểm Tra Chuẩn Thunderbird' để kiểm thử bắt tay SMTP AUTH & POP3 CAPA/UIDL trực tiếp...\n");

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tbInfoArea), new JScrollPane(tbTestResultArea));
        split.setResizeWeight(0.62);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void refreshThunderbirdInfo() {
        if (tbAccountCombo == null || tbInfoArea == null) return;

        List<MailAccount> accounts = MailDatabaseManager.getInstance().getAllAccounts();
        boolean itemsChanged = tbAccountCombo.getItemCount() != accounts.size();
        if (!itemsChanged) {
            for (int i = 0; i < accounts.size(); i++) {
                if (!accounts.get(i).getEmail().equals(tbAccountCombo.getItemAt(i))) {
                    itemsChanged = true;
                    break;
                }
            }
        }
        if (itemsChanged) {
            Object prev = tbAccountCombo.getSelectedItem();
            tbAccountCombo.removeAllItems();
            for (MailAccount acc : accounts) {
                tbAccountCombo.addItem(acc.getEmail());
            }
            if (prev != null) {
                tbAccountCombo.setSelectedItem(prev);
            } else if (!accounts.isEmpty()) {
                tbAccountCombo.setSelectedIndex(0);
            }
        }

        Object selectedItem = tbAccountCombo.getSelectedItem();
        MailAccount selectedAcc = null;
        for (MailAccount acc : accounts) {
            if (selectedItem != null && selectedItem.equals(acc.getEmail())) {
                selectedAcc = acc;
                break;
            }
        }
        if (selectedAcc == null && !accounts.isEmpty()) {
            selectedAcc = accounts.get(0);
        }

        int smtpP = (server != null && server.isRunning()) ? server.getSmtpPort() : MailConfig.LOCAL_SMTP_PORT;
        int pop3P = (server != null && server.isRunning()) ? server.getPop3Port() : MailConfig.LOCAL_POP3_PORT;
        String lanIp = MailServer.getLocalLanIp();
        String email = selectedAcc != null ? selectedAcc.getEmail() : "alice@local.com";
        String pass = selectedAcc != null ? selectedAcc.getPassword() : "123456";
        String name = selectedAcc != null ? selectedAcc.getDisplayName() : "Alice Nguyễn";

        StringBuilder sb = new StringBuilder();
        sb.append("=== THÔNG SỐ KẾT NỐI MOZILLA THUNDERBIRD ===\n");
        sb.append("Trạng thái Server : ").append((server != null && server.isRunning()) ? "ĐANG CHẠY" : "CHƯA KHỞI ĐỘNG").append("\n\n");
        sb.append("1) Thông tin tài khoản:\n");
        sb.append("   - Your full name      : ").append(name).append("\n");
        sb.append("   - Email address       : ").append(email).append("\n");
        sb.append("   - Password            : ").append(pass).append("\n\n");
        sb.append("2) Chọn 'Configure manually' (Cấu hình thủ công) trong Thunderbird:\n");
        sb.append("   [INCOMING SERVER - POP3]\n");
        sb.append("   - Protocol            : POP3\n");
        sb.append("   - Hostname            : localhost   (hoặc IP LAN nếu khác máy: ").append(lanIp).append(")\n");
        sb.append("   - Port                : ").append(pop3P).append("\n");
        sb.append("   - Connection security : None (Không mã hóa)\n");
        sb.append("   - Authentication      : Normal password (Mật khẩu thông thường)\n");
        sb.append("   - Username            : ").append(email).append("\n\n");
        sb.append("   [OUTGOING SERVER - SMTP]\n");
        sb.append("   - Hostname            : localhost   (hoặc IP LAN nếu khác máy: ").append(lanIp).append(")\n");
        sb.append("   - Port                : ").append(smtpP).append("\n");
        sb.append("   - Connection security : None (Không mã hóa)\n");
        sb.append("   - Authentication      : Normal password (Mật khẩu thông thường)\n");
        sb.append("   - Username            : ").append(email).append("\n\n");
        sb.append("3) Lưu ý khi bấm 'Done':\n");
        sb.append("   - Thunderbird sẽ cảnh báo kết nối không mã hóa (No Encryption) -> Tích 'I understand the risks' -> Confirm.\n");
        sb.append("   - Server đã hỗ trợ đầy đủ: SMTP (EHLO, AUTH PLAIN/LOGIN, 8BITMIME, SMTPUTF8) & POP3 (CAPA, UIDL, TOP, AUTH PLAIN/LOGIN).\n");

        String newText = sb.toString();
        if (!newText.equals(tbInfoArea.getText())) {
            tbInfoArea.setText(newText);
            tbInfoArea.setCaretPosition(0);
        }
    }

    private void runThunderbirdProtocolTest() {
        if (server == null || !server.isRunning()) {
            JOptionPane.showMessageDialog(this, "Vui lòng khởi động Mail Server trước khi kiểm tra!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String selectedEmail = (String) tbAccountCombo.getSelectedItem();
        if (selectedEmail == null) return;
        MailAccount target = null;
        for (MailAccount a : MailDatabaseManager.getInstance().getAllAccounts()) {
            if (a.getEmail().equals(selectedEmail)) {
                target = a;
                break;
            }
        }
        if (target == null) return;
        final MailAccount acc = target;
        final int smtpP = server.getSmtpPort();
        final int pop3P = server.getPop3Port();

        tbTestResultArea.setText("Đang kiểm tra bắt tay chuẩn Mozilla Thunderbird với tài khoản " + acc.getEmail() + "...\n");
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            boolean smtpOk = false;
            boolean pop3Ok = false;

            // 1. Test SMTP EHLO + AUTH PLAIN
            try (Socket s = new Socket("127.0.0.1", smtpP);
                 BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
                s.setSoTimeout(5000);
                out.append("[SMTP] << ").append(r.readLine()).append("\n");
                w.write("EHLO thunderbird.local\r\n");
                w.flush();
                String line;
                boolean hasAuth = false;
                while ((line = r.readLine()) != null) {
                    out.append("[SMTP] << ").append(line).append("\n");
                    if (line.toUpperCase().contains("AUTH") && line.toUpperCase().contains("PLAIN")) hasAuth = true;
                    if (line.length() < 4 || line.charAt(3) != '-') break;
                }
                String plainToken = Base64.getEncoder().encodeToString(
                        ("\0" + acc.getEmail() + "\0" + acc.getPassword()).getBytes(StandardCharsets.UTF_8));
                w.write("AUTH PLAIN " + plainToken + "\r\n");
                w.flush();
                String authResp = r.readLine();
                out.append("[SMTP] >> AUTH PLAIN ********\n[SMTP] << ").append(authResp).append("\n");
                w.write("QUIT\r\n");
                w.flush();
                r.readLine();
                smtpOk = hasAuth && authResp != null && authResp.startsWith("235");
            } catch (Exception ex) {
                out.append("[SMTP LỖI] ").append(ex.getMessage()).append("\n");
            }

            // 2. Test POP3 CAPA + AUTH PLAIN + UIDL + STAT
            try (Socket s = new Socket("127.0.0.1", pop3P);
                 BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
                s.setSoTimeout(5000);
                out.append("\n[POP3] << ").append(r.readLine()).append("\n");
                w.write("CAPA\r\n");
                w.flush();
                String capLine;
                boolean hasUidl = false;
                while ((capLine = r.readLine()) != null) {
                    out.append("[POP3] << ").append(capLine).append("\n");
                    if ("UIDL".equalsIgnoreCase(capLine.trim())) hasUidl = true;
                    if (".".equals(capLine)) break;
                }
                String plainToken = Base64.getEncoder().encodeToString(
                        ("\0" + acc.getEmail() + "\0" + acc.getPassword()).getBytes(StandardCharsets.UTF_8));
                w.write("AUTH PLAIN " + plainToken + "\r\n");
                w.flush();
                String authResp = r.readLine();
                out.append("[POP3] >> AUTH PLAIN ********\n[POP3] << ").append(authResp).append("\n");

                w.write("UIDL\r\n");
                w.flush();
                String uidlResp = r.readLine();
                out.append("[POP3] >> UIDL\n[POP3] << ").append(uidlResp).append("\n");
                if (uidlResp != null && uidlResp.startsWith("+OK")) {
                    String uLine;
                    while ((uLine = r.readLine()) != null) {
                        out.append("   ").append(uLine).append("\n");
                        if (".".equals(uLine)) break;
                    }
                }
                w.write("QUIT\r\n");
                w.flush();
                r.readLine();
                pop3Ok = hasUidl && authResp != null && authResp.startsWith("+OK") && uidlResp != null && uidlResp.startsWith("+OK");
            } catch (Exception ex) {
                out.append("[POP3 LỖI] ").append(ex.getMessage()).append("\n");
            }

            out.append("\n==> KẾT QUẢ: ")
               .append((smtpOk && pop3Ok)
                       ? "THÀNH CÔNG! Server sẵn sàng kết nối Mozilla Thunderbird (SMTP AUTH + POP3 CAPA/UIDL)."
                       : "THẤT BẠI (SMTP=" + smtpOk + ", POP3=" + pop3Ok + ")");

            SwingUtilities.invokeLater(() -> tbTestResultArea.setText(out.toString()));
        }, "Thunderbird-Self-Test").start();
    }

    private void launchThunderbird() {
        String[] paths = {
                "C:\\Program Files\\Mozilla Thunderbird\\thunderbird.exe",
                "C:\\Program Files (x86)\\Mozilla Thunderbird\\thunderbird.exe"
        };
        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                try {
                    new ProcessBuilder(p).start();
                    return;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Không thể mở Thunderbird: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }
        JOptionPane.showMessageDialog(this,
                "Không tìm thấy Mozilla Thunderbird trên máy này.\n"
                        + "Hãy tải & cài đặt tại https://www.thunderbird.net/ rồi nhập thông số ở bảng bên dưới!",
                "Chưa Cài Thunderbird", JOptionPane.INFORMATION_MESSAGE);
    }

    private void loadAccountsToTable() {
        accountTableModel.setRowCount(0);
        List<MailAccount> list = MailDatabaseManager.getInstance().getAllAccounts();
        for (MailAccount a : list) {
            accountTableModel.addRow(new Object[]{a.getEmail(), a.getDisplayName(), a.getPassword(), a.isEnabled() ? "Hoạt Động" : "Khóa"});
        }
        refreshThunderbirdInfo();
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

