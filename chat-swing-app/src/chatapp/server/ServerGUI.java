package chatapp.server;

import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;
import chatapp.common.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Giao diện Quản trị Server bằng Java Swing:
 * - Điều khiển Start/Stop Server
 * - Bảng theo dõi số lượng Client trực tuyến (5-6 client cùng lúc)
 * - Nhật ký hoạt động mạng (TCP connections, UDP broadcasts, Multicast routing)
 * - Quản lý phòng chat Multicast
 * - Xem & Lọc lịch sử tin nhắn trong CSDL SQL (SQLite) theo từng User
 * - Bảng danh sách toàn bộ tài khoản người dùng đã tạo trong CSDL SQL
 */
public class ServerGUI extends JFrame implements ChatServer.ServerListener {
    private ChatServer server;
    private JTextField portField;
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel onlineCountLabel;
    private JLabel totalMsgLabel;
    private JLabel totalUsersLabel;
    private JTextArea logArea;
    private DefaultTableModel clientTableModel;
    private DefaultTableModel historyTableModel;
    private DefaultTableModel usersDbTableModel;
    private DefaultListModel<String> roomListModel;
    private JTextField newRoomField;

    // Filters cho tab Lịch Sử CSDL
    private JComboBox<String> userFilterCombo;
    private JComboBox<String> scopeFilterCombo;
    private JLabel historyCountLabel;

    public ServerGUI() {
        setTitle("MÁY CHỦ CHAT - LẬP TRÌNH MẠNG (TCP - UDP MULTICAST/BROADCAST)");
        setSize(1020, 720);
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
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Tiêu đề
        JLabel titleLabel = new JLabel("BẢNG ĐIỀU KHIỂN MÁY CHỦ CHAT (SERVER DASHBOARD)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(new Color(24, 119, 242));

        // Control buttons
        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ctrlPanel.setOpaque(false);

        ctrlPanel.add(new JLabel("Cổng TCP:"));
        portField = new JTextField(String.valueOf(Config.DEFAULT_TCP_PORT), 5);
        ctrlPanel.add(portField);

        startBtn = new JButton("Khởi Động Server");
        startBtn.setBackground(new Color(40, 167, 69));
        startBtn.setForeground(Color.WHITE);
        startBtn.setFocusPainted(false);
        startBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        startBtn.addActionListener(e -> startServer());
        ctrlPanel.add(startBtn);

        stopBtn = new JButton("Dừng Server");
        stopBtn.setBackground(new Color(220, 53, 69));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFocusPainted(false);
        stopBtn.setEnabled(false);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        stopBtn.addActionListener(e -> stopServer());
        ctrlPanel.add(stopBtn);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(ctrlPanel, BorderLayout.EAST);

        // Stats bar
        JPanel statsBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái Máy Chủ", "Đang Dừng", new Color(108, 117, 125));
        onlineCountLabel = createStatCard("Clients Trực Tuyến", "0 Client", new Color(0, 123, 255));
        totalMsgLabel = createStatCard("Gói Tin Đã Xử Lý", "0 Tin", new Color(111, 66, 193));
        totalUsersLabel = createStatCard("Tài Khoản Trong CSDL", DatabaseManager.getInstance().getAllUsers().size() + " Users (SQLite)", new Color(23, 162, 184));

        statsBar.add(statusLabel);
        statsBar.add(onlineCountLabel);
        statsBar.add(totalMsgLabel);
        statsBar.add(totalUsersLabel);

        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.setOpaque(false);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 2. TABS CONTENT
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Tab 1: Console Nhật Ký Hoạt Động (Logs)
        JPanel logTab = new JPanel(new BorderLayout(8, 8));
        logTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(220, 220, 220));
        JScrollPane logScroll = new JScrollPane(logArea);

        JPanel logBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = new JButton("Xóa Màn Hình Log");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        JButton broadcastSysBtn = new JButton("Gửi Thông Báo Hệ Thống (Broadcast)");
        broadcastSysBtn.addActionListener(e -> sendSystemBroadcast());
        logBtnBar.add(broadcastSysBtn);
        logBtnBar.add(clearLogBtn);

        logTab.add(logScroll, BorderLayout.CENTER);
        logTab.add(logBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Nhật Ký Mạng (Logs)  ", logTab);

        // Tab 2: Danh sách Clients Trực Tuyến (5-6 client cùng lúc)
        JPanel clientTab = new JPanel(new BorderLayout(8, 8));
        clientTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        clientTableModel = new DefaultTableModel(new String[]{"STT", "Username", "Địa Chỉ IP", "Cổng (Port)", "Thời Gian Vào", "Phòng Tham Gia"}, 0);
        JTable clientTable = new JTable(clientTableModel);
        clientTable.setRowHeight(26);
        clientTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        clientTab.add(new JScrollPane(clientTable), BorderLayout.CENTER);
        tabbedPane.addTab("  Clients Đang Online  ", clientTab);

        // Tab 3: Quản Lý Tài Khoản Người Dùng Trong CSDL SQL (Bảng 'users')
        JPanel usersDbTab = new JPanel(new BorderLayout(8, 8));
        usersDbTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        usersDbTableModel = new DefaultTableModel(new String[]{"STT", "Tên Tài Khoản (Username)", "Địa Chỉ IP Cuối", "Cổng Port", "Thời Gian Tạo Tài Khoản", "Đăng Nhập Gần Nhất"}, 0);
        JTable usersDbTable = new JTable(usersDbTableModel);
        usersDbTable.setRowHeight(26);
        usersDbTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        usersDbTab.add(new JScrollPane(usersDbTable), BorderLayout.CENTER);

        JPanel userDbBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshUsersDbBtn = new JButton("Làm Mới Danh Sách Tài Khoản");
        refreshUsersDbBtn.addActionListener(e -> refreshUsersDbTable());
        userDbBar.add(refreshUsersDbBtn);
        usersDbTab.add(userDbBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Tài Khoản CSDL (users)  ", usersDbTab);

        // Tab 4: Quản Lý Phòng Multicast
        JPanel roomTab = new JPanel(new BorderLayout(8, 8));
        roomTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        roomListModel = new DefaultListModel<>();
        for (String r : DatabaseManager.getInstance().getAllRooms()) {
            if (!"Học Tập (Study)".equalsIgnoreCase(r) && !"Công Nghệ (Tech)".equalsIgnoreCase(r) && !"Giải Trí (Fun)".equalsIgnoreCase(r)) {
                roomListModel.addElement(r);
            }
        }
        JList<String> roomList = new JList<>(roomListModel);
        roomList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        roomTab.add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel addRoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        addRoomPanel.add(new JLabel("Tên phòng mới:"));
        newRoomField = new JTextField(18);
        JButton addRoomBtn = new JButton("Tạo Phòng Multicast Mới");
        addRoomBtn.addActionListener(e -> addNewRoom());
        addRoomPanel.add(newRoomField);
        addRoomPanel.add(addRoomBtn);
        roomTab.add(addRoomPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("  Phòng Multicast  ", roomTab);

        // Tab 5: Lịch Sử Tin Nhắn CSDL (Lưu Theo User)
        JPanel historyTab = new JPanel(new BorderLayout(8, 8));
        historyTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Filter Bar phía trên
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        filterBar.setBackground(Color.WHITE);
        filterBar.setBorder(BorderFactory.createLineBorder(new Color(225, 230, 235), 1));

        filterBar.add(new JLabel("Lọc Theo User:"));
        userFilterCombo = new JComboBox<>();
        userFilterCombo.setPreferredSize(new Dimension(170, 28));
        filterBar.add(userFilterCombo);

        filterBar.add(new JLabel("Loại Tin:"));
        scopeFilterCombo = new JComboBox<>(new String[]{
                "Tất Cả Loại Tin", "Tin Broadcast", "Phòng Multicast", "Chat Riêng 1-1", "File / Ảnh / Video"
        });
        scopeFilterCombo.setPreferredSize(new Dimension(150, 28));
        filterBar.add(scopeFilterCombo);

        JButton filterBtn = new JButton("Lọc Dữ Liệu SQL");
        filterBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        filterBtn.setBackground(new Color(0, 123, 255));
        filterBtn.setForeground(Color.WHITE);
        filterBtn.addActionListener(e -> refreshHistoryTable());
        filterBar.add(filterBtn);

        JButton refreshHistoryBtn = new JButton("Tải Lại CSDL");
        refreshHistoryBtn.addActionListener(e -> {
            updateUserFilterDropdown();
            refreshHistoryTable();
        });
        filterBar.add(refreshHistoryBtn);

        historyCountLabel = new JLabel("");
        historyCountLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        historyCountLabel.setForeground(new Color(80, 85, 95));
        filterBar.add(historyCountLabel);

        historyTab.add(filterBar, BorderLayout.NORTH);

        historyTableModel = new DefaultTableModel(new String[]{"ID", "Thời Gian", "Loại Tin", "Người Gửi", "Người/Phòng Nhận", "Nội Dung", "Đính Kèm"}, 0);
        JTable historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(24);
        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        historyTab.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        tabbedPane.addTab("  Lịch Sử CSDL (Theo User)  ", historyTab);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);

        // Nạp dữ liệu ban đầu
        updateUserFilterDropdown();
        refreshUsersDbTable();
        refreshHistoryTable();
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

    private void updateUserFilterDropdown() {
        String selected = (String) userFilterCombo.getSelectedItem();
        userFilterCombo.removeAllItems();
        userFilterCombo.addItem("--- Tất Cả User ---");

        Set<String> users = DatabaseManager.getInstance().getAllUsers();
        for (String u : users) {
            userFilterCombo.addItem(u);
        }

        if (selected != null) {
            userFilterCombo.setSelectedItem(selected);
        }
    }

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng phải là số nguyên hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new ChatServer(port);
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
            clientTableModel.setRowCount(0);
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #dc3545;'>Đã Dừng</b></div></html>");
        }
    }

    private void sendSystemBroadcast() {
        if (server == null || !server.isRunning()) {
            JOptionPane.showMessageDialog(this, "Máy chủ chưa khởi động!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String msg = JOptionPane.showInputDialog(this, "Nhập thông báo gửi toàn bộ người dùng:", "Thông báo hệ thống", JOptionPane.QUESTION_MESSAGE);
        if (msg != null && !msg.trim().isEmpty()) {
            Message sysMsg = new Message(MessageType.SYSTEM_NOTIFICATION, "SERVER", msg.trim());
            server.broadcast(sysMsg);
            server.log("[ADMIN BROADCAST]: " + msg.trim());
        }
    }

    private void addNewRoom() {
        String name = newRoomField.getText().trim();
        if (name.isEmpty()) return;
        DatabaseManager.getInstance().addRoom(name);
        roomListModel.addElement(name);
        newRoomField.setText("");
        if (server != null && server.isRunning()) {
            server.broadcastRoomList();
            server.log("[PHONG MOI]: Đã tạo phòng multicast " + name);
        }
    }

    private void refreshUsersDbTable() {
        usersDbTableModel.setRowCount(0);
        List<DatabaseManager.UserRecord> list = DatabaseManager.getInstance().getAllUserRecords();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");
        int idx = 1;
        for (DatabaseManager.UserRecord u : list) {
            usersDbTableModel.addRow(new Object[]{
                    idx++,
                    u.username,
                    u.ipAddress != null ? u.ipAddress : "127.0.0.1",
                    u.port,
                    sdf.format(new Date(u.createdAt)),
                    sdf.format(new Date(u.lastLogin))
            });
        }
        totalUsersLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                + "<span style='font-size: 10px; color: #555555;'>Tài Khoản Trong CSDL</span><br>"
                + "<b style='font-size: 13px; color: #17a2b8;'>" + list.size() + " Users (SQLite)</b></div></html>");
    }

    private void refreshHistoryTable() {
        historyTableModel.setRowCount(0);

        String selectedUser = (String) userFilterCombo.getSelectedItem();
        String userParam = "ALL";
        if (selectedUser != null && !selectedUser.startsWith("---")) {
            userParam = selectedUser;
        }

        String scopeItem = (String) scopeFilterCombo.getSelectedItem();
        String scopeParam = "ALL";
        if ("Tin Broadcast".equals(scopeItem)) scopeParam = "BROADCAST";
        else if ("Phòng Multicast".equals(scopeItem)) scopeParam = "ROOM";
        else if ("Chat Riêng 1-1".equals(scopeItem)) scopeParam = "PRIVATE";
        else if ("File / Ảnh / Video".equals(scopeItem)) scopeParam = "FILE";

        List<DatabaseManager.MessageRecord> list = DatabaseManager.getInstance().getHistoryFiltered(userParam, scopeParam, 250);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss dd/MM");

        for (DatabaseManager.MessageRecord r : list) {
            String attach = r.fileName != null ? r.fileName + " (" + r.fileSize + "B)" : "-";
            historyTableModel.addRow(new Object[]{
                    r.id,
                    sdf.format(new Date(r.timestamp)),
                    r.type,
                    r.sender,
                    r.receiver != null ? r.receiver : (r.room != null ? r.room : "ALL"),
                    r.content,
                    attach
            });
        }

        int totalInDb = DatabaseManager.getInstance().getTotalMessageCount();
        historyCountLabel.setText("Hiển thị " + list.size() + " / Tổng " + totalInDb + " tin trong SQLite");
    }

    @Override
    public void onLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onClientConnected(User user) {
        SwingUtilities.invokeLater(() -> {
            refreshClientTable();
            refreshUsersDbTable();
            updateUserFilterDropdown();
        });
    }

    @Override
    public void onClientDisconnected(String username) {
        SwingUtilities.invokeLater(this::refreshClientTable);
    }

    private void refreshClientTable() {
        if (server == null) return;
        clientTableModel.setRowCount(0);
        int idx = 1;
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        for (ClientHandler ch : server.getActiveClients()) {
            User u = ch.getUser();
            if (u != null) {
                clientTableModel.addRow(new Object[]{
                        idx++,
                        u.getUsername(),
                        u.getIpAddress(),
                        u.getPort(),
                        sdf.format(new Date(u.getConnectedAt())),
                        String.join(", ", ch.getSubscribedRooms())
                });
            }
        }
    }

    @Override
    public void onStatsUpdated(int onlineCount, int totalMessages) {
        SwingUtilities.invokeLater(() -> {
            onlineCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Clients Trực Tuyến</span><br>"
                    + "<b style='font-size: 13px; color: #007bff;'>" + onlineCount + " Clients</b></div></html>");
            totalMsgLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Gói Tin Đã Xử Lý</span><br>"
                    + "<b style='font-size: 13px; color: #6f42c1;'>" + totalMessages + " Tin</b></div></html>");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
            gui.startServer();
        });
    }
}
