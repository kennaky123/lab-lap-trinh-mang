package rmi.server;

import rmi.common.RecordItem;
import rmi.common.RmiUser;
import rmi.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

/**
 * Giao diện Quản trị RMI Server Dashboard:
 * - Khởi tạo RMI Registry và Binding dịch vụ
 * - Giám sát trạng thái Online/Offline của Server đối tác (Peer Server)
 * - Hiển thị CSDL phân tán SQLite (bảng records & bảng users phân quyền)
 * - Nhật ký đồng bộ hóa RMI thời gian thực
 */
public class RmiServerGUI extends JFrame implements DataSyncServiceImpl.SyncEventListener {

    private String serverId = "Server 1";
    private int myPort = 1099;
    private String peerHost = "localhost";
    private int peerPort = 1100;
    private String dataDir = "server1_data";

    private DataSyncServiceImpl service;
    private Registry registry;

    private JLabel subLabel;
    private JLabel statusLabel;
    private JLabel peerStatusLabel;
    private JLabel recordCountLabel;
    private JTextArea logArea;
    private DefaultTableModel tableModel;
    private DefaultTableModel userTableModel;
    private JTable userTable;

    public static final int UDP_DISCOVERY_PORT = 1101;

    public RmiServerGUI(String serverId, int myPort, String peerHost, int peerPort, String dataDir) {
        this.serverId = serverId;
        this.myPort = myPort;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.dataDir = dataDir;

        String lanIp = getLocalLanIp();
        System.setProperty("java.rmi.server.hostname", lanIp);

        setTitle("RMI SERVER DASHBOARD - [" + serverId + " - IP LAN: " + lanIp + ":" + myPort + "]");
        setSize(1020, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        startRmiService();
        startUdpDiscoveryResponder();
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

        Color themeColor = serverId.contains("1") ? new Color(24, 119, 242) : new Color(111, 66, 193);

        JLabel titleLabel = new JLabel("HỆ THỐNG ĐỒNG BỘ HÓA RMI & PHÂN QUYỀN SQLITE (" + serverId.toUpperCase() + ")");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(themeColor);

        subLabel = new JLabel("IP LAN: " + getLocalLanIp() + ":" + myPort + "  |  Kết nối đối tác: " + peerHost + ":" + peerPort + "  |  CSDL: " + dataDir + "/records.db");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel.setForeground(new Color(100, 105, 115));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(titleLabel);
        titleBlock.add(subLabel);

        headerPanel.add(titleBlock, BorderLayout.WEST);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);

        JButton peerIpBtn = new JButton("⚙ Đổi IP Đối Tác");
        peerIpBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        peerIpBtn.addActionListener(e -> promptChangePeerIp());

        JButton addSampleBtn = new JButton("+ Thêm Nhanh Bản Ghi");
        addSampleBtn.setBackground(new Color(40, 167, 69));
        addSampleBtn.setForeground(Color.WHITE);
        addSampleBtn.setContentAreaFilled(false);
        addSampleBtn.setOpaque(true);
        addSampleBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addSampleBtn.setFocusPainted(false);
        addSampleBtn.addActionListener(e -> addSampleRecord());

        JButton resyncBtn = new JButton("🔄 Kéo Toàn Bộ Dữ Liệu (Full Sync)");
        resyncBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resyncBtn.addActionListener(e -> forceFullSync());

        headerActions.add(peerIpBtn);
        headerActions.add(addSampleBtn);
        headerActions.add(resyncBtn);
        headerPanel.add(headerActions, BorderLayout.EAST);

        // Stats Cards Bar
        JPanel statsBar = new JPanel(new GridLayout(1, 3, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái " + serverId, "Đang Khởi Động...", new Color(108, 117, 125));
        peerStatusLabel = createStatCard("Server Đối Tác (" + peerHost + ":" + peerPort + ")", "Đang Kiểm Tra...", new Color(242, 153, 74));
        recordCountLabel = createStatCard("Tổng Số Bản Ghi SQLite", "0 Bản Ghi", themeColor);

        statsBar.add(statusLabel);
        statsBar.add(peerStatusLabel);
        statsBar.add(recordCountLabel);

        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.setOpaque(false);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 2. TABS CONTENT
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Tab 1: CSDL Phân Tán Cục Bộ (Bảng records)
        JPanel dataTab = new JPanel(new BorderLayout(8, 8));
        dataTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        tableModel = new DefaultTableModel(new String[]{
                "Mã ID", "Họ Tên / Tên Bản Ghi", "Ngành Học / Phân Loại", "Điểm / Giá Trị", "Thời Gian Cập Nhật", "Server / Người Cập Nhật"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(25);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        dataTab.add(tableScroll, BorderLayout.CENTER);
        tabbedPane.addTab("  CSDL Bản Ghi (SQLite: records)  ", dataTab);

        // Tab 2: Quản lý Phân quyền Tài khoản (Bảng users)
        JPanel userTab = createUsersManagementTab();
        tabbedPane.addTab("  Phân Quyền Tài Khoản (SQLite: users)  ", userTab);

        // Tab 3: Nhật Ký Đồng Bộ RMI
        JPanel logTab = new JPanel(new BorderLayout(8, 8));
        logTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(28, 30, 36));
        logArea.setForeground(new Color(225, 230, 240));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        JPanel logBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = new JButton("Xóa Màn Hình Log");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logBtnBar.add(clearLogBtn);

        logTab.add(logScroll, BorderLayout.CENTER);
        logTab.add(logBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Nhật Ký Đồng Bộ RMI (Replication Logs)  ", logTab);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        add(mainPanel);
    }

    private JPanel createUsersManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        userTableModel = new DefaultTableModel(new String[]{
                "Tên Đăng Nhập (Username)", "Mật Khẩu", "Vai Trò (Role)", "Quyền Xem", "Quyền Thêm", "Quyền Sửa", "Quyền Xóa"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        userTable = new JTable(userTableModel);
        userTable.setRowHeight(26);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(userTable);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton addUserBtn = new JButton("+ Thêm / Sửa Tài Khoản Phân Quyền");
        addUserBtn.setBackground(new Color(24, 119, 242));
        addUserBtn.setForeground(Color.WHITE);
        addUserBtn.setContentAreaFilled(false);
        addUserBtn.setOpaque(true);
        addUserBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addUserBtn.addActionListener(e -> showAddOrEditUserDialog());

        JButton delUserBtn = new JButton("Xóa Tài Khoản");
        delUserBtn.setBackground(new Color(220, 53, 69));
        delUserBtn.setForeground(Color.WHITE);
        delUserBtn.setContentAreaFilled(false);
        delUserBtn.setOpaque(true);
        delUserBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        delUserBtn.addActionListener(e -> deleteSelectedUser());

        btnBar.add(addUserBtn);
        btnBar.add(delUserBtn);
        btnBar.add(new JLabel("   (Mặc định: admin/123456 [Toàn quyền] | editor/123456 [Thêm/Sửa] | sinhvien/123456 [Chỉ xem])"));

        panel.add(btnBar, BorderLayout.SOUTH);
        return panel;
    }

    private void showAddOrEditUserDialog() {
        if (service == null) return;
        JTextField userField = new JTextField();
        JTextField passField = new JTextField("123456");
        JComboBox<String> roleCombo = new JComboBox<>(new String[]{"ADMIN (Toàn quyền)", "EDITOR (Thêm & Sửa)", "VIEWER (Chỉ xem)", "CUSTOM (Tùy chỉnh)"});
        JCheckBox addCheck = new JCheckBox("Cho phép Thêm bản ghi (Add)", true);
        JCheckBox updateCheck = new JCheckBox("Cho phép Sửa bản ghi (Update)", true);
        JCheckBox deleteCheck = new JCheckBox("Cho phép Xóa bản ghi (Delete)", true);

        int selectedRow = userTable.getSelectedRow();
        if (selectedRow != -1) {
            userField.setText((String) userTableModel.getValueAt(selectedRow, 0));
            passField.setText((String) userTableModel.getValueAt(selectedRow, 1));
        }

        roleCombo.addActionListener(e -> {
            int idx = roleCombo.getSelectedIndex();
            if (idx == 0) {
                addCheck.setSelected(true);
                updateCheck.setSelected(true);
                deleteCheck.setSelected(true);
            } else if (idx == 1) {
                addCheck.setSelected(true);
                updateCheck.setSelected(true);
                deleteCheck.setSelected(false);
            } else if (idx == 2) {
                addCheck.setSelected(false);
                updateCheck.setSelected(false);
                deleteCheck.setSelected(false);
            }
        });

        JPanel p = new JPanel(new GridLayout(6, 2, 6, 6));
        p.add(new JLabel("Tên đăng nhập (Username):"));
        p.add(userField);
        p.add(new JLabel("Mật khẩu (Password):"));
        p.add(passField);
        p.add(new JLabel("Vai trò mẫu (Role):"));
        p.add(roleCombo);
        p.add(new JLabel("Quyền Thêm:"));
        p.add(addCheck);
        p.add(new JLabel("Quyền Sửa:"));
        p.add(updateCheck);
        p.add(new JLabel("Quyền Xóa:"));
        p.add(deleteCheck);

        int res = JOptionPane.showConfirmDialog(this, p, "Cấu Hình Tài Khoản & Phân Quyền (Đồng bộ 2 Server)", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            String u = userField.getText().trim();
            String pw = passField.getText().trim();
            if (u.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String roleName = "CUSTOM";
            if (addCheck.isSelected() && updateCheck.isSelected() && deleteCheck.isSelected()) roleName = "ADMIN";
            else if (addCheck.isSelected() && updateCheck.isSelected() && !deleteCheck.isSelected()) roleName = "EDITOR";
            else if (!addCheck.isSelected() && !updateCheck.isSelected() && !deleteCheck.isSelected()) roleName = "VIEWER";

            RmiUser newUser = new RmiUser(u, pw, roleName, addCheck.isSelected(), updateCheck.isSelected(), deleteCheck.isSelected());
            try {
                service.saveUser(newUser);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi lưu tài khoản: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedUser() {
        if (service == null) return;
        int row = userTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Hãy chọn một tài khoản trong bảng để xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String username = (String) userTableModel.getValueAt(row, 0);
        if ("admin".equalsIgnoreCase(username)) {
            JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản quản trị gốc 'admin'!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa tài khoản '" + username + "' trên cả 2 Server?", "Xác Nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                service.deleteUser(username);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi xóa tài khoản: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
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

    private void startRmiService() {
        new Thread(() -> {
            try {
                try {
                    registry = LocateRegistry.createRegistry(myPort);
                } catch (Exception e) {
                    registry = LocateRegistry.getRegistry(myPort);
                }

                service = new DataSyncServiceImpl(serverId, myPort, peerHost, peerPort, dataDir);
                service.addListener(this);

                registry.rebind("DataSyncService", service);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                            + "<span style='font-size: 10px; color: #555555;'>Trạng Thái " + serverId + "</span><br>"
                            + "<b style='font-size: 13px; color: #28a745;'>Đang Chạy (RMI Port " + myPort + ")</b></div></html>");
                    onDataChanged(service.getCurrentData());
                    onUsersChanged(service.getCurrentUsers());
                });

                service.startPeerMonitor();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Lỗi: " + e.getMessage());
                    JOptionPane.showMessageDialog(this, "Không thể khởi tạo RMI Service tại cổng " + myPort + ": " + e.getMessage(), "Lỗi RMI", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void addSampleRecord() {
        if (service == null) return;
        int rnd = new Random().nextInt(900) + 100;
        String id = "SV" + rnd;
        String[] names = {"Nguyễn Văn An", "Trần Thị Bích", "Lê Hoàng Nam", "Phạm Minh Đức", "Đỗ Quỳnh Như"};
        String[] depts = {"Công Nghệ Thông Tin", "Khoa Học Máy Tính", "Kỹ Thuật Phần Mềm", "An Toàn Thông Tin"};
        String name = names[new Random().nextInt(names.length)];
        String dept = depts[new Random().nextInt(depts.length)];
        double score = 7.0 + (new Random().nextInt(30) / 10.0);

        RecordItem item = new RecordItem(id, name, dept, score, serverId + " (admin)");
        try {
            service.addRecord(item);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi thêm bản ghi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void forceFullSync() {
        if (service == null) return;
        new Thread(() -> {
            try {
                Registry peerReg = LocateRegistry.getRegistry(peerHost, peerPort);
                rmi.common.IDataSyncService peer = (rmi.common.IDataSyncService) peerReg.lookup("DataSyncService");
                List<RecordItem> fullList = peer.requestFullSync();
                int added = 0;
                int skipped = 0;
                if (fullList != null) {
                    for (RecordItem it : fullList) {
                        if (service.getRecordById(it.getId()) == null) {
                            service.syncReplicate("ADD", it, "FORCE_SYNC");
                            added++;
                        } else {
                            skipped++;
                        }
                    }
                    JOptionPane.showMessageDialog(this,
                            "Đã đồng bộ thêm " + added + " bản ghi mới (bỏ qua " + skipped + " bản ghi đã tồn tại)!",
                            "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể kết nối Server đối tác: " + ex.getMessage(), "Lỗi Đồng Bộ", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
    }

    private void promptChangePeerIp() {
        JTextField hostF = new JTextField(peerHost);
        JTextField portF = new JTextField(String.valueOf(peerPort));
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.add(new JLabel("IP Server Đối Tác (Peer IP):"));
        p.add(hostF);
        p.add(new JLabel("Cổng RMI Đối Tác (1099 / 1100):"));
        p.add(portF);

        int r = JOptionPane.showConfirmDialog(this, p, "Cấu Hình Kết Nối Server Đối Tác", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            peerHost = hostF.getText().trim();
            try {
                peerPort = Integer.parseInt(portF.getText().trim());
            } catch (NumberFormatException ignored) {}
            subLabel.setText("IP LAN: " + getLocalLanIp() + ":" + myPort + "  |  Kết nối đối tác: " + peerHost + ":" + peerPort + "  |  CSDL: " + dataDir + "/records.db");
            if (service != null) {
                service.updatePeerTarget(peerHost, peerPort);
            }
            onPeerStatusChanged(false);
        }
    }

    private void startUdpDiscoveryResponder() {
        Thread t = new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(UDP_DISCOVERY_PORT));
                byte[] buf = new byte[256];
                while (!socket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                    if ("DISCOVER_RMI_SERVER_REQUEST".equals(msg)) {
                        String reply = "RMI_SERVER_DISCOVERED:" + myPort;
                        byte[] out = reply.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket resp = new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort());
                        socket.send(resp);
                    }
                }
            } catch (Exception ignored) {}
        }, "RMI-UDP-Discovery");
        t.setDaemon(true);
        t.start();
    }

    public static String getLocalLanIp() {
        try {
            String fallbackIp = null;
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                if (name.contains("virtual") || name.contains("vmware") || name.contains("vbox")
                        || name.contains("wsl") || name.contains("hyper-v") || name.contains("loopback")) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (name.contains("wi-fi") || name.contains("wlan") || name.contains("wireless")) {
                            return ip;
                        }
                        if (fallbackIp == null) fallbackIp = ip;
                    }
                }
            }
            if (fallbackIp != null) return fallbackIp;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
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
    public void onDataChanged(List<RecordItem> currentData) {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (RecordItem it : currentData) {
                tableModel.addRow(new Object[]{
                        it.getId(), it.getName(), it.getCategory(), it.getValue(), it.getFormattedDate(), it.getLastUpdatedByServer()
                });
            }
            recordCountLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Tổng Số Bản Ghi SQLite</span><br>"
                    + "<b style='font-size: 13px; color: #1877f2;'>" + currentData.size() + " Bản Ghi</b></div></html>");
        });
    }

    @Override
    public void onUsersChanged(List<RmiUser> currentUsers) {
        SwingUtilities.invokeLater(() -> {
            userTableModel.setRowCount(0);
            for (RmiUser u : currentUsers) {
                userTableModel.addRow(new Object[]{
                        u.getUsername(),
                        u.getPassword(),
                        u.getRole(),
                        "✔ Có",
                        u.isCanAdd() ? "✔ Có" : "✖ Không",
                        u.isCanUpdate() ? "✔ Có" : "✖ Không",
                        u.isCanDelete() ? "✔ Có" : "✖ Không"
                });
            }
        });
    }

    @Override
    public void onPeerStatusChanged(boolean isPeerOnline) {
        SwingUtilities.invokeLater(() -> {
            if (isPeerOnline) {
                peerStatusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                        + "<span style='font-size: 10px; color: #555555;'>Server Đối Tác (" + peerHost + ":" + peerPort + ")</span><br>"
                        + "<b style='font-size: 13px; color: #28a745;'>ONLINE (Đã Đồng Bộ)</b></div></html>");
            } else {
                peerStatusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                        + "<span style='font-size: 10px; color: #555555;'>Server Đối Tác (" + peerHost + ":" + peerPort + ")</span><br>"
                        + "<b style='font-size: 13px; color: #dc3545;'>OFFLINE (Chờ Kết Nối)</b></div></html>");
            }
        });
    }

    public static void main(String[] args) {
        String serverId = "Server 1";
        int myPort = 1099;
        String peerHost = "localhost";
        int peerPort = 1100;
        String dataDir = "server1_data";

        if (args.length >= 1) {
            int num = Integer.parseInt(args[0]);
            if (num == 2) {
                serverId = "Server 2";
                myPort = 1100;
                peerHost = "localhost";
                peerPort = 1099;
                dataDir = "server2_data";
            }
        }
        if (args.length >= 3) {
            peerHost = args[1];
            peerPort = Integer.parseInt(args[2]);
        }

        final String fId = serverId;
        final int fMyPort = myPort;
        final String fPeerHost = peerHost;
        final int fPeerPort = peerPort;
        final String fDataDir = dataDir;

        SwingUtilities.invokeLater(() -> {
            RmiServerGUI gui = new RmiServerGUI(fId, fMyPort, fPeerHost, fPeerPort, fDataDir);
            gui.setVisible(true);
        });
    }
}
