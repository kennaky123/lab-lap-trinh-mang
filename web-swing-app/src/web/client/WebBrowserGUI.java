package web.client;

import web.common.HttpConstants;
import web.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.html.FormSubmitEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

/**
 * Giao diện Trình Duyệt Web (Web Browser) phong cách Chrome / Cốc Cốc bằng Java Swing:
 * - Hỗ trợ các phương thức HTTP: GET, HEAD, POST
 * - Hỗ trợ Lịch Sử duyệt web (History)
 * - Hỗ trợ Quản lý Tải xuống (Download Manager cho tệp .pdf, .mp3, .mp4...)
 * - Hỗ trợ Điều hướng: Back, Forward, Reload, Home
 */
public class WebBrowserGUI extends JFrame {

    private final HttpClientEngine engine = new HttpClientEngine();

    // History stack điều hướng
    private final Stack<String> backStack = new Stack<>();
    private final Stack<String> forwardStack = new Stack<>();
    private String currentUrl = "http://localhost:8080/";

    // UI Top Nav
    private JButton backBtn;
    private JButton fwdBtn;
    private JButton reloadBtn;
    private JButton homeBtn;
    private JTextField addressBar;
    private JButton goBtn;

    // Tabs
    private JTabbedPane mainTabbedPane;

    // Tab 1: Web View
    private JEditorPane webPane;
    private JLabel statusLabel;

    // Tab 2: HTTP Inspector (GET / HEAD / POST)
    private JComboBox<String> methodCombo;
    private JTextField inspectUrlField;
    private JTextArea postBodyArea;
    private JTextArea responseHeadersArea;
    private JTextArea responseBodyArea;
    private JLabel inspectorStatusLabel;

    // Tab 3: History
    private DefaultTableModel historyTableModel;
    private final File historyFile = new File(HttpConstants.DATA_DIR, "history.txt");

    // Tab 4: Downloads
    private DefaultTableModel downloadTableModel;
    private volatile boolean lastNavigationFailed = false;
    private volatile boolean isNavigating = false;

    public WebBrowserGUI() {
        setTitle("CHROME / COC COC BROWSER (JAVA SWING) - HTTP/1.1 CLIENT");
        setSize(1100, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        File dDir = new File(HttpConstants.DOWNLOADS_DIR);
        if (!dDir.exists()) dDir.mkdirs();
        File bDir = new File(HttpConstants.DATA_DIR);
        if (!bDir.exists()) bDir.mkdirs();

        initUI();
        loadHistoryFromDisk();
        navigateTo(currentUrl, false);
        startAutoReconnectTimer();
    }

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(new Color(242, 244, 248));

        // ==================== 1. CHROME TOP NAVIGATION BAR ====================
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(Color.WHITE);
        topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 225, 235)));

        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        navBar.setOpaque(false);

        backBtn = createNavBtn("<", "Quay lại (Back)");
        backBtn.setEnabled(false);
        backBtn.addActionListener(e -> goBack());

        fwdBtn = createNavBtn(">", "Đi tiếp (Forward)");
        fwdBtn.setEnabled(false);
        fwdBtn.addActionListener(e -> goForward());

        reloadBtn = createNavBtn("Tải Lại", "Tải lại trang (Reload)");
        reloadBtn.addActionListener(e -> navigateTo(currentUrl, false));

        homeBtn = createNavBtn("Trang Chủ", "Trang chủ (Home)");
        homeBtn.addActionListener(e -> navigateTo(getBaseOrigin() + "/", false));

        JButton lanBtn = createNavBtn("Tìm IP LAN", "Tự động tìm Web Server trong mạng Wi-Fi");
        lanBtn.addActionListener(e -> discoverWebServerInLan());

        navBar.add(backBtn);
        navBar.add(fwdBtn);
        navBar.add(reloadBtn);
        navBar.add(homeBtn);
        navBar.add(lanBtn);

        // Omnibox (Thanh địa chỉ)
        JPanel omniboxPanel = new JPanel(new BorderLayout(6, 0));
        omniboxPanel.setOpaque(false);
        omniboxPanel.setBorder(new EmptyBorder(8, 0, 8, 12));

        addressBar = new JTextField(currentUrl);
        addressBar.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        addressBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1),
                new EmptyBorder(6, 12, 6, 12)
        ));
        addressBar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateTo(addressBar.getText().trim(), false);
                }
            }
        });

        goBtn = new JButton("Đi Đến (Go)");
        goBtn.setBackground(new Color(26, 115, 232));
        goBtn.setForeground(Color.WHITE);
        goBtn.setContentAreaFilled(false);
        goBtn.setOpaque(true);
        goBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        goBtn.setFocusPainted(false);
        goBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        goBtn.addActionListener(e -> navigateTo(addressBar.getText().trim(), false));

        omniboxPanel.add(addressBar, BorderLayout.CENTER);
        omniboxPanel.add(goBtn, BorderLayout.EAST);

        topContainer.add(navBar, BorderLayout.WEST);
        topContainer.add(omniboxPanel, BorderLayout.CENTER);

        rootPanel.add(topContainer, BorderLayout.NORTH);

        // ==================== 2. MAIN TABS ====================
        mainTabbedPane = new JTabbedPane();
        mainTabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Tab 1: Web View HTML Render
        JPanel webViewPanel = createWebViewPanel();
        mainTabbedPane.addTab("  Trình Duyệt Web (HTML View)  ", webViewPanel);

        // Tab 2: HTTP Inspector (GET / HEAD / POST)
        JPanel inspectorPanel = createHttpInspectorPanel();
        mainTabbedPane.addTab("  Công Cụ HTTP (GET / HEAD / POST)  ", inspectorPanel);

        // Tab 3: History
        JPanel historyPanel = createHistoryPanel();
        mainTabbedPane.addTab("  Lịch Sử Duyệt Web (History)  ", historyPanel);

        // Tab 4: Downloads
        JPanel downloadPanel = createDownloadPanel();
        mainTabbedPane.addTab("  Quản Lý Tải Xuống (Downloads)  ", downloadPanel);

        rootPanel.add(mainTabbedPane, BorderLayout.CENTER);

        // ==================== 3. STATUS BAR ====================
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        statusBar.setBackground(Color.WHITE);
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(225, 230, 235)));

        statusLabel = new JLabel("Trạng thái: Sẵn sàng");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBar.add(statusLabel);

        rootPanel.add(statusBar, BorderLayout.SOUTH);

        add(rootPanel);
    }

    private JButton createNavBtn(String icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(245, 247, 250));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 225, 235), 1),
                new EmptyBorder(6, 10, 6, 10)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ==================== TAB 1: WEB VIEW ====================

    private JPanel createWebViewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        webPane = new JEditorPane();
        webPane.setEditable(false);
        webPane.setContentType("text/html");
        if (webPane.getEditorKit() instanceof javax.swing.text.html.HTMLEditorKit) {
            ((javax.swing.text.html.HTMLEditorKit) webPane.getEditorKit()).setAutoFormSubmission(false);
        }

        // Xử lý link click và form submit
        webPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                if (e instanceof FormSubmitEvent) {
                    // Xử lý form submit POST
                    FormSubmitEvent fse = (FormSubmitEvent) e;
                    URL formUrl = fse.getURL();
                    String postTarget = (formUrl != null) ? formUrl.toString() : getBaseOrigin() + "/submit-feedback";
                    if (postTarget.startsWith("/")) {
                        postTarget = getBaseOrigin() + postTarget;
                    }
                    if (FormSubmitEvent.MethodType.POST.equals(fse.getMethod())) {
                        handleFormPost(postTarget, fse.getData());
                        return;
                    }
                }

                URL targetUrl = e.getURL();
                String urlStr = (targetUrl != null) ? targetUrl.toString() : e.getDescription();
                if (urlStr != null) {
                    if (urlStr.startsWith("/")) {
                        urlStr = getBaseOrigin() + urlStr;
                    }
                    checkAndDownloadOrNavigate(urlStr);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(webPane);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Tự động nhận diện nếu link là tệp đa dịch vụ (.pdf, .mp3, .mp4) để chuyển sang tải về
     */
    private void checkAndDownloadOrNavigate(String urlStr) {
        String lower = urlStr.toLowerCase();
        if (lower.endsWith(".pdf") || lower.endsWith(".mp3") || lower.endsWith(".mp4") || lower.endsWith(".zip") || lower.endsWith(".rar")) {
            String fileName = urlStr.substring(urlStr.lastIndexOf('/') + 1);
            int opt = JOptionPane.showConfirmDialog(this,
                    "Bạn có muốn tải tệp tin này về máy qua dịch vụ HTTP không?\nTệp: " + fileName,
                    "Tải Tệp Tin Đa Dịch Vụ", JOptionPane.YES_NO_OPTION);
            if (opt == JOptionPane.YES_OPTION) {
                triggerDownload(urlStr, fileName);
            }
        } else {
            navigateTo(urlStr, false);
        }
    }

    private void handleFormPost(String targetUrl, String postData) {
        statusLabel.setText("Đang gửi yêu cầu HTTP POST: " + targetUrl);
        new Thread(() -> {
            try {
                HttpClientEngine.HttpResult res = engine.sendPost(targetUrl, postData, "application/x-www-form-urlencoded");
                SwingUtilities.invokeLater(() -> {
                    currentUrl = targetUrl;
                    addressBar.setText(currentUrl);
                    webPane.setText(res.body);
                    webPane.setCaretPosition(0);
                    recordHistory(currentUrl, "HTTP POST Response");
                    statusLabel.setText("HTTP/1.1 " + res.statusCode + " " + res.statusMessage);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Lỗi gửi POST: " + ex.getMessage());
                });
            }
        }).start();
    }

    // ==================== TAB 2: HTTP INSPECTOR (GET / HEAD / POST) ====================

    private JPanel createHttpInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setBackground(Color.WHITE);

        // Top Control bar
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);

        methodCombo = new JComboBox<>(new String[]{"GET", "HEAD", "POST"});
        methodCombo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        methodCombo.setPreferredSize(new Dimension(85, 34));

        inspectUrlField = new JTextField("http://localhost:8080/");
        inspectUrlField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JButton sendInspectBtn = new JButton("Gửi Request HTTP");
        sendInspectBtn.setBackground(new Color(26, 115, 232));
        sendInspectBtn.setForeground(Color.WHITE);
        sendInspectBtn.setContentAreaFilled(false);
        sendInspectBtn.setOpaque(true);
        sendInspectBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendInspectBtn.setFocusPainted(false);
        sendInspectBtn.addActionListener(e -> executeInspectorRequest());

        topBar.add(methodCombo, BorderLayout.WEST);
        topBar.add(inspectUrlField, BorderLayout.CENTER);
        topBar.add(sendInspectBtn, BorderLayout.EAST);

        // Body input for POST
        JPanel bodyInputPanel = new JPanel(new BorderLayout(4, 4));
        bodyInputPanel.setOpaque(false);
        bodyInputPanel.setBorder(new EmptyBorder(6, 0, 6, 0));
        JLabel bodyLabel = new JLabel("Dữ liệu Body (Dành cho phương thức HTTP POST, ví dụ: student_name=Alice&student_id=123):");
        bodyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        postBodyArea = new JTextArea(3, 40);
        postBodyArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        postBodyArea.setText("student_name=Nguyễn+Văn+A&student_id=20210001&content=Thử+nghiệm+HTTP+POST");
        JScrollPane bodyScroll = new JScrollPane(postBodyArea);
        bodyScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());

        bodyInputPanel.add(bodyLabel, BorderLayout.NORTH);
        bodyInputPanel.add(bodyScroll, BorderLayout.CENTER);

        JPanel topContainerWrap = new JPanel(new BorderLayout());
        topContainerWrap.setOpaque(false);
        topContainerWrap.add(topBar, BorderLayout.NORTH);
        topContainerWrap.add(bodyInputPanel, BorderLayout.CENTER);

        panel.add(topContainerWrap, BorderLayout.NORTH);

        // Response View (Headers + Body)
        JSplitPane splitResp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitResp.setResizeWeight(0.4);
        splitResp.setBorder(null);

        // Khung Headers
        JPanel headerPanel = new JPanel(new BorderLayout(0, 4));
        headerPanel.setOpaque(false);
        JLabel hLabel = new JLabel("HTTP Response Headers (Đặc biệt quan trọng với HEAD):");
        hLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        responseHeadersArea = new JTextArea();
        responseHeadersArea.setEditable(false);
        responseHeadersArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        responseHeadersArea.setBackground(new Color(248, 249, 251));
        JScrollPane hScroll = new JScrollPane(responseHeadersArea);
        hScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        headerPanel.add(hLabel, BorderLayout.NORTH);
        headerPanel.add(hScroll, BorderLayout.CENTER);
        splitResp.setLeftComponent(headerPanel);

        // Khung Body
        JPanel bodyPanel = new JPanel(new BorderLayout(0, 4));
        bodyPanel.setOpaque(false);
        JLabel bLabel = new JLabel("HTTP Response Body:");
        bLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        responseBodyArea = new JTextArea();
        responseBodyArea.setEditable(false);
        responseBodyArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane bScroll = new JScrollPane(responseBodyArea);
        bScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        bodyPanel.add(bLabel, BorderLayout.NORTH);
        bodyPanel.add(bScroll, BorderLayout.CENTER);
        splitResp.setRightComponent(bodyPanel);

        panel.add(splitResp, BorderLayout.CENTER);

        inspectorStatusLabel = new JLabel("Trạng thái: Sẵn sàng gửi request");
        inspectorStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        panel.add(inspectorStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void executeInspectorRequest() {
        String method = (String) methodCombo.getSelectedItem();
        String url = inspectUrlField.getText().trim();
        String postBody = postBodyArea.getText().trim();

        inspectorStatusLabel.setText("Đang gửi yêu cầu " + method + " tới " + url + "...");

        new Thread(() -> {
            try {
                HttpClientEngine.HttpResult res;
                if ("HEAD".equalsIgnoreCase(method)) {
                    res = engine.sendHead(url);
                } else if ("POST".equalsIgnoreCase(method)) {
                    res = engine.sendPost(url, postBody, "application/x-www-form-urlencoded");
                } else {
                    res = engine.sendGet(url);
                }

                final HttpClientEngine.HttpResult finalRes = res;
                SwingUtilities.invokeLater(() -> {
                    responseHeadersArea.setText(finalRes.getHeaderString());
                    responseBodyArea.setText(finalRes.body);
                    inspectorStatusLabel.setText("Phản hồi: " + finalRes.statusCode + " " + finalRes.statusMessage);
                    recordHistory(url, method + " Request (" + finalRes.statusCode + ")");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    inspectorStatusLabel.setText("Lỗi: " + ex.getMessage());
                    responseHeadersArea.setText("Lỗi: " + ex.getMessage());
                    responseBodyArea.setText("");
                });
            }
        }).start();
    }

    // ==================== TAB 3: HISTORY ====================

    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setBackground(Color.WHITE);

        historyTableModel = new DefaultTableModel(new String[]{"Thời Gian", "Tiêu Đề / Ghi Chú", "Địa Chỉ URL"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(25);
        historyTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(historyTable);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnBar.setOpaque(false);

        JButton openSelectedBtn = new JButton("🌐 Mở Lại Trang Đã Chọn");
        openSelectedBtn.addActionListener(e -> {
            int r = historyTable.getSelectedRow();
            if (r != -1) {
                String u = (String) historyTableModel.getValueAt(r, 2);
                mainTabbedPane.setSelectedIndex(0);
                navigateTo(u, false);
            }
        });

        JButton clearHistoryBtn = new JButton("Xóa Toàn Bộ Lịch Sử");
        clearHistoryBtn.addActionListener(e -> {
            historyTableModel.setRowCount(0);
            if (historyFile.exists()) historyFile.delete();
        });

        btnBar.add(openSelectedBtn);
        btnBar.add(clearHistoryBtn);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void recordHistory(String url, String title) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String time = sdf.format(new Date());
        historyTableModel.insertRow(0, new Object[]{time, title, url});

        try (FileWriter fw = new FileWriter(historyFile, true)) {
            fw.write(time + "|" + title + "|" + url + "\n");
        } catch (Exception ignored) {}
    }

    private void loadHistoryFromDisk() {
        if (!historyFile.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(historyFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length >= 3) {
                    historyTableModel.insertRow(0, new Object[]{p[0], p[1], p[2]});
                }
            }
        } catch (Exception ignored) {}
    }

    // ==================== TAB 4: DOWNLOADS ====================

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));
        panel.setBackground(Color.WHITE);

        downloadTableModel = new DefaultTableModel(new String[]{
                "Tên Tệp Tin", "Dịch Vụ", "Tiến Độ", "Trạng Thái", "Đường Dẫn Lưu Trữ"
        }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable downloadTable = new JTable(downloadTableModel);
        downloadTable.setRowHeight(26);
        downloadTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        downloadTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(downloadTable);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btnBar.setOpaque(false);

        JButton openFileBtn = new JButton("▶ Mở Tệp Đã Tải");
        openFileBtn.addActionListener(e -> {
            int r = downloadTable.getSelectedRow();
            if (r != -1) {
                String path = (String) downloadTableModel.getValueAt(r, 4);
                try {
                    Desktop.getDesktop().open(new File(path));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Không thể mở tệp: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton openFolderBtn = new JButton("📁 Mở Thư Mục Downloads");
        openFolderBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File(HttpConstants.DOWNLOADS_DIR));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnBar.add(openFileBtn);
        btnBar.add(openFolderBtn);
        panel.add(btnBar, BorderLayout.SOUTH);

        return panel;
    }

    private void triggerDownload(String urlStr, String fileName) {
        File dest = new File(HttpConstants.DOWNLOADS_DIR, fileName);
        String serviceType = "Tài Liệu";
        if (fileName.endsWith(".mp3")) serviceType = "Âm Thanh (.mp3)";
        else if (fileName.endsWith(".mp4")) serviceType = "Video (.mp4)";
        else if (fileName.endsWith(".pdf")) serviceType = "Tài Liệu PDF (.pdf)";

        int rowIdx = downloadTableModel.getRowCount();
        downloadTableModel.addRow(new Object[]{fileName, serviceType, "0%", "Đang tải...", dest.getAbsolutePath()});
        mainTabbedPane.setSelectedIndex(3); // Chuyển sang Tab Downloads

        statusLabel.setText("Đang tải xuống: " + fileName);

        new Thread(() -> {
            boolean ok = engine.downloadFile(urlStr, dest, (transferred, total) -> {
                if (total > 0) {
                    int percent = (int) ((transferred * 100) / total);
                    SwingUtilities.invokeLater(() -> {
                        downloadTableModel.setValueAt(percent + "%", rowIdx, 2);
                    });
                }
            });

            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    downloadTableModel.setValueAt("100%", rowIdx, 2);
                    downloadTableModel.setValueAt("Hoàn Tất", rowIdx, 3);
                    statusLabel.setText("Tải về hoàn tất: " + fileName);
                    JOptionPane.showMessageDialog(this, "Đã tải xong tệp: " + fileName + "\nLưu tại: " + dest.getAbsolutePath(), "Tải Xuống Thành Công", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    downloadTableModel.setValueAt("Thất Bại", rowIdx, 3);
                    statusLabel.setText("Tải về thất bại: " + fileName);
                }
            });
        }).start();
    }

    // ==================== ĐIỀU HƯỚNG BROWSER ====================

    private void navigateTo(String urlStr, boolean isNavigatingHistory) {
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
        }

        final String targetUrl = urlStr;
        isNavigating = true;
        statusLabel.setText("Đang tải trang: " + targetUrl);

        new Thread(() -> {
            try {
                HttpClientEngine.HttpResult res = engine.sendGet(targetUrl);

                SwingUtilities.invokeLater(() -> {
                    isNavigating = false;
                    lastNavigationFailed = false;
                    if (!isNavigatingHistory && !currentUrl.equalsIgnoreCase(targetUrl)) {
                        backStack.push(currentUrl);
                        forwardStack.clear();
                        updateNavButtons();
                    }

                    currentUrl = targetUrl;
                    addressBar.setText(currentUrl);
                    inspectUrlField.setText(currentUrl);

                    try {
                        if (webPane.getDocument() instanceof HTMLDocument) {
                            ((HTMLDocument) webPane.getDocument()).setBase(new URL(currentUrl));
                        }
                    } catch (Exception ignored) {}
                    webPane.setText(res.body);
                    webPane.setCaretPosition(0);

                    recordHistory(currentUrl, "Trang Web HTTP (200 OK)");
                    statusLabel.setText("Hoàn tất: HTTP/1.1 " + res.statusCode + " " + res.statusMessage);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    isNavigating = false;
                    lastNavigationFailed = true;
                    statusLabel.setText("Đang chờ kết nối tới Web Server (" + targetUrl + ")...");
                    webPane.setText("<html><body style='font-family: Segoe UI, Arial; text-align: center; padding: 40px;'>"
                            + "<h2 style='color: #d93025;'>Đang Chờ Kết Nối Tới Web Server...</h2>"
                            + "<p>Chưa kết nối được tới máy chủ tại <b>" + targetUrl + "</b>.</p>"
                            + "<p>Ngay khi bạn bật <b>WebServerGUI</b> (Port 8080), trình duyệt sẽ <b>tự động kết nối và tải trang</b>!</p>"
                            + "</body></html>");
                });
            }
        }).start();
    }

    private void startAutoReconnectTimer() {
        Timer timer = new Timer(1500, e -> {
            if (lastNavigationFailed && !isNavigating) {
                try {
                    URL u = new URL(currentUrl);
                    int port = u.getPort() != -1 ? u.getPort() : 8080;
                    String host = u.getHost() != null && !u.getHost().isEmpty() ? u.getHost() : "localhost";
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(host, port), 400);
                        navigateTo(currentUrl, true);
                    }
                } catch (Exception ignored) {}
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    private String getBaseOrigin() {
        try {
            URL u = new URL(currentUrl);
            int p = u.getPort() != -1 ? u.getPort() : 8080;
            return u.getProtocol() + "://" + u.getHost() + ":" + p;
        } catch (Exception e) {
            return "http://localhost:8080";
        }
    }

    private void discoverWebServerInLan() {
        statusLabel.setText("Đang dò tìm Web Server trong mạng Wi-Fi (LAN)...");
        new Thread(() -> {
            String foundIp = null;
            int foundPort = 8080;
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(2000);
                byte[] req = "DISCOVER_WEB_SERVER_REQUEST".getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(
                        req, req.length,
                        InetAddress.getByName("255.255.255.255"),
                        8081
                );
                socket.send(packet);

                byte[] buf = new byte[256];
                DatagramPacket resp = new DatagramPacket(buf, buf.length);
                socket.receive(resp);
                String msg = new String(resp.getData(), 0, resp.getLength(), StandardCharsets.UTF_8).trim();
                if (msg.startsWith("WEB_SERVER_DISCOVERED")) {
                    foundIp = resp.getAddress().getHostAddress();
                    String[] parts = msg.split(":");
                    if (parts.length >= 2) {
                        try {
                            foundPort = Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            final String ip = foundIp;
            final int port = foundPort;
            SwingUtilities.invokeLater(() -> {
                if (ip != null) {
                    String lanUrl = "http://" + ip + ":" + port + "/";
                    navigateTo(lanUrl, false);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Không tìm thấy Web Server tự động qua UDP.\nHãy nhập trực tiếp http://<IP_MÁY_1>:8080/ vào thanh địa chỉ.",
                            "Thông Báo", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "Web-LAN-Discovery").start();
    }

    private void goBack() {
        if (!backStack.isEmpty()) {
            forwardStack.push(currentUrl);
            String prev = backStack.pop();
            updateNavButtons();
            navigateTo(prev, true);
        }
    }

    private void goForward() {
        if (!forwardStack.isEmpty()) {
            backStack.push(currentUrl);
            String next = forwardStack.pop();
            updateNavButtons();
            navigateTo(next, true);
        }
    }

    private void updateNavButtons() {
        backBtn.setEnabled(!backStack.isEmpty());
        fwdBtn.setEnabled(!forwardStack.isEmpty());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WebBrowserGUI gui = new WebBrowserGUI();
            gui.setVisible(true);
        });
    }
}

