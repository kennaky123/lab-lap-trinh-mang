package rmi.client;

import rmi.common.IDataSyncService;
import rmi.common.RecordItem;
import rmi.common.RmiUser;
import rmi.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;

/**
 * Giao diện RMI Client (chạy trên các máy ảo hoặc máy trạm):
 * - Đăng nhập & Phân quyền theo vai trò (ADMIN, EDITOR, VIEWER) từ SQLite trên Server
 * - Kết nối tới Server 1 hoặc Server 2 qua RMI Registry
 * - Hỗ trợ Auto Failover: Tự động chuyển server khi một server mất kết nối
 * - Thao tác Thêm / Sửa / Xóa dữ liệu phân tán theo quyền hạn được cấp
 */
public class RmiClientGUI extends JFrame {

    private String server1Host = "localhost";
    private int server1Port = 1099;
    private String server2Host = "localhost";
    private int server2Port = 1100;

    private int activeServerIndex = 1; // 1 hoặc 2
    private IDataSyncService activeService;

    // Thông tin tài khoản đang đăng nhập
    private RmiUser currentUser = RmiUser.createByRole("admin", "123456", "ADMIN");
    private String currentUsername = "admin";
    private String currentPassword = "123456";

    // UI Components
    private JComboBox<String> serverSelectorCombo;
    private JComboBox<String> quickRoleCombo;
    private JLabel connectionStatusLabel;
    private JLabel userBadgeLabel;
    private JCheckBox autoFailoverCheckBox;

    private JTextField idField;
    private JTextField nameField;
    private JTextField categoryField;
    private JTextField valueField;

    private JButton addBtn;
    private JButton updateBtn;
    private JButton deleteBtn;

    private DefaultTableModel tableModel;
    private JTable dataTable;
    private JTextField searchField;
    private boolean updatingRoleCombo = false;

    public RmiClientGUI() {
        setTitle("RMI CLIENT - ĐĂNG NHẬP & PHÂN QUYỀN DỮ LIỆU ĐỒNG BỘ");
        setSize(1080, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        applyUserPermissionsUI();
        connectToServer(1);
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainPanel.setBackground(new Color(245, 247, 250));

        // ==================== 1. TOP HEADER & SERVER / AUTH SELECTOR ====================
        JPanel topHeader = new JPanel(new BorderLayout(10, 6));
        topHeader.setBackground(Color.WHITE);
        topHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        JLabel titleLabel = new JLabel("ỨNG DỤNG RMI CLIENT (PHÂN QUYỀN SQLITE & ĐỒNG BỘ ĐA MÁY CHỦ)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(24, 119, 242));

        connectionStatusLabel = new JLabel("Đang kết nối...");
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        connectionStatusLabel.setForeground(new Color(100, 105, 115));
        titleBlock.add(titleLabel);
        titleBlock.add(connectionStatusLabel);

        JPanel serverControl = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        serverControl.setOpaque(false);

        serverControl.add(new JLabel("Máy Chủ:"));
        serverSelectorCombo = new JComboBox<>(new String[]{
                "Server 1 (" + server1Host + ":" + server1Port + ")",
                "Server 2 (" + server2Host + ":" + server2Port + ")"
        });
        serverSelectorCombo.addActionListener(e -> {
            int selected = serverSelectorCombo.getSelectedIndex() + 1;
            if (selected != activeServerIndex) {
                connectToServer(selected);
            }
        });
        serverControl.add(serverSelectorCombo);

        autoFailoverCheckBox = new JCheckBox("Auto Failover", true);
        autoFailoverCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        serverControl.add(autoFailoverCheckBox);

        JButton findLanBtn = new JButton("Tìm IP LAN");
        findLanBtn.addActionListener(e -> discoverRmiServerInLan(null, null));
        serverControl.add(findLanBtn);

        JButton configIpBtn = new JButton("⚙ Đổi IP");
        configIpBtn.addActionListener(e -> promptChangeServerIps());
        serverControl.add(configIpBtn);

        JButton refreshBtn = new JButton("🔄 Nạp Lại");
        refreshBtn.addActionListener(e -> refreshData());
        serverControl.add(refreshBtn);

        // Thanh Đăng nhập & Phân quyền ở dòng thứ 2 của Header
        JPanel authBar = new JPanel(new BorderLayout(8, 0));
        authBar.setBackground(new Color(238, 244, 255));
        authBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(195, 215, 250), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));

        userBadgeLabel = new JLabel();
        userBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        userBadgeLabel.setForeground(new Color(20, 90, 190));

        JPanel authActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        authActions.setOpaque(false);
        authActions.add(new JLabel("Chuyển nhanh tài khoản test:"));
        quickRoleCombo = new JComboBox<>(new String[]{
                "admin (ADMIN - Toàn quyền: Xem, Thêm, Sửa, Xóa)",
                "editor (EDITOR - Cán bộ: Xem, Thêm, Sửa)",
                "sinhvien (VIEWER - Sinh viên: Chỉ Xem)"
        });
        quickRoleCombo.addActionListener(e -> {
            if (updatingRoleCombo) return;
            int idx = quickRoleCombo.getSelectedIndex();
            if (idx == 0) performLogin("admin", "123456", false);
            else if (idx == 1) performLogin("editor", "123456", false);
            else if (idx == 2) performLogin("sinhvien", "123456", false);
        });
        authActions.add(quickRoleCombo);

        JButton loginBtn = new JButton("🔑 Đăng Nhập Tài Khoản Khác...");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        loginBtn.addActionListener(e -> promptLoginDialog());
        authActions.add(loginBtn);

        authBar.add(userBadgeLabel, BorderLayout.WEST);
        authBar.add(authActions, BorderLayout.EAST);

        topHeader.add(titleBlock, BorderLayout.WEST);
        topHeader.add(serverControl, BorderLayout.EAST);
        topHeader.add(authBar, BorderLayout.SOUTH);

        mainPanel.add(topHeader, BorderLayout.NORTH);

        // ==================== 2. CENTER: DATA TABLE & INPUT FORM ====================
        JPanel centerContainer = new JPanel(new BorderLayout(0, 10));
        centerContainer.setOpaque(false);

        // Search & Filter Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        searchBar.setBackground(Color.WHITE);
        searchBar.setBorder(BorderFactory.createLineBorder(new Color(225, 230, 238), 1));
        searchBar.add(new JLabel("🔍 Tìm kiếm bản ghi:"));
        searchField = new JTextField(20);
        searchField.addCaretListener(e -> filterTable(searchField.getText().trim()));
        searchBar.add(searchField);
        centerContainer.add(searchBar, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(new String[]{
                "Mã ID", "Họ Tên / Bản Ghi", "Ngành Học / Phân Loại", "Điểm / Giá Trị", "Thời Gian Sửa Đổi", "Server / Người Cập Nhật"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        dataTable = new JTable(tableModel);
        dataTable.setRowHeight(26);
        dataTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dataTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        dataTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = dataTable.getSelectedRow();
                if (row != -1) {
                    idField.setText((String) tableModel.getValueAt(row, 0));
                    nameField.setText((String) tableModel.getValueAt(row, 1));
                    categoryField.setText((String) tableModel.getValueAt(row, 2));
                    valueField.setText(String.valueOf(tableModel.getValueAt(row, 3)));
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(dataTable);
        tableScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        centerContainer.add(tableScroll, BorderLayout.CENTER);

        mainPanel.add(centerContainer, BorderLayout.CENTER);

        // ==================== 3. SOUTH: CRUD FORM ====================
        JPanel formCard = new JPanel(new BorderLayout(8, 8));
        formCard.setBackground(Color.WHITE);
        formCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel inputsGrid = new JPanel(new GridLayout(2, 4, 8, 8));
        inputsGrid.setOpaque(false);

        idField = new JTextField();
        nameField = new JTextField();
        categoryField = new JTextField("Công Nghệ Thông Tin");
        valueField = new JTextField("8.5");

        inputsGrid.add(new JLabel("Mã Định Danh (ID):"));
        inputsGrid.add(idField);
        inputsGrid.add(new JLabel("Họ Tên / Bản Ghi:"));
        inputsGrid.add(nameField);

        inputsGrid.add(new JLabel("Ngành Học / Phân Loại:"));
        inputsGrid.add(categoryField);
        inputsGrid.add(new JLabel("Điểm Số / Giá Trị:"));
        inputsGrid.add(valueField);

        formCard.add(inputsGrid, BorderLayout.CENTER);

        // Action Buttons Bar
        JPanel actionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actionsBar.setOpaque(false);

        addBtn = new JButton("+ Thêm Mới");
        addBtn.setBackground(new Color(40, 167, 69));
        addBtn.setForeground(Color.WHITE);
        addBtn.setContentAreaFilled(false);
        addBtn.setOpaque(true);
        addBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addBtn.addActionListener(e -> doAddRecord());

        updateBtn = new JButton("Sửa Bản Ghi");
        updateBtn.setBackground(new Color(242, 153, 74));
        updateBtn.setForeground(Color.WHITE);
        updateBtn.setContentAreaFilled(false);
        updateBtn.setOpaque(true);
        updateBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        updateBtn.addActionListener(e -> doUpdateRecord());

        deleteBtn = new JButton("Xóa Bản Ghi");
        deleteBtn.setBackground(new Color(220, 53, 69));
        deleteBtn.setForeground(Color.WHITE);
        deleteBtn.setContentAreaFilled(false);
        deleteBtn.setOpaque(true);
        deleteBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        deleteBtn.addActionListener(e -> doDeleteRecord());

        JButton clearBtn = new JButton("Xóa Trắng Form");
        clearBtn.addActionListener(e -> clearForm());

        actionsBar.add(addBtn);
        actionsBar.add(updateBtn);
        actionsBar.add(deleteBtn);
        actionsBar.add(clearBtn);

        formCard.add(actionsBar, BorderLayout.SOUTH);
        mainPanel.add(formCard, BorderLayout.SOUTH);

        add(mainPanel);
    }

    // ==================== ĐĂNG NHẬP & ÁP DỤNG PHÂN QUYỀN ====================

    private void promptLoginDialog() {
        JTextField uField = new JTextField(currentUsername);
        JPasswordField pField = new JPasswordField(currentPassword);

        JPanel p = new JPanel(new GridLayout(3, 2, 6, 6));
        p.add(new JLabel("Tên đăng nhập (Username):"));
        p.add(uField);
        p.add(new JLabel("Mật khẩu (Password):"));
        p.add(pField);
        p.add(new JLabel("Gợi ý tài khoản mẫu:"));
        p.add(new JLabel("admin / editor / sinhvien (MK: 123456)"));

        int res = JOptionPane.showConfirmDialog(this, p, "Đăng Nhập Phân Quyền RMI", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            performLogin(uField.getText().trim(), new String(pField.getPassword()).trim(), true);
        }
    }

    private void performLogin(String username, String password, boolean showSuccessDialog) {
        if (activeService == null) {
            currentUsername = username;
            currentPassword = password;
            return;
        }
        new Thread(() -> {
            try {
                RmiUser loggedIn = activeService.login(username, password);
                SwingUtilities.invokeLater(() -> {
                    if (loggedIn != null) {
                        currentUser = loggedIn;
                        currentUsername = loggedIn.getUsername();
                        currentPassword = password;
                        applyUserPermissionsUI();
                        if (showSuccessDialog) {
                            JOptionPane.showMessageDialog(this,
                                    "Đăng nhập thành công!\n"
                                    + "Tài khoản: " + loggedIn.getUsername() + "\n"
                                    + "Vai trò: " + loggedIn.getRole() + "\n"
                                    + "Quyền hạn: " + loggedIn.getPermissionSummary(),
                                    "Xác Thực RMI Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Đăng nhập thất bại! Sai tên tài khoản hoặc mật khẩu trên CSDL SQLite của Server.",
                                "Từ Chối Truy Cập", JOptionPane.ERROR_MESSAGE);
                        syncQuickRoleCombo();
                    }
                });
            } catch (Exception e) {
                handleConnectionLoss("đăng nhập");
            }
        }).start();
    }

    private void applyUserPermissionsUI() {
        if (currentUser == null) return;
        userBadgeLabel.setText("👤 Đang đăng nhập: " + currentUser.getUsername()
                + "  |  Vai trò: " + currentUser.getRole()
                + "  |  Quyền hạn: [" + currentUser.getPermissionSummary() + "]");

        setTitle("RMI CLIENT - [" + currentUser.getUsername() + " - " + currentUser.getRole() + "] - ĐỒNG BỘ DỮ LIỆU");

        boolean canAdd = currentUser.isCanAdd();
        boolean canUpdate = currentUser.isCanUpdate();
        boolean canDelete = currentUser.isCanDelete();

        addBtn.setEnabled(canAdd);
        addBtn.setBackground(canAdd ? new Color(40, 167, 69) : new Color(180, 185, 190));

        updateBtn.setEnabled(canUpdate);
        updateBtn.setBackground(canUpdate ? new Color(242, 153, 74) : new Color(180, 185, 190));

        deleteBtn.setEnabled(canDelete);
        deleteBtn.setBackground(canDelete ? new Color(220, 53, 69) : new Color(180, 185, 190));

        boolean canEditFields = canAdd || canUpdate;
        idField.setEditable(canEditFields);
        nameField.setEditable(canEditFields);
        categoryField.setEditable(canEditFields);
        valueField.setEditable(canEditFields);

        syncQuickRoleCombo();
    }

    private void syncQuickRoleCombo() {
        updatingRoleCombo = true;
        try {
            if ("admin".equalsIgnoreCase(currentUsername)) quickRoleCombo.setSelectedIndex(0);
            else if ("editor".equalsIgnoreCase(currentUsername)) quickRoleCombo.setSelectedIndex(1);
            else if ("sinhvien".equalsIgnoreCase(currentUsername)) quickRoleCombo.setSelectedIndex(2);
        } finally {
            updatingRoleCombo = false;
        }
    }

    // ==================== KẾT NỐI RMI & FAILOVER ====================

    private void connectToServer(int serverIndex) {
        this.activeServerIndex = serverIndex;
        String host = (serverIndex == 1) ? server1Host : server2Host;
        int port = (serverIndex == 1) ? server1Port : server2Port;

        connectionStatusLabel.setText("Đang kết nối tới Server " + serverIndex + " (" + host + ":" + port + ")...");

        new Thread(() -> {
            try {
                Registry reg = LocateRegistry.getRegistry(host, port);
                activeService = (IDataSyncService) reg.lookup("DataSyncService");
                boolean alive = activeService.ping();

                if (alive) {
                    RmiUser authUser = activeService.login(currentUsername, currentPassword);
                    if (authUser != null) {
                        currentUser = authUser;
                    }
                    SwingUtilities.invokeLater(() -> {
                        connectionStatusLabel.setText("● Đang kết nối: Server " + activeServerIndex + " (" + host + ":" + port + ") - ONLINE");
                        connectionStatusLabel.setForeground(new Color(40, 167, 69));
                        serverSelectorCombo.setSelectedIndex(activeServerIndex - 1);
                        applyUserPermissionsUI();
                        refreshData();
                    });
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatusLabel.setText("● Server " + activeServerIndex + " (" + host + ":" + port + ") - OFFLINE");
                    connectionStatusLabel.setForeground(new Color(220, 53, 69));

                    if (autoFailoverCheckBox.isSelected()) {
                        int otherIndex = (activeServerIndex == 1) ? 2 : 1;
                        JOptionPane.showMessageDialog(this,
                                "Mất kết nối tới Server " + activeServerIndex + "!\n" +
                                "Hệ thống tự động chuyển đổi dự phòng (Failover) sang Server " + otherIndex + ".",
                                "Auto Failover", JOptionPane.WARNING_MESSAGE);
                        connectToServer(otherIndex);
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể kết nối Server " + activeServerIndex + ": " + e.getMessage(), "Lỗi RMI", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }
        }).start();
    }

    private void refreshData() {
        if (activeService == null) return;
        new Thread(() -> {
            try {
                List<RecordItem> list = activeService.getAllRecords();
                SwingUtilities.invokeLater(() -> {
                    tableModel.setRowCount(0);
                    for (RecordItem it : list) {
                        tableModel.addRow(new Object[]{
                                it.getId(), it.getName(), it.getCategory(), it.getValue(), it.getFormattedDate(), it.getLastUpdatedByServer()
                        });
                    }
                });
            } catch (Exception ex) {
                handleConnectionLoss("nạp dữ liệu");
            }
        }).start();
    }

    private void doAddRecord() {
        if (currentUser != null && !currentUser.isCanAdd()) {
            JOptionPane.showMessageDialog(this, "Tài khoản '" + currentUser.getUsername() + "' không có quyền Thêm bản ghi!", "Từ Chối Quyền Hạn", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String cat = categoryField.getText().trim();
        double val;
        try {
            val = Double.parseDouble(valueField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Điểm/Giá trị phải là số hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Mã ID và Họ Tên!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RecordItem item = new RecordItem(id, name, cat, val, currentUsername + " -> Server " + activeServerIndex);

        new Thread(() -> {
            try {
                boolean ok = activeService.addRecord(item);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(this,
                                "Đã thêm bản ghi " + id + " thành công trên Server " + activeServerIndex + "!\n" +
                                "Dữ liệu đã được lưu vào SQLite (records.db) và đồng bộ sang Server còn lại qua RMI.",
                                "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        clearForm();
                        refreshData();
                    } else {
                        JOptionPane.showMessageDialog(this, "Thêm thất bại (Mã ID có thể đã tồn tại)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                handleConnectionLoss("thêm bản ghi");
            }
        }).start();
    }

    private void doUpdateRecord() {
        if (currentUser != null && !currentUser.isCanUpdate()) {
            JOptionPane.showMessageDialog(this, "Tài khoản '" + currentUser.getUsername() + "' không có quyền Sửa bản ghi!", "Từ Chối Quyền Hạn", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        String cat = categoryField.getText().trim();
        double val;
        try {
            val = Double.parseDouble(valueField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Điểm/Giá trị phải là số hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        RecordItem item = new RecordItem(id, name, cat, val, currentUsername + " -> Server " + activeServerIndex);
        new Thread(() -> {
            try {
                boolean ok = activeService.updateRecord(item);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(this, "Cập nhật bản ghi " + id + " thành công (đã đồng bộ SQLite 2 Server)!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        clearForm();
                        refreshData();
                    } else {
                        JOptionPane.showMessageDialog(this, "Bản ghi không tồn tại để cập nhật!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                handleConnectionLoss("cập nhật bản ghi");
            }
        }).start();
    }

    private void doDeleteRecord() {
        if (currentUser != null && !currentUser.isCanDelete()) {
            JOptionPane.showMessageDialog(this, "Tài khoản '" + currentUser.getUsername() + "' không có quyền Xóa bản ghi!", "Từ Chối Quyền Hạn", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String id = idField.getText().trim();
        if (id.isEmpty()) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa bản ghi " + id + "? (Hành động sẽ đồng bộ xóa trên cả 2 Server)", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                boolean ok = activeService.deleteRecord(id);
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        JOptionPane.showMessageDialog(this, "Đã xóa bản ghi " + id + " khỏi cả 2 Server!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        clearForm();
                        refreshData();
                    } else {
                        JOptionPane.showMessageDialog(this, "Không tìm thấy bản ghi để xóa!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            } catch (Exception e) {
                handleConnectionLoss("xóa bản ghi");
            }
        }).start();
    }

    private void handleConnectionLoss(String action) {
        SwingUtilities.invokeLater(() -> {
            if (autoFailoverCheckBox.isSelected()) {
                int otherIndex = (activeServerIndex == 1) ? 2 : 1;
                JOptionPane.showMessageDialog(this,
                        "Mất kết nối tới Server " + activeServerIndex + " khi " + action + "!\n" +
                        "Đang kích hoạt Failover chuyển sang Server " + otherIndex + "...",
                        "Failover", JOptionPane.WARNING_MESSAGE);
                connectToServer(otherIndex);
            } else {
                JOptionPane.showMessageDialog(this, "Mất kết nối tới Server " + activeServerIndex + "!", "Lỗi Mạng RMI", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void filterTable(String query) {
        if (query.isEmpty()) {
            refreshData();
            return;
        }
        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
            String id = (String) tableModel.getValueAt(i, 0);
            String name = (String) tableModel.getValueAt(i, 1);
            if (!id.toLowerCase().contains(query.toLowerCase()) && !name.toLowerCase().contains(query.toLowerCase())) {
                tableModel.removeRow(i);
            }
        }
    }

    private void clearForm() {
        idField.setText("");
        nameField.setText("");
        categoryField.setText("Công Nghệ Thông Tin");
        valueField.setText("8.5");
        dataTable.clearSelection();
    }

    private void discoverRmiServerInLan(JTextField s1Field, JTextField s2Field) {
        connectionStatusLabel.setText("Đang dò tìm RMI Server trong mạng Wi-Fi (LAN)...");
        new Thread(() -> {
            String foundIp = null;
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(2000);
                byte[] req = "DISCOVER_RMI_SERVER_REQUEST".getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(
                        req, req.length,
                        InetAddress.getByName("255.255.255.255"),
                        1101
                );
                socket.send(packet);

                byte[] buf = new byte[256];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                socket.receive(resp);
                String msg = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8).trim();
                if (msg.startsWith("RMI_SERVER_DISCOVERED")) {
                    foundIp = resp.getAddress().getHostAddress();
                }
            } catch (Exception ignored) {}

            final String ip = foundIp;
            SwingUtilities.invokeLater(() -> {
                if (ip != null) {
                    server1Host = ip;
                    server2Host = ip;
                    if (s1Field != null) s1Field.setText(ip);
                    if (s2Field != null) s2Field.setText(ip);
                    serverSelectorCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                            "Server 1 (" + server1Host + ":" + server1Port + ")",
                            "Server 2 (" + server2Host + ":" + server2Port + ")"
                    }));
                    JOptionPane.showMessageDialog(this,
                            "Đã tìm thấy RMI Server trong mạng LAN tại IP: " + ip,
                            "Tìm IP LAN Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    connectToServer(activeServerIndex);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Không tìm thấy RMI Server tự động qua UDP.\nHãy nhập trực tiếp địa chỉ IP LAN hiện trên tiêu đề của RMI Server.",
                            "Thông Báo", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "RMI-Client-LAN-Discovery").start();
    }

    private void promptChangeServerIps() {
        JTextField s1HostField = new JTextField(server1Host);
        JTextField s1PortField = new JTextField(String.valueOf(server1Port));
        JTextField s2HostField = new JTextField(server2Host);
        JTextField s2PortField = new JTextField(String.valueOf(server2Port));

        JButton autoFindBtn = new JButton("Tìm IP LAN");
        autoFindBtn.addActionListener(e -> discoverRmiServerInLan(s1HostField, s2HostField));

        JPanel s1Panel = new JPanel(new BorderLayout(4, 0));
        s1Panel.add(s1HostField, BorderLayout.CENTER);
        s1Panel.add(autoFindBtn, BorderLayout.EAST);

        JPanel p = new JPanel(new GridLayout(4, 2, 6, 6));
        p.add(new JLabel("Server 1 Host / IP:"));
        p.add(s1Panel);
        p.add(new JLabel("Server 1 Port RMI:"));
        p.add(s1PortField);
        p.add(new JLabel("Server 2 Host / IP:"));
        p.add(s2HostField);
        p.add(new JLabel("Server 2 Port RMI:"));
        p.add(s2PortField);

        int r = JOptionPane.showConfirmDialog(this, p, "Cấu Hình Địa Chỉ IP 2 Máy Thực", JOptionPane.OK_CANCEL_OPTION);
        if (r == JOptionPane.OK_OPTION) {
            server1Host = s1HostField.getText().trim();
            server2Host = s2HostField.getText().trim();
            try {
                server1Port = Integer.parseInt(s1PortField.getText().trim());
                server2Port = Integer.parseInt(s2PortField.getText().trim());
            } catch (Exception ignored) {}

            serverSelectorCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                    "Server 1 (" + server1Host + ":" + server1Port + ")",
                    "Server 2 (" + server2Host + ":" + server2Port + ")"
            }));
            connectToServer(activeServerIndex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RmiClientGUI gui = new RmiClientGUI();
            gui.setVisible(true);
        });
    }
}
