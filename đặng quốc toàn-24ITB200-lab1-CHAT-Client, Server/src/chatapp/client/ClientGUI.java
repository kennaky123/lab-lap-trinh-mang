package chatapp.client;

import chatapp.common.Config;
import chatapp.common.Message;
import chatapp.common.MessageType;
import chatapp.ui.ChatBubblePanel;
import chatapp.ui.EmojiPickerPopup;
import chatapp.ui.ModernScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Giao diện Client kiểu Zalo / Messenger, chia thành 3 trang (page):
 *  1. TIN NHẮN : Kênh chung (Broadcast / UDP Multicast / phòng) + Kênh bạn bè (chat 1-1)
 *  2. TÌM BẠN  : Nhập tên tài khoản -> tìm thấy -> nút "Gửi lời mời kết bạn"
 *  3. LỜI MỜI  : Lời mời đã nhận (Chấp nhận / Từ chối) và lời mời đã gửi (Thu hồi)
 * Chỉ những người đã là bạn bè mới có thể nhắn tin riêng với nhau.
 */
public class ClientGUI extends JFrame implements ChatClient.ClientListener, UdpMulticastService.UdpMessageListener {

    public static class UserListItem {
        public final String username;
        public final boolean isOnline;

        public UserListItem(String username, boolean isOnline) {
            this.username = username;
            this.isOnline = isOnline;
        }

        @Override
        public String toString() {
            return username;
        }
    }

    private static final Color PRIMARY = new Color(0, 132, 255);
    private static final Color NAV_BG = new Color(0, 104, 214);
    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color RED = new Color(220, 53, 69);
    private static final Color BORDER = new Color(225, 230, 236);
    private static final Color PAGE_BG = new Color(244, 246, 249);

    private static final String PAGE_CHAT = "CHAT";
    private static final String PAGE_SEARCH = "SEARCH";
    private static final String PAGE_REQUESTS = "REQUESTS";

    private final String currentUsername;
    private final String serverHost;
    private final int serverPort;

    private ChatClient client;
    private UdpMulticastService udpService;
    private final AudioRecorder audioRecorder = new AudioRecorder();

    // Context trò chuyện hiện tại: "ALL", "UDP_MULTICAST", "ROOM:<tên>", hoặc "USER:<tên>"
    private String currentChatTarget = "ALL";

    // Dữ liệu bạn bè
    private final Set<String> myFriends = new LinkedHashSet<>();
    private final List<String> pendingIncoming = new ArrayList<>();
    private final List<String> pendingSent = new ArrayList<>();
    private final Map<String, Boolean> onlineStatus = new LinkedHashMap<>();

    // Lưu trữ lịch sử tin nhắn cục bộ theo từng mục trò chuyện
    private final Map<String, List<Message>> conversationMap = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private final long sessionStartTime = System.currentTimeMillis();
    private String currentActivePage = PAGE_CHAT;

    // Thanh thông báo (Toast Banner) khi có tin nhắn / lời mời mới
    private JPanel notificationBannerPanel;
    private JLabel notificationBannerLabel;
    private JButton notificationActionBtn;
    private Runnable currentNotificationAction;
    private javax.swing.Timer notificationHideTimer;

    // Điều hướng trang
    private CardLayout pageLayout;
    private JPanel pageContainer;
    private JButton navChatBtn, navSearchBtn, navRequestsBtn;

    // Trang Tin Nhắn
    private JLabel currentChatTitleLabel;
    private JLabel currentChatSubtitleLabel;
    private JPanel chatHistoryPanel;
    private JScrollPane chatScrollPane;
    private JTextField messageInputField;
    private JButton voiceRecordButton;
    private JButton emojiButton;
    private DefaultListModel<String> broadcastRoomModel;
    private JList<String> broadcastRoomList;
    private DefaultListModel<UserListItem> friendsModel;
    private JList<UserListItem> friendsList;
    private JLabel friendsEmptyHint;

    // Trang Tìm Bạn
    private JTextField searchField;
    private JLabel searchStatusLabel;
    private JPanel searchResultPanel;
    private String lastSearchFound;
    private boolean lastSearchOnline;

    // Trang Lời Mời
    private JPanel incomingListPanel;
    private JPanel sentListPanel;

    public ClientGUI(String host, int port, String username) {
        this.serverHost = host;
        this.serverPort = port;
        this.currentUsername = username;

        setTitle("Zalo / Messenger Chat - [" + username + "]");
        setSize(1020, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initUI();
        connectToServer();
    }

    // =====================================================================
    //                              KHUNG CHÍNH
    // =====================================================================

    private void initUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildNavBar(), BorderLayout.WEST);

        pageLayout = new CardLayout();
        pageContainer = new JPanel(pageLayout);
        pageContainer.add(buildChatPage(), PAGE_CHAT);
        pageContainer.add(buildSearchPage(), PAGE_SEARCH);
        pageContainer.add(buildRequestsPage(), PAGE_REQUESTS);

        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(buildNotificationBanner(), BorderLayout.NORTH);
        rightContainer.add(pageContainer, BorderLayout.CENTER);
        root.add(rightContainer, BorderLayout.CENTER);

        add(root);
        showPage(PAGE_CHAT);
    }

    /** Thanh thông báo nổi phía trên cùng khi có người gửi tin nhắn hoặc lời mời kết bạn */
    private JPanel buildNotificationBanner() {
        notificationBannerPanel = new JPanel(new BorderLayout(10, 0));
        notificationBannerPanel.setBackground(new Color(255, 248, 225));
        notificationBannerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 1, 0, new Color(255, 152, 0)),
                new EmptyBorder(8, 14, 8, 14)
        ));
        notificationBannerPanel.setVisible(false);

        notificationBannerLabel = new JLabel("");
        notificationBannerLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        notificationBannerLabel.setForeground(new Color(35, 40, 50));

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);

        notificationActionBtn = createActionButton("Mở Xem Ngay", PRIMARY);
        notificationActionBtn.addActionListener(e -> {
            notificationBannerPanel.setVisible(false);
            if (currentNotificationAction != null) {
                currentNotificationAction.run();
            }
        });

        JButton closeBtn = createActionButton("Đóng", new Color(120, 125, 135));
        closeBtn.addActionListener(e -> notificationBannerPanel.setVisible(false));

        btnGroup.add(notificationActionBtn);
        btnGroup.add(closeBtn);

        notificationBannerPanel.add(notificationBannerLabel, BorderLayout.CENTER);
        notificationBannerPanel.add(btnGroup, BorderLayout.EAST);

        notificationHideTimer = new javax.swing.Timer(6000, e -> notificationBannerPanel.setVisible(false));
        notificationHideTimer.setRepeats(false);

        return notificationBannerPanel;
    }

    private void showToastNotification(String text, String actionText, Runnable onAction) {
        notificationBannerLabel.setText(text);
        notificationActionBtn.setText(actionText);
        currentNotificationAction = onAction;
        notificationBannerPanel.setVisible(true);
        notificationBannerPanel.revalidate();
        notificationBannerPanel.repaint();
        notificationHideTimer.restart();
        Toolkit.getDefaultToolkit().beep();
    }

    /** Thanh điều hướng dọc bên trái (giống Zalo) */
    private JPanel buildNavBar() {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(NAV_BG);
        nav.setPreferredSize(new Dimension(96, 0));
        nav.setBorder(new EmptyBorder(16, 8, 16, 8));

        JLabel avatar = createAvatar(currentUsername, 48, Color.WHITE, NAV_BG);
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);
        nav.add(avatar);
        nav.add(Box.createVerticalStrut(6));

        JLabel name = new JLabel(currentUsername, SwingConstants.CENTER);
        name.setFont(new Font("Segoe UI", Font.BOLD, 11));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        nav.add(name);
        nav.add(Box.createVerticalStrut(22));

        navChatBtn = createNavButton("Tin Nhắn", PAGE_CHAT);
        navSearchBtn = createNavButton("Tìm Bạn", PAGE_SEARCH);
        navRequestsBtn = createNavButton("Lời Mời", PAGE_REQUESTS);

        nav.add(navChatBtn);
        nav.add(Box.createVerticalStrut(8));
        nav.add(navSearchBtn);
        nav.add(Box.createVerticalStrut(8));
        nav.add(navRequestsBtn);
        nav.add(Box.createVerticalGlue());
        return nav;
    }

    private JButton createNavButton(String text, String page) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(NAV_BG);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(80, 44));
        b.setPreferredSize(new Dimension(80, 44));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> showPage(page));
        return b;
    }

    private void showPage(String page) {
        this.currentActivePage = page;
        pageLayout.show(pageContainer, page);
        Color active = new Color(0, 72, 160);
        navChatBtn.setBackground(PAGE_CHAT.equals(page) ? active : NAV_BG);
        navSearchBtn.setBackground(PAGE_SEARCH.equals(page) ? active : NAV_BG);
        navRequestsBtn.setBackground(PAGE_REQUESTS.equals(page) ? active : NAV_BG);
        if (PAGE_CHAT.equals(page)) {
            unreadCounts.remove(currentChatTarget);
            updateUnreadBadges();
        } else if (PAGE_SEARCH.equals(page)) {
            searchField.requestFocusInWindow();
        }
    }

    private void updateRequestBadge() {
        int n = pendingIncoming.size();
        navRequestsBtn.setText(n > 0 ? "<html><center>Lời Mời<br><font color='#ffdd57'>(" + n + " mới)</font></center></html>" : "Lời Mời");
    }

    private void updateUnreadBadges() {
        int totalUnread = 0;
        for (int c : unreadCounts.values()) {
            totalUnread += c;
        }
        if (navChatBtn != null) {
            navChatBtn.setText(totalUnread > 0
                    ? "<html><center>Tin Nhắn<br><font color='#ffdd57'>(" + totalUnread + " mới)</font></center></html>"
                    : "Tin Nhắn");
        }
        if (broadcastRoomList != null) broadcastRoomList.repaint();
        if (friendsList != null) friendsList.repaint();
    }

    // =====================================================================
    //                           TRANG 1: TIN NHẮN
    // =====================================================================

    private JPanel buildChatPage() {
        JPanel page = new JPanel(new BorderLayout());

        // ---------- Sidebar danh sách kênh ----------
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);

        // Kênh chung
        broadcastRoomModel = new DefaultListModel<>();
        broadcastRoomModel.addElement("Kênh Chung (Broadcast)");
        broadcastRoomModel.addElement("Kênh UDP Multicast (230.0.0.1)");
        broadcastRoomList = new JList<>(broadcastRoomModel);
        broadcastRoomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        broadcastRoomList.setSelectedIndex(0);
        broadcastRoomList.setFixedCellHeight(34);
        broadcastRoomList.setCellRenderer((list, value, index, isSelected, focus) -> {
            String key = value.contains("Broadcast") ? "ALL"
                    : value.contains("UDP Multicast") ? "UDP_MULTICAST"
                    : "ROOM:" + value.replace("# ", "").trim();
            int unread = unreadCounts.getOrDefault(key, 0);
            String labelText = unread > 0
                    ? "<html>" + value + " <b style='color:#dc3545;'>(" + unread + " mới)</b></html>"
                    : value;
            return createListCell(labelText, isSelected, new Color(50, 55, 65));
        });
        broadcastRoomList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || broadcastRoomList.getSelectedIndex() == -1) return;
            friendsList.clearSelection();
            String selected = broadcastRoomList.getSelectedValue();
            if (selected.contains("Broadcast")) {
                switchChatContext("ALL", "Kênh Chung (Broadcast Toàn Bộ)", "Tin nhắn gửi tới tất cả người dùng trong hệ thống");
            } else if (selected.contains("UDP Multicast")) {
                switchChatContext("UDP_MULTICAST", "Kênh UDP Multicast (230.0.0.1:4446)", "Socket Datagram UDP Multicast độc lập");
            } else {
                String room = selected.replace("# ", "").trim();
                switchChatContext("ROOM:" + room, "Phòng #" + room, "Chỉ thành viên trong phòng này mới nhận được tin");
            }
        });

        content.add(createSectionHeader("KÊNH CHUNG"));
        content.add(broadcastRoomList);
        content.add(Box.createVerticalStrut(8));

        // Kênh bạn bè
        friendsModel = new DefaultListModel<>();
        friendsList = new JList<>(friendsModel);
        friendsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendsList.setFixedCellHeight(38);
        friendsList.setCellRenderer((list, value, index, isSelected, focus) -> {
            int unread = unreadCounts.getOrDefault("USER:" + value.username, 0);
            String prefix = value.isOnline ? "●  " : "○  ";
            String text = unread > 0
                    ? "<html>" + prefix + "<b>" + value.username + "</b> <span style='color:#dc3545;font-weight:bold;'>[" + unread + " tin mới]</span></html>"
                    : prefix + value.username;
            JLabel c = createListCell(text, isSelected, value.isOnline ? GREEN : new Color(130, 135, 145));
            c.setToolTipText(value.isOnline ? "Đang trực tuyến" : "Ngoại tuyến");
            return c;
        });
        friendsList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || friendsList.getSelectedIndex() == -1) return;
            broadcastRoomList.clearSelection();
            UserListItem item = friendsList.getSelectedValue();
            String status = item.isOnline ? "Đang trực tuyến" : "Ngoại tuyến - tin nhắn sẽ được lưu vào CSDL";
            switchChatContext("USER:" + item.username, item.username, "Bạn bè · " + status);
        });

        friendsEmptyHint = new JLabel("<html><div style='padding:6px 14px;color:#888;'>Chưa có bạn bè.<br>Vào trang <b>Tìm Bạn</b> để kết bạn.</div></html>");
        friendsEmptyHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        content.add(createSectionHeader("BẠN BÈ"));
        content.add(friendsList);
        content.add(friendsEmptyHint);

        JPanel contentWrap = new JPanel(new BorderLayout());
        contentWrap.setBackground(Color.WHITE);
        contentWrap.add(content, BorderLayout.NORTH);
        JScrollPane sideScroll = new JScrollPane(contentWrap);
        sideScroll.setBorder(null);
        sideScroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sidebar.add(sideScroll, BorderLayout.CENTER);

        JButton addRoomButton = new JButton("+ Tạo / Tham Gia Phòng");
        addRoomButton.setFocusPainted(false);
        addRoomButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addRoomButton.setBackground(new Color(245, 247, 250));
        addRoomButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(10, 0, 10, 0)));
        addRoomButton.addActionListener(e -> promptJoinCustomRoom());
        sidebar.add(addRoomButton, BorderLayout.SOUTH);

        page.add(sidebar, BorderLayout.WEST);

        // ---------- Khu vực chat ----------
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(Color.WHITE);

        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(10, 18, 10, 18)));
        currentChatTitleLabel = new JLabel("Kênh Chung (Broadcast Toàn Bộ)");
        currentChatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        currentChatSubtitleLabel = new JLabel("Tin nhắn gửi tới tất cả người dùng trong hệ thống");
        currentChatSubtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        currentChatSubtitleLabel.setForeground(new Color(120, 125, 135));
        header.add(currentChatTitleLabel);
        header.add(currentChatSubtitleLabel);
        chatPanel.add(header, BorderLayout.NORTH);

        chatHistoryPanel = new JPanel();
        chatHistoryPanel.setLayout(new BoxLayout(chatHistoryPanel, BoxLayout.Y_AXIS));
        chatHistoryPanel.setBackground(new Color(248, 249, 252));
        chatScrollPane = new JScrollPane(chatHistoryPanel);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);

        // Thanh công cụ + ô nhập
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        tools.setOpaque(false);
        emojiButton = createToolButton("Icon Emoji", "Mở bảng chọn biểu cảm Emoji");
        emojiButton.addActionListener(e -> showEmojiPicker());
        JButton imgBtn = createToolButton("Gửi Hình Ảnh", "Gửi hình ảnh (xem trước trực tiếp)");
        imgBtn.addActionListener(e -> chooseAndSend(MessageType.IMAGE_MESSAGE));
        JButton vidBtn = createToolButton("Gửi Video", "Gửi tệp video");
        vidBtn.addActionListener(e -> chooseAndSend(MessageType.VIDEO_MESSAGE));
        JButton fileBtn = createToolButton("Đính Kèm File", "Gửi tệp đính kèm");
        fileBtn.addActionListener(e -> chooseAndSend(MessageType.FILE_TRANSFER));
        voiceRecordButton = createToolButton("Ghi Âm Voice", "Bấm để bắt đầu ghi âm giọng nói");
        voiceRecordButton.addActionListener(e -> toggleVoiceRecording());
        tools.add(emojiButton);
        tools.add(imgBtn);
        tools.add(vidBtn);
        tools.add(fileBtn);
        tools.add(voiceRecordButton);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(false);
        inputBar.setBorder(new EmptyBorder(0, 12, 10, 12));
        messageInputField = new JTextField();
        messageInputField.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        messageInputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 215, 225), 1), new EmptyBorder(8, 12, 8, 12)));
        messageInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendMessage();
            }
        });
        JButton sendButton = createActionButton("Gửi Tin", PRIMARY);
        sendButton.setPreferredSize(new Dimension(90, 36));
        sendButton.addActionListener(e -> sendMessage());
        inputBar.add(messageInputField, BorderLayout.CENTER);
        inputBar.add(sendButton, BorderLayout.EAST);

        bottom.add(tools, BorderLayout.NORTH);
        bottom.add(inputBar, BorderLayout.SOUTH);
        chatPanel.add(bottom, BorderLayout.SOUTH);

        page.add(chatPanel, BorderLayout.CENTER);
        return page;
    }

    // =====================================================================
    //                           TRANG 2: TÌM BẠN
    // =====================================================================

    private JPanel buildSearchPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(PAGE_BG);
        page.add(createPageHeader("Tìm Kiếm & Kết Bạn",
                "Danh sách tất cả tài khoản trên hệ thống — Gõ bất kỳ ký tự nào để lọc nhanh tài khoản"), BorderLayout.NORTH);

        JPanel topControls = new JPanel();
        topControls.setLayout(new BoxLayout(topControls, BoxLayout.Y_AXIS));
        topControls.setOpaque(false);
        topControls.setBorder(new EmptyBorder(18, 40, 10, 40));

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField = new JTextField();
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        searchField.setToolTipText("Gõ 1 hoặc nhiều chữ cái để lọc danh sách tài khoản theo thời gian thực");
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 215), 1), new EmptyBorder(8, 12, 8, 12)));
        searchField.addActionListener(e -> doSearch());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                lastSearchFound = null;
                renderSearchResult();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                lastSearchFound = null;
                renderSearchResult();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                lastSearchFound = null;
                renderSearchResult();
            }
        });

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnGroup.setOpaque(false);

        JButton searchBtn = createActionButton("Tìm Kiếm", PRIMARY);
        searchBtn.setPreferredSize(new Dimension(105, 40));
        searchBtn.addActionListener(e -> doSearch());

        JButton clearBtn = createActionButton("Hiện Tất Cả", new Color(108, 117, 125));
        clearBtn.setPreferredSize(new Dimension(105, 40));
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            lastSearchFound = null;
            renderSearchResult();
        });

        btnGroup.add(searchBtn);
        btnGroup.add(clearBtn);

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(btnGroup, BorderLayout.EAST);

        searchStatusLabel = new JLabel("Đang tải danh sách tài khoản...");
        searchStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchStatusLabel.setForeground(new Color(100, 105, 115));
        searchStatusLabel.setBorder(new EmptyBorder(10, 2, 2, 0));
        searchStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        topControls.add(searchRow);
        topControls.add(searchStatusLabel);

        searchResultPanel = new JPanel();
        searchResultPanel.setLayout(new BoxLayout(searchResultPanel, BoxLayout.Y_AXIS));
        searchResultPanel.setOpaque(false);
        searchResultPanel.setBorder(new EmptyBorder(4, 40, 20, 40));

        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(searchResultPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(listWrap);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.add(topControls, BorderLayout.NORTH);
        centerWrap.add(scroll, BorderLayout.CENTER);

        page.add(centerWrap, BorderLayout.CENTER);
        renderSearchResult();
        return page;
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        renderSearchResult();
        if (!q.isEmpty() && client != null) {
            client.searchUser(q);
        }
    }

    /** Vẽ danh sách tất cả tài khoản (hoặc lọc theo từ khóa gõ vào ô tìm kiếm) */
    private void renderSearchResult() {
        if (searchResultPanel == null) return;
        searchResultPanel.removeAll();

        String keyword = searchField != null ? searchField.getText().trim().toLowerCase() : "";

        // Tập hợp tất cả tài khoản đã biết trên hệ thống (trừ bản thân mình đặt ở cuối hoặc hiển thị rõ)
        Set<String> allKnownUsers = new LinkedHashSet<>(onlineStatus.keySet());
        allKnownUsers.addAll(myFriends);
        allKnownUsers.addAll(pendingIncoming);
        allKnownUsers.addAll(pendingSent);
        if (lastSearchFound != null && !lastSearchFound.isEmpty()) {
            allKnownUsers.add(lastSearchFound);
        }

        List<String> matchedUsers = new ArrayList<>();
        int totalOtherUsers = 0;
        for (String u : allKnownUsers) {
            if (u == null || u.isEmpty()) continue;
            if (u.equalsIgnoreCase(currentUsername)) continue;
            totalOtherUsers++;
            if (keyword.isEmpty() || u.toLowerCase().contains(keyword)) {
                matchedUsers.add(u);
            }
        }

        if (keyword.isEmpty()) {
            searchStatusLabel.setText("TẤT CẢ TÀI KHOẢN TRÊN HỆ THỐNG (" + matchedUsers.size() + " người dùng)");
        } else {
            searchStatusLabel.setText("KẾT QUẢ LỌC CHO TỪ KHÓA \"" + searchField.getText().trim() + "\": tìm thấy "
                    + matchedUsers.size() + " / " + totalOtherUsers + " tài khoản");
        }

        if (matchedUsers.isEmpty()) {
            String emptyMsg = keyword.isEmpty()
                    ? "Chưa có tài khoản người dùng nào khác trên hệ thống."
                    : "Không có tài khoản nào chứa ký tự \"" + searchField.getText().trim() + "\".";
            searchResultPanel.add(createEmptyLabel(emptyMsg));
        } else {
            for (String name : matchedUsers) {
                boolean online = onlineStatus.getOrDefault(name,
                        name.equalsIgnoreCase(lastSearchFound) && lastSearchOnline);
                JPanel actions = buildUserActionButtons(name);
                searchResultPanel.add(createUserCard(name, online ? "● Đang trực tuyến" : "○ Ngoại tuyến", online, actions));
                searchResultPanel.add(Box.createVerticalStrut(8));
            }
        }

        searchResultPanel.revalidate();
        searchResultPanel.repaint();
    }

    private JPanel buildUserActionButtons(String name) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        if (name.equalsIgnoreCase(currentUsername)) {
            actions.add(createInfoLabel("Đây là tài khoản của bạn"));
        } else if (myFriends.contains(name)) {
            JButton chat = createActionButton("Nhắn Tin", PRIMARY);
            chat.addActionListener(e -> openFriendChat(name));
            actions.add(createInfoLabel("Đã là bạn bè"));
            actions.add(chat);
        } else if (pendingIncoming.contains(name)) {
            JButton accept = createActionButton("Chấp Nhận", GREEN);
            accept.addActionListener(e -> client.acceptFriendRequest(name));
            JButton decline = createActionButton("Từ Chối", RED);
            decline.addActionListener(e -> client.declineFriendRequest(name));
            actions.add(accept);
            actions.add(decline);
        } else if (pendingSent.contains(name)) {
            actions.add(createInfoLabel("Đã gửi lời mời - đang chờ"));
            JButton cancel = createActionButton("Thu Hồi", new Color(108, 117, 125));
            cancel.addActionListener(e -> client.declineFriendRequest(name));
            actions.add(cancel);
        } else {
            JButton add = createActionButton("+ Gửi Lời Mời Kết Bạn", PRIMARY);
            add.addActionListener(e -> {
                add.setEnabled(false);
                add.setText("Đang gửi...");
                client.sendFriendRequest(name);
            });
            actions.add(add);
        }
        return actions;
    }

    // =====================================================================
    //                           TRANG 3: LỜI MỜI
    // =====================================================================

    private JPanel buildRequestsPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(PAGE_BG);
        page.add(createPageHeader("Lời Mời Kết Bạn", "Chấp nhận hoặc từ chối lời mời, xem các lời mời bạn đã gửi"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 40, 16, 40));

        incomingListPanel = new JPanel();
        incomingListPanel.setLayout(new BoxLayout(incomingListPanel, BoxLayout.Y_AXIS));
        incomingListPanel.setOpaque(false);

        sentListPanel = new JPanel();
        sentListPanel.setLayout(new BoxLayout(sentListPanel, BoxLayout.Y_AXIS));
        sentListPanel.setOpaque(false);

        body.add(createBigSectionLabel("LỜI MỜI ĐÃ NHẬN"));
        body.add(incomingListPanel);
        body.add(Box.createVerticalStrut(20));
        body.add(createBigSectionLabel("LỜI MỜI ĐÃ GỬI"));
        body.add(sentListPanel);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(body, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrap);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PAGE_BG);
        scroll.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        page.add(scroll, BorderLayout.CENTER);

        renderRequestsPage();
        return page;
    }

    private void renderRequestsPage() {
        incomingListPanel.removeAll();
        if (pendingIncoming.isEmpty()) {
            incomingListPanel.add(createEmptyLabel("Không có lời mời kết bạn nào."));
        }
        for (String from : pendingIncoming) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setOpaque(false);
            JButton accept = createActionButton("Chấp Nhận", GREEN);
            accept.addActionListener(e -> {
                accept.setEnabled(false);
                client.acceptFriendRequest(from);
            });
            JButton decline = createActionButton("Từ Chối", RED);
            decline.addActionListener(e -> {
                decline.setEnabled(false);
                client.declineFriendRequest(from);
            });
            actions.add(accept);
            actions.add(decline);
            boolean on = onlineStatus.getOrDefault(from, false);
            incomingListPanel.add(createUserCard(from, "muốn kết bạn với bạn", on, actions));
            incomingListPanel.add(Box.createVerticalStrut(8));
        }

        sentListPanel.removeAll();
        if (pendingSent.isEmpty()) {
            sentListPanel.add(createEmptyLabel("Bạn chưa gửi lời mời nào."));
        }
        for (String to : pendingSent) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setOpaque(false);
            JButton cancel = createActionButton("Thu Hồi", new Color(108, 117, 125));
            cancel.addActionListener(e -> {
                cancel.setEnabled(false);
                client.declineFriendRequest(to);
            });
            actions.add(createInfoLabel("Đang chờ chấp nhận"));
            actions.add(cancel);
            boolean on = onlineStatus.getOrDefault(to, false);
            sentListPanel.add(createUserCard(to, on ? "● Đang trực tuyến" : "○ Ngoại tuyến", on, actions));
            sentListPanel.add(Box.createVerticalStrut(8));
        }

        incomingListPanel.revalidate();
        incomingListPanel.repaint();
        sentListPanel.revalidate();
        sentListPanel.repaint();
        updateRequestBadge();
    }

    // =====================================================================
    //                          THÀNH PHẦN UI DÙNG CHUNG
    // =====================================================================

    private JLabel createAvatar(String name, int size, Color bg, Color fg) {
        JLabel l = new JLabel(getInitials(name), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        Dimension d = new Dimension(size, size);
        l.setPreferredSize(d);
        l.setMaximumSize(d);
        l.setMinimumSize(d);
        l.setFont(new Font("Segoe UI", Font.BOLD, size / 2 - 4));
        l.setForeground(fg);
        return l;
    }

    private Color colorFor(String name) {
        Color[] palette = {new Color(0, 132, 255), new Color(111, 66, 193), new Color(232, 62, 140),
                new Color(253, 126, 20), new Color(32, 201, 151), new Color(23, 162, 184)};
        return palette[Math.abs(name.hashCode()) % palette.length];
    }

    /** Thẻ người dùng: avatar + tên + dòng phụ + vùng nút bên phải */
    private JPanel createUserCard(String name, String subtitle, boolean online, JComponent actions) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1), new EmptyBorder(12, 14, 12, 14)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(createAvatar(name, 46, colorFor(name), Color.WHITE), BorderLayout.WEST);

        JPanel info = new JPanel(new GridLayout(2, 1));
        info.setOpaque(false);
        JLabel n = new JLabel(name);
        n.setFont(new Font("Segoe UI", Font.BOLD, 15));
        JLabel s = new JLabel(subtitle);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        s.setForeground(subtitle.startsWith("●") ? GREEN : new Color(120, 125, 135));
        info.add(n);
        info.add(s);
        card.add(info, BorderLayout.CENTER);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(actions);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JPanel createPageHeader(String title, String subtitle) {
        JPanel h = new JPanel(new GridLayout(2, 1));
        h.setBackground(Color.WHITE);
        h.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(14, 22, 14, 22)));
        JLabel t = new JLabel(title);
        t.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel s = new JLabel(subtitle);
        s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        s.setForeground(new Color(120, 125, 135));
        h.add(t);
        h.add(s);
        return h;
    }

    private JLabel createBigSectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(110, 115, 125));
        l.setBorder(new EmptyBorder(0, 2, 8, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel createEmptyLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 13));
        l.setForeground(new Color(140, 145, 155));
        l.setBorder(new EmptyBorder(4, 4, 4, 4));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel createInfoLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        l.setForeground(new Color(120, 125, 135));
        return l;
    }

    private JButton createActionButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(245, 247, 250));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1), new EmptyBorder(5, 10, 5, 10)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel createListCell(Object value, boolean selected, Color fg) {
        JLabel c = new JLabel(String.valueOf(value));
        c.setOpaque(true);
        c.setBorder(new EmptyBorder(0, 14, 0, 0));
        c.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 13));
        c.setBackground(selected ? new Color(230, 240, 255) : Color.WHITE);
        c.setForeground(selected ? new Color(0, 102, 204) : fg);
        return c;
    }

    private JPanel createSectionHeader(String title) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = new JLabel(title);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(new Color(135, 140, 150));
        p.add(l);
        return p;
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.substring(0, 1).toUpperCase();
    }

    // =====================================================================
    //                              LOGIC CHAT
    // =====================================================================

    private void switchChatContext(String targetKey, String title, String subtitle) {
        this.currentChatTarget = targetKey;
        unreadCounts.remove(targetKey);
        updateUnreadBadges();
        currentChatTitleLabel.setText(title);
        currentChatSubtitleLabel.setText(subtitle);

        chatHistoryPanel.removeAll();
        for (Message m : conversationMap.computeIfAbsent(targetKey, k -> new ArrayList<>())) {
            chatHistoryPanel.add(new ChatBubblePanel(m, currentUsername.equalsIgnoreCase(m.getSender())));
        }
        chatHistoryPanel.revalidate();
        chatHistoryPanel.repaint();
        scrollToBottom();
        messageInputField.requestFocus();
    }

    /** Chuyển sang trang Tin Nhắn và mở kênh chat với bạn bè */
    private void openFriendChat(String friend) {
        showPage(PAGE_CHAT);
        for (int i = 0; i < friendsModel.size(); i++) {
            if (friendsModel.get(i).username.equals(friend)) {
                friendsList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void connectToServer() {
        client = new ChatClient(serverHost, serverPort, currentUsername);
        client.addListener(this);

        new Thread(() -> {
            boolean ok = client.connect();
            if (!ok) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Không thể kết nối đến Máy chủ tại " + serverHost + ":" + serverPort + "!\n"
                                + "Vui lòng đảm bảo Server đã được khởi động trước.",
                        "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE));
            }
        }).start();

        udpService = new UdpMulticastService(currentUsername, this);
        udpService.start();
    }

    /** Kiểm tra quyền nhắn tin riêng: chỉ bạn bè mới được nhắn */
    private boolean canSendToCurrentTarget() {
        if (currentChatTarget.startsWith("USER:")) {
            String target = currentChatTarget.substring(5);
            if (!myFriends.contains(target)) {
                JOptionPane.showMessageDialog(this,
                        "Bạn chỉ có thể nhắn tin với bạn bè.\nHãy kết bạn với " + target + " trước.",
                        "Chưa kết bạn", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        return true;
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty() || !canSendToCurrentTarget()) return;

        if ("UDP_MULTICAST".equals(currentChatTarget)) {
            udpService.sendMulticastMessage(text);
        } else if ("ALL".equals(currentChatTarget)) {
            client.sendBroadcast(text);
        } else if (currentChatTarget.startsWith("ROOM:")) {
            client.sendMulticast(currentChatTarget.substring(5), text);
        } else if (currentChatTarget.startsWith("USER:")) {
            client.sendPrivate(currentChatTarget.substring(5), text);
        }
        messageInputField.setText("");
    }

    /** Trả về {receiver, room} theo kênh hiện tại; null nếu là kênh UDP (không hỗ trợ file) */
    private String[] currentFileTarget() {
        if ("UDP_MULTICAST".equals(currentChatTarget)) {
            JOptionPane.showMessageDialog(this, "Kênh UDP Multicast chỉ hỗ trợ tin nhắn văn bản.",
                    "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        if (currentChatTarget.startsWith("ROOM:")) {
            String room = currentChatTarget.substring(5);
            return new String[]{room, room};
        }
        if (currentChatTarget.startsWith("USER:")) {
            return new String[]{currentChatTarget.substring(5), null};
        }
        return new String[]{"ALL", null};
    }

    private void toggleVoiceRecording() {
        if (!audioRecorder.isRecording()) {
            if (!canSendToCurrentTarget()) return;
            if (audioRecorder.startRecording()) {
                voiceRecordButton.setText("Đang Ghi Âm... (Bấm gửi)");
                voiceRecordButton.setBackground(new Color(255, 235, 235));
                voiceRecordButton.setForeground(Color.RED);
            } else {
                JOptionPane.showMessageDialog(this, "Không thể kích hoạt micro!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            byte[] wavData = audioRecorder.stopRecording();
            voiceRecordButton.setText("Ghi Âm Voice");
            voiceRecordButton.setBackground(new Color(245, 247, 250));
            voiceRecordButton.setForeground(Color.BLACK);
            String[] t = currentFileTarget();
            if (t != null && wavData != null && wavData.length > 0) {
                client.sendAudioVoiceNote(t[0], t[1], wavData);
            }
        }
    }

    private void chooseAndSend(MessageType type) {
        if (!canSendToCurrentTarget()) return;
        String[] t = currentFileTarget();
        if (t == null) return;

        JFileChooser chooser = new JFileChooser();
        if (type == MessageType.IMAGE_MESSAGE) {
            chooser.setDialogTitle("Chọn Hình Ảnh Để Gửi");
            chooser.setFileFilter(new FileNameExtensionFilter("Hình Ảnh (*.png, *.jpg, *.jpeg, *.gif)", "png", "jpg", "jpeg", "gif"));
        } else if (type == MessageType.VIDEO_MESSAGE) {
            chooser.setDialogTitle("Chọn Video Để Gửi");
            chooser.setFileFilter(new FileNameExtensionFilter("Video (*.mp4, *.mkv, *.avi, *.mov)", "mp4", "mkv", "avi", "mov"));
        } else {
            chooser.setDialogTitle("Chọn Tệp Tin Đính Kèm");
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            client.sendFileAttachment(type, t[0], t[1], f);
        }
    }

    private void showEmojiPicker() {
        EmojiPickerPopup popup = new EmojiPickerPopup(emoji -> {
            messageInputField.setText(messageInputField.getText() + emoji);
            messageInputField.requestFocus();
        });
        popup.show(emojiButton, 0, -popup.getPreferredSize().height);
    }

    private void promptJoinCustomRoom() {
        String room = JOptionPane.showInputDialog(this, "Nhập tên phòng Multicast muốn tham gia:", "Tham gia phòng", JOptionPane.QUESTION_MESSAGE);
        if (room != null && !room.trim().isEmpty()) {
            String clean = room.trim();
            if (!broadcastRoomModel.contains("# " + clean)) {
                broadcastRoomModel.addElement("# " + clean);
            }
            client.joinRoom(clean);
            broadcastRoomList.setSelectedValue("# " + clean, true);
        }
    }

    private void appendMessage(String targetKey, Message msg) {
        conversationMap.computeIfAbsent(targetKey, k -> new ArrayList<>()).add(msg);
        boolean isViewingThisChat = PAGE_CHAT.equals(currentActivePage) && targetKey.equalsIgnoreCase(currentChatTarget);
        if (targetKey.equalsIgnoreCase(currentChatTarget)) {
            chatHistoryPanel.add(new ChatBubblePanel(msg, currentUsername.equalsIgnoreCase(msg.getSender())));
            chatHistoryPanel.revalidate();
            chatHistoryPanel.repaint();
            scrollToBottom();
        }

        // Kiểm tra hiển thị thông báo khi có người khác gửi tin nhắn mới tới
        boolean isFromOther = msg.getSender() != null
                && !currentUsername.equalsIgnoreCase(msg.getSender())
                && !"HỆ THỐNG".equalsIgnoreCase(msg.getSender())
                && !"SERVER".equalsIgnoreCase(msg.getSender())
                && msg.getType() != MessageType.SYSTEM_NOTIFICATION;
        boolean isLiveMessage = msg.getTimestamp() >= (sessionStartTime - 2500);

        if (isFromOther && isLiveMessage) {
            if (!isViewingThisChat) {
                unreadCounts.merge(targetKey, 1, Integer::sum);
                updateUnreadBadges();
            }
            String preview = buildMessagePreview(msg);
            String scopeLabel = getScopeDisplayName(targetKey);
            showToastNotification(
                    "Tin nhắn mới từ " + msg.getSender() + " (" + scopeLabel + "): " + preview,
                    "Xem Tin Nhắn",
                    () -> openTargetConversation(targetKey)
            );
        }
    }

    private String buildMessagePreview(Message msg) {
        if (msg.getType() == MessageType.IMAGE_MESSAGE) {
            return "[Hình ảnh] " + (msg.getFileName() != null ? msg.getFileName() : "");
        } else if (msg.getType() == MessageType.VIDEO_MESSAGE) {
            return "[Video] " + (msg.getFileName() != null ? msg.getFileName() : "");
        } else if (msg.getType() == MessageType.AUDIO_MESSAGE) {
            return "[Tin nhắn thoại Voice]";
        } else if (msg.getType() == MessageType.FILE_TRANSFER) {
            return "[Tệp đính kèm] " + (msg.getFileName() != null ? msg.getFileName() : "");
        }
        String c = msg.getContent() != null ? msg.getContent().trim() : "";
        return c.length() > 45 ? c.substring(0, 45) + "..." : c;
    }

    private String getScopeDisplayName(String targetKey) {
        if ("ALL".equalsIgnoreCase(targetKey)) return "Kênh Chung Broadcast";
        if ("UDP_MULTICAST".equalsIgnoreCase(targetKey)) return "Kênh UDP Multicast";
        if (targetKey.startsWith("ROOM:")) return "Phòng #" + targetKey.substring(5);
        if (targetKey.startsWith("USER:")) return "Chat riêng 1-1";
        return targetKey;
    }

    private void openTargetConversation(String targetKey) {
        showPage(PAGE_CHAT);
        if (targetKey.startsWith("USER:")) {
            String friend = targetKey.substring(5);
            openFriendChat(friend);
        } else if ("ALL".equalsIgnoreCase(targetKey)) {
            broadcastRoomList.setSelectedIndex(0);
        } else if ("UDP_MULTICAST".equalsIgnoreCase(targetKey)) {
            broadcastRoomList.setSelectedIndex(1);
        } else if (targetKey.startsWith("ROOM:")) {
            String roomEntry = "# " + targetKey.substring(5);
            broadcastRoomList.setSelectedValue(roomEntry, true);
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar v = chatScrollPane.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    private void rebuildFriendsList() {
        String selected = friendsList.getSelectedValue() != null ? friendsList.getSelectedValue().username : null;
        friendsModel.clear();
        int reselect = -1;
        for (String f : myFriends) {
            friendsModel.addElement(new UserListItem(f, onlineStatus.getOrDefault(f, false)));
            if (f.equals(selected)) reselect = friendsModel.size() - 1;
        }
        if (reselect != -1) friendsList.setSelectedIndex(reselect);
        friendsEmptyHint.setVisible(myFriends.isEmpty());
    }

    // =====================================================================
    //                       CALLBACKS TỪ TCP CHAT CLIENT
    // =====================================================================

    @Override
    public void onMessageReceived(Message msg) {
        SwingUtilities.invokeLater(() -> {
            String targetKey;
            boolean hasRoom = msg.getRoom() != null && !msg.getRoom().isEmpty();
            if (msg.getType() == MessageType.CHAT_BROADCAST) {
                targetKey = "ALL";
            } else if (msg.getType() == MessageType.CHAT_MULTICAST || hasRoom) {
                targetKey = "ROOM:" + msg.getRoom();
            } else if (msg.getType() == MessageType.SYSTEM_NOTIFICATION) {
                if (msg.getReceiver() != null && !msg.getReceiver().isEmpty() && !"ALL".equalsIgnoreCase(msg.getReceiver())) {
                    targetKey = "USER:" + msg.getReceiver();
                } else {
                    targetKey = currentChatTarget;
                }
            } else if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
                targetKey = "ALL";
            } else {
                // Tin nhắn riêng (text / file) giữa 2 người
                targetKey = currentUsername.equalsIgnoreCase(msg.getSender())
                        ? "USER:" + msg.getReceiver() : "USER:" + msg.getSender();
            }
            appendMessage(targetKey, msg);
        });
    }

    @Override
    public void onUserListUpdated(List<String> userRawList) {
        SwingUtilities.invokeLater(() -> {
            onlineStatus.clear();
            for (String raw : userRawList) {
                int idx = raw.lastIndexOf(':');
                String name = idx > 0 ? raw.substring(0, idx) : raw;
                boolean on = idx > 0 && "ONLINE".equalsIgnoreCase(raw.substring(idx + 1));
                onlineStatus.put(name, on);
            }
            rebuildFriendsList();
            renderRequestsPage();
            renderSearchResult();
        });
    }

    @Override
    public void onFriendDataUpdated(List<String> friends, List<String> pending, List<String> sent) {
        SwingUtilities.invokeLater(() -> {
            myFriends.clear();
            myFriends.addAll(friends);
            pendingIncoming.clear();
            pendingIncoming.addAll(pending);
            pendingSent.clear();
            pendingSent.addAll(sent);

            rebuildFriendsList();
            renderRequestsPage();
            renderSearchResult();
        });
    }

    @Override
    public void onFriendRequestReceived(String fromUser) {
        SwingUtilities.invokeLater(() -> {
            Message notice = new Message(MessageType.SYSTEM_NOTIFICATION, "HỆ THỐNG",
                    fromUser + " đã gửi lời mời kết bạn. Vào trang 'Lời Mời' để chấp nhận hoặc từ chối.");
            appendMessage(currentChatTarget, notice);
            showToastNotification(
                    fromUser + " vừa gửi lời mời kết bạn cho bạn!",
                    "Xem Lời Mời",
                    () -> showPage(PAGE_REQUESTS)
            );
        });
    }

    @Override
    public void onSearchResult(String query, String foundUser, boolean online) {
        SwingUtilities.invokeLater(() -> {
            lastSearchFound = foundUser;
            lastSearchOnline = online;
            if (foundUser != null && !onlineStatus.containsKey(foundUser)) {
                onlineStatus.put(foundUser, online);
            }
            renderSearchResult();
        });
    }

    @Override
    public void onRoomListUpdated(List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            for (String r : rooms) {
                String entry = "# " + r;
                if (!broadcastRoomModel.contains(entry)) {
                    broadcastRoomModel.addElement(entry);
                }
            }
        });
    }

    @Override
    public void onConnected() {
        System.out.println("[Client " + currentUsername + "] Đã kết nối TCP thành công.");
    }

    @Override
    public void onConnectionFailed(String reason) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Kết nối thất bại: " + reason, "Lỗi", JOptionPane.ERROR_MESSAGE));
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this, "Mất kết nối với máy chủ!", "Cảnh báo", JOptionPane.WARNING_MESSAGE));
    }

    @Override
    public void onUdpMessageReceived(String sender, String text) {
        SwingUtilities.invokeLater(() -> {
            Message msg = new Message(MessageType.CHAT_MULTICAST, sender, text);
            msg.setRoom("UDP_MULTICAST");
            appendMessage("UDP_MULTICAST", msg);
        });
    }

    // =====================================================================
    //                            KHỞI CHẠY ỨNG DỤNG
    // =====================================================================

    public static void main(String[] args) {
        String host = "localhost";
        int port = Config.DEFAULT_TCP_PORT;
        String name = "";

        if (args.length >= 1) {
            name = args[0];
            if (args.length >= 2) host = args[1];
            if (args.length >= 3) {
                try { port = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            }
        } else {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JTextField nameInput = new JTextField("NguoiDung_" + (new Random().nextInt(900) + 100));
            nameInput.setFont(new Font("Segoe UI", Font.BOLD, 13));
            JTextField hostInput = new JTextField("localhost");
            JTextField portInput = new JTextField(String.valueOf(Config.DEFAULT_TCP_PORT));

            JPanel hostRow = new JPanel(new BorderLayout(4, 0));
            hostRow.add(hostInput, BorderLayout.CENTER);
            JButton findLanBtn = new JButton("Tìm IP LAN");
            findLanBtn.setToolTipText("Tự động dò tìm IP máy chủ Server trong cùng mạng Wi-Fi");
            findLanBtn.addActionListener(ev -> {
                String foundIp = discoverServerInLan();
                if (foundIp != null) {
                    hostInput.setText(foundIp);
                    JOptionPane.showMessageDialog(null, "Đã tìm thấy Server trong mạng Wi-Fi tại IP: " + foundIp,
                            "Tìm Thấy Server", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Không tự động dò thấy qua UDP Broadcast.\nHãy nhập trực tiếp địa chỉ IPv4 hiện trên màn hình Log của máy Server (ví dụ: 192.168.1.x hoặc 172.20.10.x).",
                            "Nhập IP Thủ Công", JOptionPane.WARNING_MESSAGE);
                }
            });
            hostRow.add(findLanBtn, BorderLayout.EAST);

            JPanel loginPanel = new JPanel(new GridLayout(4, 2, 8, 8));
            loginPanel.add(new JLabel("Tên tài khoản (Username):"));
            loginPanel.add(nameInput);
            loginPanel.add(new JLabel("Địa chỉ Server (IP):"));
            loginPanel.add(hostRow);
            loginPanel.add(new JLabel("Cổng (Port TCP):"));
            loginPanel.add(portInput);
            loginPanel.add(new JLabel("Gợi ý kết nối 2 máy:"));
            loginPanel.add(new JLabel("<html><small style='color:#0066cc;'>* Máy 2 cùng Wi-Fi: bấm 'Tìm IP LAN' hoặc nhập IP của Máy 1</small></html>"));

            int result = JOptionPane.showConfirmDialog(null, loginPanel, "Đăng Nhập / Tạo Tài Khoản Chat",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                System.exit(0);
                return;
            }
            name = nameInput.getText().trim();
            host = hostInput.getText().trim();
            try {
                port = Integer.parseInt(portInput.getText().trim());
            } catch (NumberFormatException ignored) {}
        }

        if (name.isEmpty()) {
            name = "User_" + (System.currentTimeMillis() % 1000);
        }

        final String finalHost = host;
        final int finalPort = port;
        final String finalName = name;
        SwingUtilities.invokeLater(() -> new ClientGUI(finalHost, finalPort, finalName).setVisible(true));
    }

    private static String discoverServerInLan() {
        try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(1500);
            byte[] sendData = "DISCOVER_CHAT_SERVER_REQUEST".getBytes();
            java.net.DatagramPacket sendPacket = new java.net.DatagramPacket(
                    sendData, sendData.length,
                    java.net.InetAddress.getByName("255.255.255.255"),
                    Config.UDP_BROADCAST_PORT
            );
            socket.send(sendPacket);

            byte[] recvBuf = new byte[256];
            java.net.DatagramPacket recvPacket = new java.net.DatagramPacket(recvBuf, recvBuf.length);
            socket.receive(recvPacket);
            String msg = new String(recvPacket.getData(), 0, recvPacket.getLength()).trim();
            if ("CHAT_SERVER_DISCOVERED".equals(msg)) {
                return recvPacket.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
