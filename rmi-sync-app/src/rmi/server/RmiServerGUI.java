package rmi.server;

import rmi.common.RecordItem;
import rmi.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Random;

/**
 * Giao diện Quản trị RMI Server Dashboard:
 * - Khởi tạo RMI Registry và Binding dịch vụ
 * - Giám sát trạng thái Online/Offline của Server đối tác (Peer Server)
 * - Hiển thị CSDL phân tán và nhật ký đồng bộ hóa RMI thời gian thực
 */
public class RmiServerGUI extends JFrame implements DataSyncServiceImpl.SyncEventListener {

    private String serverId = "Server 1";
    private int myPort = 1099;
    private String peerHost = "localhost";
    private int peerPort = 1100;
    private String dataDir = "server1_data";

    private DataSyncServiceImpl service;
    private Registry registry;

    private JLabel statusLabel;
    private JLabel peerStatusLabel;
    private JLabel recordCountLabel;
    private JTextArea logArea;
    private DefaultTableModel tableModel;

    public RmiServerGUI(String serverId, int myPort, String peerHost, int peerPort, String dataDir) {
        this.serverId = serverId;
        this.myPort = myPort;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.dataDir = dataDir;

        setTitle("RMI SERVER DASHBOARD - [" + serverId + " - Cổng " + myPort + "]");
        setSize(980, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        startRmiService();
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

        JLabel titleLabel = new JLabel("HỆ THỐNG ĐỒNG BỘ HÓA RMI (" + serverId.toUpperCase() + ")");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(themeColor);

        JLabel subLabel = new JLabel("Cổng nội bộ: " + myPort + "  |  Kết nối đối tác: " + peerHost + ":" + peerPort);
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel.setForeground(new Color(100, 105, 115));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(titleLabel);
        titleBlock.add(subLabel);

        headerPanel.add(titleBlock, BorderLayout.WEST);

        // Nút thêm nhanh dữ liệu mẫu để test đồng bộ
        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerActions.setOpaque(false);

        JButton addSampleBtn = new JButton("+ Thêm Nhanh Bản Ghi");
        addSampleBtn.setBackground(new Color(40, 167, 69));
        addSampleBtn.setForeground(Color.WHITE);
        addSampleBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addSampleBtn.setFocusPainted(false);
        addSampleBtn.addActionListener(e -> addSampleRecord());

        JButton resyncBtn = new JButton("🔄 Kéo Toàn Bộ Dữ Liệu (Full Sync)");
        resyncBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resyncBtn.addActionListener(e -> forceFullSync());

        headerActions.add(addSampleBtn);
        headerActions.add(resyncBtn);
        headerPanel.add(headerActions, BorderLayout.EAST);

        // Stats Cards Bar
        JPanel statsBar = new JPanel(new GridLayout(1, 3, 10, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái " + serverId, "Đang Khởi Động...", new Color(108, 117, 125));
        peerStatusLabel = createStatCard("Server Đối Tác (" + peerHost + ":" + peerPort + ")", "Đang Kiểm Tra...", new Color(242, 153, 74));
        recordCountLabel = createStatCard("Tổng Số Bản Ghi CSDL", "0 Bản Ghi", themeColor);

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

        // Tab 1: CSDL Phân Tán Cục Bộ
        JPanel dataTab = new JPanel(new BorderLayout(8, 8));
        dataTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        tableModel = new DefaultTableModel(new String[]{
                "Mã ID", "Họ Tên / Tên Bản Ghi", "Ngành Học / Phân Loại", "Điểm / Giá Trị", "Thời Gian Cập Nhật", "Server Xuất Phát"
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
        tabbedPane.addTab("  Cơ Sở Dữ Liệu Phân Tán (Local Storage)  ", dataTab);

        // Tab 2: Nhật Ký Đồng Bộ RMI
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

        RecordItem item = new RecordItem(id, name, dept, score, serverId);
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
                java.rmi.registry.Registry peerReg = LocateRegistry.getRegistry(peerHost, peerPort);
                rmi.common.IDataSyncService peer = (rmi.common.IDataSyncService) peerReg.lookup("DataSyncService");
                List<RecordItem> fullList = peer.requestFullSync();
                if (fullList != null) {
                    for (RecordItem it : fullList) {
                        service.syncReplicate("ADD", it, "FORCE_SYNC");
                    }
                    JOptionPane.showMessageDialog(this, "Đã đồng bộ toàn bộ (" + fullList.size() + " bản ghi) từ Server đối tác!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể kết nối Server đối tác: " + ex.getMessage(), "Lỗi Đồng Bộ", JOptionPane.ERROR_MESSAGE);
            }
        }).start();
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
                    + "<span style='font-size: 10px; color: #555555;'>Tổng Số Bản Ghi CSDL</span><br>"
                    + "<b style='font-size: 13px; color: #1877f2;'>" + currentData.size() + " Bản Ghi</b></div></html>");
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

