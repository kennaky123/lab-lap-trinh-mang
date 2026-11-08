package ftp.client;

import ftp.common.FtpConfig;
import ftp.common.FtpFileItem;
import ftp.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao diện FTP Client phong cách FileZilla chuyên nghiệp:
 * - Thanh kết nối nhanh (Host, Username, Password, Port)
 * - Khung nhật ký phản hồi lệnh FTP chuẩn RFC 959
 * - Giao diện song song 2 khung: Máy cục bộ (Local Site) và Máy chủ từ xa (Remote Site)
 * - Hỗ trợ Tải lên (Upload), Tải xuống (Download), Tạo/Xóa thư mục, Đổi tên
 * - Hàng đợi truyền file và thanh tiến trình thời gian thực
 */
public class FtpClientGUI extends JFrame implements FtpClient.FtpLogListener {

    private final FtpClient client = new FtpClient();

    // Quickconnect Bar Components
    private JTextField hostField;
    private JTextField userField;
    private JPasswordField passField;
    private JTextField portField;
    private JButton connectBtn;
    private JButton disconnectBtn;

    // Log Console
    private JTextArea logArea;

    // Local Explorer Components
    private File currentLocalDir;
    private JTextField localPathField;
    private DefaultTableModel localTableModel;
    private JTable localTable;

    // Remote Explorer Components
    private String currentRemotePath = "/";
    private JTextField remotePathField;
    private DefaultTableModel remoteTableModel;
    private JTable remoteTable;

    // Transfer Queue / Progress
    private JProgressBar progressBar;
    private JLabel progressStatusLabel;
    private DefaultTableModel queueTableModel;

    public FtpClientGUI() {
        setTitle("FILEZILLA FTP CLIENT - LẬP TRÌNH MẠNG (RFC 959)");
        setSize(1080, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Khởi tạo thư mục cục bộ mặc định
        File defaultLocal = new File(FtpConfig.CLIENT_LOCAL_DIR);
        if (!defaultLocal.exists()) defaultLocal.mkdirs();
        this.currentLocalDir = defaultLocal.getAbsoluteFile();

        client.addLogListener(this);
        initUI();
        refreshLocalFiles();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel rootPanel = new JPanel(new BorderLayout(0, 4));
        rootPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        rootPanel.setBackground(new Color(242, 244, 248));

        // ==================== 1. QUICKCONNECT BAR ====================
        JPanel topContainer = new JPanel(new BorderLayout(0, 4));
        topContainer.setOpaque(false);

        JPanel quickBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        quickBar.setBackground(Color.WHITE);
        quickBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 220, 230), 1),
                new EmptyBorder(4, 6, 4, 6)
        ));

        quickBar.add(new JLabel("Host (IP):"));
        hostField = new JTextField("localhost", 10);
        quickBar.add(hostField);

        JButton findLanBtn = new JButton("Tìm IP LAN");
        findLanBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        findLanBtn.setFocusPainted(false);
        findLanBtn.setToolTipText("Tự động dò tìm IP máy chủ FTP trong cùng mạng Wi-Fi");
        findLanBtn.addActionListener(e -> discoverFtpServerInLan());
        quickBar.add(findLanBtn);

        quickBar.add(new JLabel("Username:"));
        userField = new JTextField("admin", 8);
        quickBar.add(userField);

        quickBar.add(new JLabel("Mật khẩu:"));
        passField = new JPasswordField("123456", 7);
        quickBar.add(passField);

        quickBar.add(new JLabel("Cổng:"));
        portField = new JTextField(String.valueOf(FtpConfig.DEFAULT_PORT), 4);
        quickBar.add(portField);

        connectBtn = new JButton("Kết Nối Nhanh");
        connectBtn.setBackground(new Color(0, 120, 215));
        connectBtn.setForeground(Color.WHITE);
        connectBtn.setContentAreaFilled(false);
        connectBtn.setOpaque(true);
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        connectBtn.setFocusPainted(false);
        connectBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        connectBtn.addActionListener(e -> doConnect());
        quickBar.add(connectBtn);

        disconnectBtn = new JButton("Ngắt Kết Nối");
        disconnectBtn.setEnabled(false);
        disconnectBtn.setFocusPainted(false);
        disconnectBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        disconnectBtn.addActionListener(e -> doDisconnect());
        quickBar.add(disconnectBtn);

        topContainer.add(quickBar, BorderLayout.NORTH);

        // ==================== 2. LOG CONSOLE (TRẠNG THÁI LỆNH) ====================
        logArea = new JTextArea(5, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(28, 32, 40));
        logArea.setForeground(new Color(220, 225, 235));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225), 1));
        logScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        topContainer.add(logScroll, BorderLayout.CENTER);
        rootPanel.add(topContainer, BorderLayout.NORTH);

        // ==================== 3. DUAL-PANE EXPLORER (LOCAL & REMOTE) ====================
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);

        // Khung bên trái: Máy Cục Bộ (Local Site)
        JPanel localPanel = createLocalExplorerPanel();
        splitPane.setLeftComponent(localPanel);

        // Khung bên phải: Máy Chủ Từ Xa (Remote Site)
        JPanel remotePanel = createRemoteExplorerPanel();
        splitPane.setRightComponent(remotePanel);

        rootPanel.add(splitPane, BorderLayout.CENTER);

        // ==================== 4. TRANSFER QUEUE & PROGRESS BAR ====================
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 4));
        bottomPanel.setPreferredSize(new Dimension(0, 160));
        bottomPanel.setOpaque(false);

        // Progress bar container
        JPanel progressPanel = new JPanel(new BorderLayout(8, 0));
        progressPanel.setBackground(Color.WHITE);
        progressPanel.setBorder(new EmptyBorder(4, 8, 4, 8));

        progressStatusLabel = new JLabel("Trạng thái: Sẵn sàng");
        progressStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(280, 20));

        progressPanel.add(progressStatusLabel, BorderLayout.CENTER);
        progressPanel.add(progressBar, BorderLayout.EAST);

        bottomPanel.add(progressPanel, BorderLayout.NORTH);

        // Queue table
        queueTableModel = new DefaultTableModel(new String[]{
                "Tên Tệp Tin", "Hướng Truyền", "Dung Lượng", "Tiến Độ", "Trạng Thái"
        }, 0);
        JTable queueTable = new JTable(queueTableModel);
        queueTable.setRowHeight(22);
        queueTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        JScrollPane queueScroll = new JScrollPane(queueTable);
        queueScroll.setBorder(BorderFactory.createLineBorder(new Color(210, 215, 225), 1));
        queueScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        bottomPanel.add(queueScroll, BorderLayout.CENTER);

        rootPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(rootPanel);
    }

    private JPanel createLocalExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 220, 230), 1),
                new EmptyBorder(6, 6, 6, 6)
        ));

        // Header Local
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        JLabel title = new JLabel("📁 Máy Cục Bộ (Local Site):");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));

        localPathField = new JTextField(currentLocalDir.getAbsolutePath());
        localPathField.setEditable(false);

        JButton upBtn = new JButton("▲ Lên");
        upBtn.setToolTipText("Lên thư mục cha");
        upBtn.addActionListener(e -> navigateLocalUp());

        JButton refreshBtn = new JButton("Làm Mới");
        refreshBtn.addActionListener(e -> refreshLocalFiles());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(upBtn);
        btnPanel.add(refreshBtn);

        header.add(title, BorderLayout.WEST);
        header.add(localPathField, BorderLayout.CENTER);
        header.add(btnPanel, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // Local Table
        localTableModel = new DefaultTableModel(new String[]{"Tên Tệp Tin", "Kích Thước", "Loại", "Ngày Sửa Đổi"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        localTable = new JTable(localTableModel);
        localTable.setRowHeight(24);
        localTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        localTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double click to enter folder
        localTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = localTable.getSelectedRow();
                    if (row != -1) {
                        String name = (String) localTableModel.getValueAt(row, 0);
                        File target = new File(currentLocalDir, name);
                        if (target.isDirectory()) {
                            currentLocalDir = target;
                            localPathField.setText(currentLocalDir.getAbsolutePath());
                            refreshLocalFiles();
                        }
                    }
                }
            }
        });

        // Context Menu
        JPopupMenu localMenu = new JPopupMenu();
        JMenuItem uploadItem = new JMenuItem("⬆ Tải Lên Máy Chủ (Upload)");
        uploadItem.addActionListener(e -> uploadSelectedLocalFile());
        JMenuItem uploadFolderItem = new JMenuItem("📁 Tải Lên Cả Thư Mục (Upload Folder)");
        uploadFolderItem.addActionListener(e -> uploadSelectedLocalFolderAction());
        JMenuItem newFolderItem = new JMenuItem("+ Tạo Thư Mục Cục Bộ Mới");
        newFolderItem.addActionListener(e -> createLocalFolder());
        JMenuItem deleteLocalItem = new JMenuItem("Xóa Tệp/Thư Mục Cục Bộ");
        deleteLocalItem.addActionListener(e -> deleteSelectedLocalFile());

        localMenu.add(uploadItem);
        localMenu.add(uploadFolderItem);
        localMenu.addSeparator();
        localMenu.add(newFolderItem);
        localMenu.add(deleteLocalItem);

        localTable.setComponentPopupMenu(localMenu);

        JScrollPane scroll = new JScrollPane(localTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(230, 235, 242), 1));
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        // Action button bar
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actionPanel.setOpaque(false);
        JButton uploadBtn = new JButton("⬆ Tải Lên (Upload)");
        uploadBtn.setBackground(new Color(40, 167, 69));
        uploadBtn.setForeground(Color.WHITE);
        uploadBtn.setContentAreaFilled(false);
        uploadBtn.setOpaque(true);
        uploadBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        uploadBtn.setToolTipText("Tải lên tệp tin hoặc thư mục đang chọn");
        uploadBtn.addActionListener(e -> uploadSelectedLocalFile());
        actionPanel.add(uploadBtn);

        JButton uploadFolderBtn = new JButton("📁 Tải Thư Mục");
        uploadFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        uploadFolderBtn.setToolTipText("Tải toàn bộ một thư mục lên Server");
        uploadFolderBtn.addActionListener(e -> uploadSelectedLocalFolderAction());
        actionPanel.add(uploadFolderBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createRemoteExplorerPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 220, 230), 1),
                new EmptyBorder(6, 6, 6, 6)
        ));

        // Header Remote
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        JLabel title = new JLabel("🌐 Máy Chủ Từ Xa (Remote Site):");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));

        remotePathField = new JTextField("/");
        remotePathField.setEditable(false);

        JButton upBtn = new JButton("▲ Lên");
        upBtn.setToolTipText("Lên thư mục cha trên Server");
        upBtn.addActionListener(e -> navigateRemoteUp());

        JButton refreshBtn = new JButton("Làm Mới");
        refreshBtn.addActionListener(e -> refreshRemoteFiles());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(upBtn);
        btnPanel.add(refreshBtn);

        header.add(title, BorderLayout.WEST);
        header.add(remotePathField, BorderLayout.CENTER);
        header.add(btnPanel, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // Remote Table
        remoteTableModel = new DefaultTableModel(new String[]{"Tên Tệp Tin", "Kích Thước", "Quyền Hạn", "Loại"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        remoteTable = new JTable(remoteTableModel);
        remoteTable.setRowHeight(24);
        remoteTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        remoteTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double click to enter remote folder
        remoteTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = remoteTable.getSelectedRow();
                    if (row != -1) {
                        String name = (String) remoteTableModel.getValueAt(row, 0);
                        String type = (String) remoteTableModel.getValueAt(row, 3);
                        if ("Thư Mục".equals(type)) {
                            new Thread(() -> {
                                if (client.cwd(name)) {
                                    currentRemotePath = client.pwd();
                                    SwingUtilities.invokeLater(() -> {
                                        remotePathField.setText(currentRemotePath);
                                        refreshRemoteFiles();
                                    });
                                }
                            }).start();
                        }
                    }
                }
            }
        });

        // Context Menu Remote
        JPopupMenu remoteMenu = new JPopupMenu();
        JMenuItem downloadItem = new JMenuItem("⬇ Tải Về Máy (Download)");
        downloadItem.addActionListener(e -> downloadSelectedRemoteFile());
        JMenuItem downloadFolderItem = new JMenuItem("📁 Tải Về Cả Thư Mục (Download Folder)");
        downloadFolderItem.addActionListener(e -> downloadSelectedRemoteFolderAction());
        JMenuItem newRemoteFolderItem = new JMenuItem("+ Tạo Thư Mục Trên Server (MKD)");
        newRemoteFolderItem.addActionListener(e -> createRemoteFolder());
        JMenuItem deleteRemoteFileItem = new JMenuItem("Xóa Tệp Tin Trên Server (DELE)");
        deleteRemoteFileItem.addActionListener(e -> deleteSelectedRemoteItem());
        JMenuItem deleteRemoteDirItem = new JMenuItem("Xóa Thư Mục Trên Server (RMD)");
        deleteRemoteDirItem.addActionListener(e -> deleteSelectedRemoteItem());

        remoteMenu.add(downloadItem);
        remoteMenu.add(downloadFolderItem);
        remoteMenu.addSeparator();
        remoteMenu.add(newRemoteFolderItem);
        remoteMenu.add(deleteRemoteFileItem);
        remoteMenu.add(deleteRemoteDirItem);

        remoteTable.setComponentPopupMenu(remoteMenu);

        JScrollPane scroll = new JScrollPane(remoteTable);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(230, 235, 242), 1));
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        // Action button bar
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actionPanel.setOpaque(false);
        JButton downloadBtn = new JButton("⬇ Tải Về (Download)");
        downloadBtn.setBackground(new Color(0, 123, 255));
        downloadBtn.setForeground(Color.WHITE);
        downloadBtn.setContentAreaFilled(false);
        downloadBtn.setOpaque(true);
        downloadBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        downloadBtn.setToolTipText("Tải về tệp tin hoặc thư mục đang chọn");
        downloadBtn.addActionListener(e -> downloadSelectedRemoteFile());

        JButton downloadFolderBtn = new JButton("📁 Tải Thư Mục");
        downloadFolderBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        downloadFolderBtn.setToolTipText("Tải toàn bộ thư mục được chọn về máy");
        downloadFolderBtn.addActionListener(e -> downloadSelectedRemoteFolderAction());

        JButton mkdirBtn = new JButton("+ Tạo Thư Mục");
        mkdirBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        mkdirBtn.addActionListener(e -> createRemoteFolder());

        JButton deleteBtn = new JButton("Xóa");
        deleteBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        deleteBtn.addActionListener(e -> deleteSelectedRemoteItem());

        actionPanel.add(downloadBtn);
        actionPanel.add(downloadFolderBtn);
        actionPanel.add(mkdirBtn);
        actionPanel.add(deleteBtn);

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== XỬ LÝ KẾT NỐI & NGẮT KẾT NỐI ====================

    private void discoverFtpServerInLan() {
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1500);
            byte[] sendData = "DISCOVER_FTP_SERVER_REQUEST".getBytes();
            java.net.DatagramPacket sendPkt = new java.net.DatagramPacket(
                    sendData, sendData.length,
                    java.net.InetAddress.getByName("255.255.255.255"),
                    FtpConfig.UDP_DISCOVERY_PORT
            );
            socket.send(sendPkt);

            byte[] recvBuf = new byte[256];
            java.net.DatagramPacket recvPkt = new java.net.DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(recvPkt);
            String msg = new String(recvPkt.getData(), 0, recvPkt.getLength()).trim();
            if (msg.startsWith("FTP_SERVER_DISCOVERED")) {
                String foundIp = recvPkt.getAddress().getHostAddress();
                hostField.setText(foundIp);
                int colon = msg.indexOf(':');
                if (colon != -1) {
                    portField.setText(msg.substring(colon + 1).trim());
                }
                JOptionPane.showMessageDialog(this,
                        "Đã tìm thấy FTP Server trong mạng Wi-Fi tại IP: " + foundIp + "\nBấm 'Kết Nối Nhanh' để đăng nhập ngay!",
                        "Tìm Thấy FTP Server", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        } catch (Exception ignored) {}
        JOptionPane.showMessageDialog(this,
                "Không tự động dò thấy qua UDP Broadcast.\nHãy nhập trực tiếp địa chỉ IPv4 hiện trên màn hình Log của máy FTP Server (ví dụ: 192.168.1.x).",
                "Nhập IP Thủ Công", JOptionPane.WARNING_MESSAGE);
    }

    private void doConnect() {
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        connectBtn.setEnabled(false);
        progressStatusLabel.setText("Đang kết nối tới " + host + ":" + port + "...");

        new Thread(() -> {
            boolean ok = client.connect(host, port);
            if (ok) {
                boolean loggedIn = client.login(user, pass);
                if (loggedIn) {
                    currentRemotePath = client.pwd();
                    SwingUtilities.invokeLater(() -> {
                        connectBtn.setEnabled(false);
                        disconnectBtn.setEnabled(true);
                        remotePathField.setText(currentRemotePath);
                        progressStatusLabel.setText("Đã đăng nhập thành công (" + user + ")");
                        refreshRemoteFiles();
                    });
                } else {
                    client.disconnect();
                    SwingUtilities.invokeLater(() -> {
                        connectBtn.setEnabled(true);
                        progressStatusLabel.setText("Đăng nhập thất bại");
                        JOptionPane.showMessageDialog(this, "Đăng nhập thất bại! Kiểm tra username/password.", "Lỗi Xác Thực", JOptionPane.ERROR_MESSAGE);
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> {
                    connectBtn.setEnabled(true);
                    progressStatusLabel.setText("Không thể kết nối máy chủ");
                    JOptionPane.showMessageDialog(this, "Không thể kết nối tới FTP Server tại " + host + ":" + port, "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void doDisconnect() {
        client.disconnect();
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        remoteTableModel.setRowCount(0);
        remotePathField.setText("/");
        progressStatusLabel.setText("Đã ngắt kết nối");
    }

    // ==================== LOCAL FILE ACTIONS ====================

    private void refreshLocalFiles() {
        localTableModel.setRowCount(0);
        File[] files = currentLocalDir.listFiles();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        if (files != null) {
            for (File f : files) {
                String sizeStr = f.isDirectory() ? "<THƯ MỤC>" : formatSize(f.length());
                String typeStr = f.isDirectory() ? "Thư Mục" : "Tệp Tin";
                localTableModel.addRow(new Object[]{f.getName(), sizeStr, typeStr, sdf.format(new Date(f.lastModified()))});
            }
        }
    }

    private void navigateLocalUp() {
        File parent = currentLocalDir.getParentFile();
        if (parent != null) {
            currentLocalDir = parent;
            localPathField.setText(currentLocalDir.getAbsolutePath());
            refreshLocalFiles();
        }
    }

    private void createLocalFolder() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục cục bộ mới:", "Tạo Thư Mục", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            File f = new File(currentLocalDir, name.trim());
            if (f.mkdir()) {
                refreshLocalFiles();
            } else {
                JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteSelectedLocalFile() {
        int row = localTable.getSelectedRow();
        if (row == -1) return;
        String name = (String) localTableModel.getValueAt(row, 0);
        File f = new File(currentLocalDir, name);
        String prompt = f.isDirectory() ? "Bạn có chắc muốn xóa toàn bộ thư mục " + name + " và tất cả nội dung bên trong?"
                                        : "Bạn có chắc muốn xóa tệp tin " + name + "?";
        int confirm = JOptionPane.showConfirmDialog(this, prompt, "Xác Nhận Xóa Cục Bộ", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (deleteLocalRecursively(f)) {
                refreshLocalFiles();
            } else {
                JOptionPane.showMessageDialog(this, "Xóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean deleteLocalRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteLocalRecursively(child);
                }
            }
        }
        return file.delete();
    }

    // ==================== REMOTE FILE ACTIONS ====================

    private void refreshRemoteFiles() {
        if (!client.isConnected()) return;
        new Thread(() -> {
            try {
                List<FtpFileItem> items = client.listFiles();
                SwingUtilities.invokeLater(() -> {
                    remoteTableModel.setRowCount(0);
                    for (FtpFileItem item : items) {
                        remoteTableModel.addRow(new Object[]{
                                item.getName(),
                                item.getFormattedSize(),
                                item.getPermissions(),
                                item.isDirectory() ? "Thư Mục" : "Tệp Tin"
                        });
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    progressStatusLabel.setText("Lỗi nạp danh sách tệp: " + e.getMessage());
                });
            }
        }).start();
    }

    private void navigateRemoteUp() {
        if (!client.isConnected()) return;
        new Thread(() -> {
            if (client.cdup()) {
                currentRemotePath = client.pwd();
                SwingUtilities.invokeLater(() -> {
                    remotePathField.setText(currentRemotePath);
                    refreshRemoteFiles();
                });
            }
        }).start();
    }

    private void createRemoteFolder() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối máy chủ!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục cần tạo trên Server (MKD):", "Tạo Thư Mục Server", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            new Thread(() -> {
                boolean ok = client.makeDirectory(name.trim());
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        refreshRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể tạo thư mục (Có thể thiếu quyền Write)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }).start();
        }
    }

    private void deleteSelectedRemoteItem() {
        if (!client.isConnected()) return;
        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một mục trên Server để xóa!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = (String) remoteTableModel.getValueAt(row, 0);
        String type = (String) remoteTableModel.getValueAt(row, 3);

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa " + name + " trên Server?", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            boolean ok;
            if ("Thư Mục".equals(type)) {
                ok = client.removeDirectory(name);
            } else {
                ok = client.deleteFile(name);
            }
            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    refreshRemoteFiles();
                } else {
                    JOptionPane.showMessageDialog(this, "Xóa thất bại (Có thể thiếu quyền Delete hoặc thư mục không rỗng)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    // ==================== UPLOAD & DOWNLOAD ====================

    private void uploadSelectedLocalFile() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối tới FTP Server trước khi tải lên!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = localTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp tin hoặc thư mục cục bộ để tải lên!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = (String) localTableModel.getValueAt(row, 0);
        File localFile = new File(currentLocalDir, name);
        if (localFile.isDirectory()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Mục bạn chọn là thư mục '" + name + "'.\nBạn có muốn tải toàn bộ thư mục này và các tệp con lên Server không?",
                    "Xác Nhận Tải Thư Mục", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                uploadFolderRecursively(localFile);
            }
            return;
        }

        int queueRow = queueTableModel.getRowCount();
        queueTableModel.addRow(new Object[]{name, "Tải Lên (Upload)", formatSize(localFile.length()), "0%", "Đang truyền..."});

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressStatusLabel.setText("Đang tải lên " + name + "...");
            });

            boolean ok = client.uploadFile(localFile, name, (transferred, total) -> {
                if (total > 0) {
                    int percent = (int) ((transferred * 100) / total);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        queueTableModel.setValueAt(percent + "%", queueRow, 3);
                    });
                }
            });

            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    progressBar.setValue(100);
                    progressStatusLabel.setText("Tải lên thành công: " + name);
                    queueTableModel.setValueAt("100%", queueRow, 3);
                    queueTableModel.setValueAt("Thành Công", queueRow, 4);
                    refreshRemoteFiles();
                } else {
                    progressStatusLabel.setText("Tải lên thất bại: " + name);
                    queueTableModel.setValueAt("Thất Bại", queueRow, 4);
                    JOptionPane.showMessageDialog(this, "Tải lên thất bại (Có thể do quyền Write bị từ chối)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void uploadSelectedLocalFolderAction() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối tới FTP Server trước khi tải lên!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = localTable.getSelectedRow();
        if (row != -1) {
            String name = (String) localTableModel.getValueAt(row, 0);
            File localFile = new File(currentLocalDir, name);
            if (localFile.isDirectory()) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Bạn có chắc muốn tải toàn bộ thư mục '" + name + "' lên Server?",
                        "Xác Nhận Tải Thư Mục", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    uploadFolderRecursively(localFile);
                }
                return;
            }
        }

        // Mở hộp thoại chọn thư mục nếu chưa chọn hoặc đang chọn file
        JFileChooser chooser = new JFileChooser(currentLocalDir);
        chooser.setDialogTitle("Chọn thư mục để tải lên Server");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = chooser.getSelectedFile();
            if (selectedFolder != null && selectedFolder.isDirectory()) {
                uploadFolderRecursively(selectedFolder);
            }
        }
    }

    private void uploadFolderRecursively(File localDir) {
        if (!client.isConnected() || localDir == null || !localDir.isDirectory()) return;

        int totalFiles = FtpClient.countLocalFiles(localDir);
        long totalBytes = FtpClient.calculateLocalDirectorySize(localDir);
        String folderName = localDir.getName();

        int queueRow = queueTableModel.getRowCount();
        queueTableModel.addRow(new Object[]{
                "📁 " + folderName,
                "Tải Lên Thư Mục",
                formatSize(totalBytes) + " (" + totalFiles + " tệp)",
                "0%",
                "Đang gửi..."
        });

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressStatusLabel.setText("Đang tải lên thư mục " + folderName + " (" + totalFiles + " tệp)...");
            });

            boolean ok = client.uploadDirectory(localDir, folderName, new FtpClient.DirectoryTransferListener() {
                @Override
                public void onFileStart(String relativePath, int fileIndex, int total, long fileSize) {
                    SwingUtilities.invokeLater(() -> {
                        progressStatusLabel.setText(String.format("Đang tải lên [%d/%d]: %s (%s)",
                                fileIndex, total, relativePath, formatSize(fileSize)));
                    });
                }

                @Override
                public void onFileProgress(int fileIndex, int total, long transferredBytes, long fileSize) {
                    if (total > 0) {
                        int filePct = fileSize > 0 ? (int) ((transferredBytes * 100) / fileSize) : 100;
                        int overallPct = (int) (((fileIndex - 1 + (filePct / 100.0)) / total) * 100);
                        overallPct = Math.min(100, Math.max(0, overallPct));
                        final int p = overallPct;
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(p);
                            queueTableModel.setValueAt(String.format("%d%% (%d/%d)", p, fileIndex, total), queueRow, 3);
                        });
                    }
                }

                @Override
                public void onFileComplete(String relativePath, boolean success) {
                }
            });

            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    progressBar.setValue(100);
                    progressStatusLabel.setText("Đã tải lên toàn bộ thư mục: " + folderName);
                    queueTableModel.setValueAt("100%", queueRow, 3);
                    queueTableModel.setValueAt("Thành Công", queueRow, 4);
                    refreshRemoteFiles();
                    JOptionPane.showMessageDialog(this,
                            "Đã tải lên toàn bộ thư mục '" + folderName + "' thành công!\nSố lượng tệp: " + totalFiles + " (" + formatSize(totalBytes) + ")",
                            "Tải Lên Thư Mục Hoàn Tất", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    progressStatusLabel.setText("Tải lên thư mục thất bại: " + folderName);
                    queueTableModel.setValueAt("Thất Bại", queueRow, 4);
                    refreshRemoteFiles();
                    JOptionPane.showMessageDialog(this, "Tải lên thư mục gặp lỗi (Có thể do quyền Write bị từ chối)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void downloadSelectedRemoteFile() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp tin hoặc thư mục trên Server để tải về!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = (String) remoteTableModel.getValueAt(row, 0);
        String type = (String) remoteTableModel.getValueAt(row, 3);
        if ("Thư Mục".equals(type)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Mục bạn chọn là thư mục '" + name + "'.\nBạn có muốn tải toàn bộ thư mục này và tất cả tệp tin con về máy cục bộ không?",
                    "Xác Nhận Tải Thư Mục", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                downloadFolderRecursively(name);
            }
            return;
        }

        File destFile = new File(currentLocalDir, name);
        int queueRow = queueTableModel.getRowCount();
        queueTableModel.addRow(new Object[]{name, "Tải Về (Download)", remoteTableModel.getValueAt(row, 1), "0%", "Đang truyền..."});

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressStatusLabel.setText("Đang tải về " + name + "...");
            });

            boolean ok = client.downloadFile(name, destFile, (transferred, total) -> {
                if (total > 0) {
                    int percent = (int) ((transferred * 100) / total);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percent);
                        queueTableModel.setValueAt(percent + "%", queueRow, 3);
                    });
                }
            });

            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    progressBar.setValue(100);
                    progressStatusLabel.setText("Tải về thành công: " + name);
                    queueTableModel.setValueAt("100%", queueRow, 3);
                    queueTableModel.setValueAt("Thành Công", queueRow, 4);
                    refreshLocalFiles();
                } else {
                    progressStatusLabel.setText("Tải về thất bại: " + name);
                    queueTableModel.setValueAt("Thất Bại", queueRow, 4);
                    JOptionPane.showMessageDialog(this, "Tải về thất bại (Có thể do quyền Read bị từ chối)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private void downloadSelectedRemoteFolderAction() {
        if (!client.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thư mục trên Server để tải về toàn bộ!", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = (String) remoteTableModel.getValueAt(row, 0);
        String type = (String) remoteTableModel.getValueAt(row, 3);
        if (!"Thư Mục".equals(type)) {
            JOptionPane.showMessageDialog(this, "Mục bạn chọn không phải là thư mục! Vui lòng chọn một thư mục để tải về toàn bộ.", "Thông Báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn tải toàn bộ thư mục '" + name + "' và các tệp tin con về máy cục bộ không?",
                "Xác Nhận Tải Thư Mục", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            downloadFolderRecursively(name);
        }
    }

    private void downloadFolderRecursively(String remoteDirName) {
        if (!client.isConnected()) return;

        int queueRow = queueTableModel.getRowCount();
        queueTableModel.addRow(new Object[]{
                "📁 " + remoteDirName,
                "Tải Về Thư Mục",
                "--",
                "0%",
                "Đang tải..."
        });

        new Thread(() -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                progressStatusLabel.setText("Đang tải về toàn bộ thư mục: " + remoteDirName + "...");
            });

            final int[] fileCount = new int[]{0};

            boolean ok = client.downloadDirectory(remoteDirName, currentLocalDir, new FtpClient.DirectoryTransferListener() {
                @Override
                public void onFileStart(String relativePath, int fileIndex, int total, long fileSize) {
                    fileCount[0] = fileIndex;
                    SwingUtilities.invokeLater(() -> {
                        progressStatusLabel.setText(String.format("Đang tải về tệp [%d]: %s (%s)",
                                fileIndex, relativePath, formatSize(fileSize)));
                        queueTableModel.setValueAt(fileIndex + " tệp", queueRow, 3);
                    });
                }

                @Override
                public void onFileProgress(int fileIndex, int total, long transferredBytes, long fileSize) {
                    if (fileSize > 0) {
                        int filePct = (int) ((transferredBytes * 100) / fileSize);
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setValue(filePct);
                        });
                    }
                }

                @Override
                public void onFileComplete(String relativePath, boolean success) {
                }
            });

            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    progressBar.setValue(100);
                    progressStatusLabel.setText("Đã tải về thành công thư mục: " + remoteDirName);
                    queueTableModel.setValueAt("100%", queueRow, 3);
                    queueTableModel.setValueAt("Thành Công", queueRow, 4);
                    refreshLocalFiles();
                    JOptionPane.showMessageDialog(this,
                            "Đã tải về toàn bộ thư mục '" + remoteDirName + "' thành công!\nTổng cộng: " + fileCount[0] + " tệp tin.",
                            "Tải Về Thư Mục Hoàn Tất", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    progressStatusLabel.setText("Tải về thư mục thất bại: " + remoteDirName);
                    queueTableModel.setValueAt("Thất Bại", queueRow, 4);
                    refreshLocalFiles();
                    JOptionPane.showMessageDialog(this, "Tải về thư mục gặp lỗi (Có thể do quyền Read bị từ chối)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });
        }).start();
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    // ==================== LOG CALLBACKS ====================

    @Override
    public void onCommand(String cmd) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("Lệnh:  " + cmd + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    @Override
    public void onResponse(String resp) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("Phản hồi: " + resp + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FtpClientGUI gui = new FtpClientGUI();
            gui.setVisible(true);
        });
    }
}

