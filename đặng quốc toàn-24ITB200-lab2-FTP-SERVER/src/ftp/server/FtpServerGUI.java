package ftp.server;

import ftp.common.FtpConfig;
import ftp.common.FtpUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Giao diện Quản trị FTP Server bằng Java Swing:
 * - Điều khiển Start / Stop Server
 * - Quản lý CSDL Người dùng & Phân quyền (Users Database)
 * - Quản lý CSDL Nhật ký truyền file & thao tác lệnh (Audit Log Database)
 * - Giám sát lưu lượng và các phiên kết nối thời gian thực
 */
public class FtpServerGUI extends JFrame implements FtpServer.ServerEventListener {
    private FtpServer server;

    private JTextField portField;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel clientsCountLabel;
    private JLabel transferCountLabel;
    private JLabel totalBytesLabel;

    private JTextArea logArea;
    private DefaultTableModel userTableModel;
    private DefaultTableModel auditLogTableModel;
    private DefaultTableModel activeClientsTableModel;

    public FtpServerGUI() {
        setTitle("MÁY CHỦ FTP SERVER (RFC 959) - QUẢN TRỊ CSDL & DỊCH VỤ");
        setSize(980, 700);
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

        JLabel titleLabel = new JLabel("BẢNG ĐIỀU KHIỂN MÁY CHỦ FTP (FTP SERVER DASHBOARD)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(24, 119, 242));

        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ctrlPanel.setOpaque(false);

        ctrlPanel.add(new JLabel("Cổng Control:"));
        portField = new JTextField(String.valueOf(FtpConfig.DEFAULT_PORT), 5);
        ctrlPanel.add(portField);

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
        JPanel statsBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái Máy Chủ", "Đang Dừng", new Color(108, 117, 125));
        clientsCountLabel = createStatCard("Clients Kết Nối", "0 Phiên", new Color(0, 123, 255));
        transferCountLabel = createStatCard("Lượt Tải Lên / Tải Về", "0 Lượt", new Color(111, 66, 193));
        totalBytesLabel = createStatCard("Tổng Dung Lượng", "0 B", new Color(40, 167, 69));

        statsBar.add(statusLabel);
        statsBar.add(clientsCountLabel);
        statsBar.add(transferCountLabel);
        statsBar.add(totalBytesLabel);

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
        tabbedPane.addTab("  Nhật Ký Mạng (Logs)  ", logTab);

        // Tab 2: Quản trị CSDL Người dùng & Phân quyền
        JPanel userTab = new JPanel(new BorderLayout(8, 8));
        userTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        userTableModel = new DefaultTableModel(new String[]{
                "Tài Khoản (Username)", "Mật Khẩu", "Thư Mục Gốc", "Tải Về (Read)", "Tải Lên (Write)", "Xóa (Delete)", "Trạng Thái"
        }, 0);
        JTable userTable = new JTable(userTableModel);
        userTable.setRowHeight(26);
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        userTab.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel userActionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton addUserBtn = new JButton("+ Thêm Người Dùng");
        addUserBtn.addActionListener(e -> showAddUserDialog());
        JButton deleteUserBtn = new JButton("Xóa Tài Khoản");
        deleteUserBtn.addActionListener(e -> deleteSelectedUser(userTable));
        JButton refreshUsersBtn = new JButton("Nạp Lại Từ CSDL");
        refreshUsersBtn.addActionListener(e -> loadUsersToTable());

        userActionsBar.add(addUserBtn);
        userActionsBar.add(deleteUserBtn);
        userActionsBar.add(refreshUsersBtn);
        userTab.add(userActionsBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  CSDL Người Dùng & Phân Quyền  ", userTab);

        // Tab 3: CSDL Nhật Ký Kiểm Toán (Audit Logs)
        JPanel auditTab = new JPanel(new BorderLayout(8, 8));
        auditTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        auditLogTableModel = new DefaultTableModel(new String[]{
                "Thời Gian", "Người Dùng", "Hành Động", "Tên Tệp Tin / Thư Mục", "Dung Lượng", "Địa Chỉ IP", "Kết Quả", "Chi Tiết"
        }, 0);
        JTable auditTable = new JTable(auditLogTableModel);
        auditTable.setRowHeight(24);
        auditTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        auditTab.add(new JScrollPane(auditTable), BorderLayout.CENTER);

        JPanel auditBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshAuditBtn = new JButton("Làm Mới Lịch Sử CSDL");
        refreshAuditBtn.addActionListener(e -> loadAuditLogsToTable());
        auditBar.add(refreshAuditBtn);
        auditTab.add(auditBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  CSDL Lịch Sử Truyền File (Audit Log)  ", auditTab);

        // Tab 4: Clients Đang Kết Nối
        JPanel clientTab = new JPanel(new BorderLayout(8, 8));
        clientTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        activeClientsTableModel = new DefaultTableModel(new String[]{"STT", "Người Dùng", "Địa Chỉ IP", "Trạng Thái"}, 0);
        JTable clientTable = new JTable(activeClientsTableModel);
        clientTable.setRowHeight(26);
        clientTab.add(new JScrollPane(clientTable), BorderLayout.CENTER);
        tabbedPane.addTab("  Clients Trực Tuyến  ", clientTab);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);

        loadUsersToTable();
        loadAuditLogsToTable();
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
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng phải là số nguyên hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new FtpServer(port);
        server.addListener(this);

        if (server.start()) {
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            portField.setEditable(false);
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #28a745;'>Đang Chạy (Port " + port + ")</b></div></html>");
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            startBtn.setEnabled(true);
            stopBtn.setEnabled(false);
            portField.setEditable(true);
            activeClientsTableModel.setRowCount(0);
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #dc3545;'>Đã Dừng</b></div></html>");
        }
    }

    private void loadUsersToTable() {
        userTableModel.setRowCount(0);
        List<FtpUser> users = FtpDatabaseManager.getInstance().getAllUsers();
        for (FtpUser u : users) {
            userTableModel.addRow(new Object[]{
                    u.getUsername(),
                    u.getPassword().isEmpty() ? "(Không)" : u.getPassword(),
                    u.getHomeDirectory(),
                    u.isCanRead() ? "Có (Allow)" : "Không",
                    u.isCanWrite() ? "Có (Allow)" : "Không",
                    u.isCanDelete() ? "Có (Allow)" : "Không",
                    u.isEnabled() ? "Kích Hoạt" : "Khóa"
            });
        }
    }

    private void loadAuditLogsToTable() {
        auditLogTableModel.setRowCount(0);
        List<FtpDatabaseManager.AuditLogEntry> logs = FtpDatabaseManager.getInstance().getAuditLogs(300);
        for (FtpDatabaseManager.AuditLogEntry r : logs) {
            auditLogTableModel.addRow(new Object[]{
                    r.getFormattedTime(),
                    r.username,
                    r.action,
                    r.fileName.isEmpty() ? "-" : r.fileName,
                    r.getFormattedSize(),
                    r.ipAddress,
                    r.success ? "Thành Công" : "Thất Bại",
                    r.details
            });
        }
    }

    private void showAddUserDialog() {
        JTextField uField = new JTextField("user1");
        JPasswordField pField = new JPasswordField("123456");
        JTextField hField = new JTextField(FtpConfig.SERVER_ROOT_DIR);
        JCheckBox rBox = new JCheckBox("Quyền Tải Về (Download / RETR)", true);
        JCheckBox wBox = new JCheckBox("Quyền Tải Lên (Upload / STOR)", true);
        JCheckBox dBox = new JCheckBox("Quyền Xóa Tệp/Thư mục (DELE/RMD)", false);

        JPanel panel = new JPanel(new GridLayout(6, 2, 6, 6));
        panel.add(new JLabel("Tên tài khoản:"));
        panel.add(uField);
        panel.add(new JLabel("Mật khẩu:"));
        panel.add(pField);
        panel.add(new JLabel("Thư mục gốc:"));
        panel.add(hField);
        panel.add(new JLabel("Phân quyền:"));
        panel.add(rBox);
        panel.add(new JLabel(""));
        panel.add(wBox);
        panel.add(new JLabel(""));
        panel.add(dBox);

        int res = JOptionPane.showConfirmDialog(this, panel, "Thêm Người Dùng FTP Mới", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String u = uField.getText().trim();
            String p = new String(pField.getPassword());
            String h = hField.getText().trim();
            if (u.isEmpty()) return;

            FtpUser newUser = new FtpUser(u, p, h.isEmpty() ? FtpConfig.SERVER_ROOT_DIR : h);
            newUser.setCanRead(rBox.isSelected());
            newUser.setCanWrite(wBox.isSelected());
            newUser.setCanDelete(dBox.isSelected());

            FtpDatabaseManager.getInstance().addOrUpdateUser(newUser);
            loadUsersToTable();
            JOptionPane.showMessageDialog(this, "Đã lưu tài khoản vào Cơ Sở Dữ Liệu!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteSelectedUser(JTable table) {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một người dùng để xóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String u = (String) userTableModel.getValueAt(row, 0);
        if ("admin".equalsIgnoreCase(u)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản Quản trị viên mặc định (admin)!", "Cảnh Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa tài khoản " + u + " khỏi CSDL?", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            FtpDatabaseManager.getInstance().deleteUser(u);
            loadUsersToTable();
        }
    }

    @Override
    public void onLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onStatsChanged(int activeCount, long totalUploads, long totalDownloads, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            clientsCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Clients Kết Nối</span><br>"
                    + "<b style='font-size: 13px; color: #007bff;'>" + activeCount + " Phiên</b></div></html>");
            transferCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Lượt Tải Lên / Tải Về</span><br>"
                    + "<b style='font-size: 13px; color: #6f42c1;'>" + (totalUploads + totalDownloads) + " Lượt</b></div></html>");

            String byteStr;
            if (totalBytes < 1024) byteStr = totalBytes + " B";
            else if (totalBytes < 1024 * 1024) byteStr = String.format("%.1f KB", totalBytes / 1024.0);
            else byteStr = String.format("%.1f MB", totalBytes / (1024.0 * 1024.0));

            totalBytesLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Tổng Dung Lượng</span><br>"
                    + "<b style='font-size: 13px; color: #28a745;'>" + byteStr + "</b></div></html>");
            loadAuditLogsToTable();
        });
    }

    @Override
    public void onClientsChanged(List<FtpClientHandler> activeClients) {
        SwingUtilities.invokeLater(() -> {
            activeClientsTableModel.setRowCount(0);
            int idx = 1;
            for (FtpClientHandler ch : activeClients) {
                activeClientsTableModel.addRow(new Object[]{
                        idx++, ch.getUsername(), ch.getClientIp(), "Đang Hoạt Động"
                });
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FtpServerGUI gui = new FtpServerGUI();
            gui.setVisible(true);
            gui.startServer();
        });
    }
}

