package web.server;

import web.common.HttpConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.net.URI;

/**
 * Giao diện Quản trị Web Server bằng Java Swing:
 * - Giám sát đa dịch vụ (HTML, PDF, MP3, MP4)
 * - Theo dõi các phương thức HTTP (GET, HEAD, POST)
 * - Nhật ký truy cập (Access Log) & Dữ liệu Form POST từ Client
 */
public class WebServerGUI extends JFrame implements HttpServer.ServerEventListener {
    private HttpServer server;

    private JTextField portField;
    private JButton startBtn;
    private JButton stopBtn;
    private JButton openBrowserBtn;

    private JLabel statusLabel;
    private JLabel totalReqLabel;
    private JLabel pdfStatLabel;
    private JLabel mp3StatLabel;
    private JLabel mp4StatLabel;

    private JTextArea logArea;
    private DefaultTableModel accessTableModel;
    private DefaultTableModel postTableModel;

    public WebServerGUI() {
        setTitle("MÁY CHỦ WEB SERVER (HTTP/1.1) - ĐA DỊCH VỤ & GIÁM SÁT GIAO THỨC");
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
                BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));

        JLabel titleLabel = new JLabel("BẢNG ĐIỀU KHIỂN WEB SERVER (HTTP/1.1)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(26, 115, 232));

        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        ctrlPanel.setOpaque(false);

        ctrlPanel.add(new JLabel("Cổng HTTP:"));
        portField = new JTextField(String.valueOf(HttpConstants.DEFAULT_PORT), 5);
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

        openBrowserBtn = new JButton("Mở Trên Trình Duyệt");
        openBrowserBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        openBrowserBtn.addActionListener(e -> openUrlInBrowser("http://localhost:" + portField.getText().trim() + "/"));
        ctrlPanel.add(openBrowserBtn);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(ctrlPanel, BorderLayout.EAST);

        // Stats Cards Bar (Đa Dịch Vụ: Web, PDF, MP3, MP4)
        JPanel statsBar = new JPanel(new GridLayout(1, 5, 8, 0));
        statsBar.setOpaque(false);
        statsBar.setBorder(new EmptyBorder(8, 0, 8, 0));

        statusLabel = createStatCard("Trạng Thái Máy Chủ", "Đang Dừng", new Color(108, 117, 125));
        totalReqLabel = createStatCard("Tổng Lượt Request", "0 Lượt", new Color(0, 123, 255));
        pdfStatLabel = createStatCard("Dịch Vụ PDF (.pdf)", "0 Tải", new Color(217, 48, 37));
        mp3StatLabel = createStatCard("Dịch Vụ Audio (.mp3)", "0 Lượt", new Color(40, 167, 69));
        mp4StatLabel = createStatCard("Dịch Vụ Video (.mp4)", "0 Lượt", new Color(242, 153, 74));

        statsBar.add(statusLabel);
        statsBar.add(totalReqLabel);
        statsBar.add(pdfStatLabel);
        statsBar.add(mp3StatLabel);
        statsBar.add(mp4StatLabel);

        JPanel topContainer = new JPanel(new BorderLayout(0, 8));
        topContainer.setOpaque(false);
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(statsBar, BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 2. TABS CONTENT
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Tab 1: Access Log (Theo dõi GET, HEAD, POST thời gian thực)
        JPanel accessTab = new JPanel(new BorderLayout(8, 8));
        accessTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        accessTableModel = new DefaultTableModel(new String[]{
                "Thời Gian", "Phương Thức", "Đường Dẫn Tài Nguyên (URI)", "Mã Phản Hồi (Status)", "Địa Chỉ Client IP"
        }, 0);
        JTable accessTable = new JTable(accessTableModel);
        accessTable.setRowHeight(25);
        accessTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        accessTab.add(new JScrollPane(accessTable), BorderLayout.CENTER);

        JPanel accessBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearAccessBtn = new JButton("Xóa Danh Sách Truy Cập");
        clearAccessBtn.addActionListener(e -> accessTableModel.setRowCount(0));
        accessBtnBar.add(clearAccessBtn);
        accessTab.add(accessBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Nhật Ký Truy Cập HTTP (Access Log)  ", accessTab);

        // Tab 2: Dữ liệu nhận từ phương thức HTTP POST
        JPanel postTab = new JPanel(new BorderLayout(8, 8));
        postTab.setBorder(new EmptyBorder(8, 8, 8, 8));

        postTableModel = new DefaultTableModel(new String[]{
                "Thời Gian", "Họ Tên Sinh Viên", "Mã Số Sinh Viên (MSSV)", "Nội Dung Gửi Lên (Form Data)", "Địa Chỉ IP"
        }, 0);
        JTable postTable = new JTable(postTableModel);
        postTable.setRowHeight(26);
        postTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        postTab.add(new JScrollPane(postTable), BorderLayout.CENTER);
        tabbedPane.addTab("  Dữ Liệu Nhận Từ HTTP POST (Form Submissions)  ", postTab);

        // Tab 3: Quản lý Thư mục tài nguyên Web (web_root)
        JPanel resourceTab = new JPanel(new BorderLayout(8, 8));
        resourceTab.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTextArea resourceInfo = new JTextArea();
        resourceInfo.setEditable(false);
        resourceInfo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        resourceInfo.setText("HỆ THỐNG ĐA DỊCH VỤ TRÊN WEB SERVER:\n\n"
                + "1. Dịch vụ Trang Web Tĩnh: index.html (Trang chủ hiện đại giới thiệu dịch vụ)\n"
                + "2. Dịch vụ Tài Liệu PDF: /docs/TaiLieu_LapTrinhMang.pdf (Tài liệu chuẩn PDF-1.4)\n"
                + "3. Dịch vụ Phát Âm Thanh MP3: /assets/media/sample_audio.mp3 (Audio Stream)\n"
                + "4. Dịch vụ Phát Video MP4: /assets/media/sample_video.mp4 (Video Stream ISO BMFF)\n"
                + "5. Dịch vụ Xử Lý Form POST: /submit-feedback (Tiếp nhận dữ liệu sinh viên gửi lên)\n\n"
                + "Vị trí thư mục lưu trữ: " + new File(HttpConstants.WEB_ROOT).getAbsolutePath());
        resourceTab.add(new JScrollPane(resourceInfo), BorderLayout.CENTER);

        JPanel resBtnBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton openDirBtn = new JButton("Mở Thư Mục web_root Trên Máy Tính");
        openDirBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(HttpConstants.WEB_ROOT));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        resBtnBar.add(openDirBtn);
        resourceTab.add(resBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Tài Nguyên Đa Dịch Vụ (web_root)  ", resourceTab);

        // Tab 4: Console Logs
        JPanel logTab = new JPanel(new BorderLayout(8, 8));
        logTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(28, 30, 36));
        logArea.setForeground(new Color(225, 230, 240));
        JScrollPane logScroll = new JScrollPane(logArea);

        JPanel logBtnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearLogBtn = new JButton("Xóa Màn Hình Log");
        clearLogBtn.addActionListener(e -> logArea.setText(""));
        logBtnBar.add(clearLogBtn);

        logTab.add(logScroll, BorderLayout.CENTER);
        logTab.add(logBtnBar, BorderLayout.SOUTH);
        tabbedPane.addTab("  Console Logs  ", logTab);

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

    private void startServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Số cổng phải là số nguyên!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        server = new HttpServer(port);
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
            statusLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Trạng Thái Máy Chủ</span><br>"
                    + "<b style='font-size: 13px; color: #dc3545;'>Đã Dừng</b></div></html>");
        }
    }

    private void openUrlInBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Lỗi mở trình duyệt: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
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
    public void onAccessRecorded(HttpServer.AccessLog log) {
        SwingUtilities.invokeLater(() -> {
            accessTableModel.insertRow(0, new Object[]{
                    log.getFormattedTime(), log.method, log.uri, log.statusCode + " " + HttpConstants.getStatusText(log.statusCode), log.clientIp
            });
        });
    }

    @Override
    public void onStatsUpdated(int totalRequests, int pdfCount, int mp3Count, int mp4Count, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            totalReqLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Tổng Lượt Request</span><br>"
                    + "<b style='font-size: 13px; color: #007bff;'>" + totalRequests + " Lượt</b></div></html>");
            pdfStatLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Dịch Vụ PDF (.pdf)</span><br>"
                    + "<b style='font-size: 13px; color: #d93025;'>" + pdfCount + " Tải</b></div></html>");
            mp3StatLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Dịch Vụ Audio (.mp3)</span><br>"
                    + "<b style='font-size: 13px; color: #28a745;'>" + mp3Count + " Lượt</b></div></html>");
            mp4StatLabel.setText("<html><div style='text-align: center; padding: 6px;'>"
                    + "<span style='font-size: 10px; color: #555555;'>Dịch Vụ Video (.mp4)</span><br>"
                    + "<b style='font-size: 13px; color: #f2994a;'>" + mp4Count + " Lượt</b></div></html>");
        });
    }

    @Override
    public void onPostSubmitted(HttpServer.PostSubmission sub) {
        SwingUtilities.invokeLater(() -> {
            postTableModel.insertRow(0, new Object[]{
                    sub.getFormattedTime(), sub.name, sub.studentId, sub.content, sub.clientIp
            });
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WebServerGUI gui = new WebServerGUI();
            gui.setVisible(true);
            gui.startServer();
        });
    }
}

