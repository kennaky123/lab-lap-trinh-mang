package rmi.server;

import rmi.common.IDataSyncService;
import rmi.common.RecordItem;
import rmi.common.RmiUser;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Hiện thực Remote Interface IDataSyncService:
 * - Xác thực đăng nhập và phân quyền (ADMIN, EDITOR, VIEWER)
 * - Điều phối dữ liệu phân tán giữa 2 Máy chủ Server 1 và Server 2
 * - Tự động đồng bộ hóa hai chiều (Two-Way Active Replication) cả Dữ liệu lẫn Tài khoản
 * - Tự động phát hiện và đồng bộ bù dữ liệu (Catch-up Sync) khi máy chủ đối tác bật lại
 */
public class DataSyncServiceImpl extends UnicastRemoteObject implements IDataSyncService {
    private static final long serialVersionUID = 1L;

    public interface SyncEventListener {
        void onLog(String text);
        void onDataChanged(List<RecordItem> currentData);
        void onUsersChanged(List<RmiUser> currentUsers);
        void onPeerStatusChanged(boolean isPeerOnline);
    }

    private final String serverId;         // "Server 1" hoặc "Server 2"
    private final int myPort;              // 1099 hoặc 1100
    private String peerHost;               // IP máy thực của Peer Server (hoặc localhost)
    private int peerPort;                  // Port của Peer Server
    private String peerServiceUrl;         // rmi://peerHost:peerPort/DataSyncService

    private final RmiDatabase db;
    private final List<SyncEventListener> listeners = new ArrayList<>();
    private boolean isPeerOnline = false;

    public DataSyncServiceImpl(String serverId, int myPort, String peerHost, int peerPort, String dataDir) throws RemoteException {
        super(myPort);
        this.serverId = serverId;
        this.myPort = myPort;
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.peerServiceUrl = "rmi://" + peerHost + ":" + peerPort + "/DataSyncService";
        this.db = new RmiDatabase(dataDir);
    }

    public synchronized void updatePeerTarget(String newPeerHost, int newPeerPort) {
        this.peerHost = newPeerHost;
        this.peerPort = newPeerPort;
        this.peerServiceUrl = "rmi://" + newPeerHost + ":" + newPeerPort + "/DataSyncService";
        this.isPeerOnline = false;
        log("[CẤU HÌNH PEER] Đã cập nhật địa chỉ Server đối tác thành: " + this.peerServiceUrl);
    }

    public void addListener(SyncEventListener l) {
        listeners.add(l);
    }

    // ==================== ĐĂNG NHẬP & PHÂN QUYỀN ====================

    @Override
    public synchronized RmiUser login(String username, String password) throws RemoteException {
        RmiUser user = db.authenticate(username, password);
        if (user != null) {
            log("[XÁC THỰC RMI] Đăng nhập thành công: " + user.getUsername() + " (Vai trò: " + user.getRole() + " - Quyền: " + user.getPermissionSummary() + ")");
        } else {
            log("[TỪ CHỐI ĐĂNG NHẬP] Sai tài khoản hoặc mật khẩu cho: " + username);
        }
        return user;
    }

    @Override
    public synchronized List<RmiUser> getAllUsers() throws RemoteException {
        return db.getAllUsers();
    }

    @Override
    public synchronized boolean saveUser(RmiUser user) throws RemoteException {
        if (user == null || user.getUsername() == null) return false;
        boolean ok = db.putUser(user);
        if (ok) {
            log("[PHÂN QUYỀN SQL] Đã lưu tài khoản '" + user.getUsername() + "' [" + user.getRole() + ": " + user.getPermissionSummary() + "]");
            notifyUsersChanged();
            replicateUserToPeer("SAVE_USER", user);
        }
        return ok;
    }

    @Override
    public synchronized boolean deleteUser(String username) throws RemoteException {
        if (username == null) return false;
        boolean ok = db.removeUser(username);
        if (ok) {
            log("[PHÂN QUYỀN SQL] Đã xóa tài khoản '" + username + "'");
            notifyUsersChanged();
            RmiUser dummy = new RmiUser();
            dummy.setUsername(username);
            replicateUserToPeer("DELETE_USER", dummy);
        }
        return ok;
    }

    @Override
    public synchronized boolean syncUserReplicate(String action, RmiUser user, String senderServerId) throws RemoteException {
        if (serverId.equals(senderServerId) || user == null) return true;
        log("[ĐỒNG BỘ USER TỪ " + senderServerId + "] Nhận lệnh: " + action + " cho tài khoản '" + user.getUsername() + "'");
        if ("SAVE_USER".equals(action)) {
            db.putUser(user);
        } else if ("DELETE_USER".equals(action)) {
            db.removeUser(user.getUsername());
        }
        notifyUsersChanged();
        return true;
    }

    private void replicateUserToPeer(String action, RmiUser user) {
        new Thread(() -> {
            try {
                IDataSyncService peer = (IDataSyncService) Naming.lookup(peerServiceUrl);
                peer.syncUserReplicate(action, user, serverId);
                updatePeerStatus(true);
            } catch (Exception e) {
                updatePeerStatus(false);
            }
        }).start();
    }

    // ==================== CÁC PHƯƠNG THỨC GỌI TỪ CLIENT ====================

    @Override
    public synchronized boolean addRecord(RecordItem item) throws RemoteException {
        if (item == null || item.getId() == null) return false;
        if (db.get(item.getId()) != null) {
            log("[CLIENT THẤT BẠI] Mã ID " + item.getId() + " đã tồn tại trên " + serverId);
            return false;
        }

        item.setLastModified(System.currentTimeMillis());
        if (item.getLastUpdatedByServer() == null || item.getLastUpdatedByServer().isEmpty()) {
            item.setLastUpdatedByServer(serverId);
        }
        db.put(item);
        log("[CLIENT ADD] Đã thêm bản ghi " + item.getId() + " vào " + serverId + " (" + item.getLastUpdatedByServer() + ")");
        notifyDataChanged();

        // Đồng bộ tức thời sang Server đối tác (Replication)
        replicateToPeer("ADD", item);
        return true;
    }

    @Override
    public synchronized boolean updateRecord(RecordItem item) throws RemoteException {
        if (item == null || item.getId() == null) return false;
        if (db.get(item.getId()) == null) {
            log("[CLIENT THẤT BẠI] Mã ID " + item.getId() + " không tồn tại để cập nhật");
            return false;
        }

        item.setLastModified(System.currentTimeMillis());
        if (item.getLastUpdatedByServer() == null || item.getLastUpdatedByServer().isEmpty()) {
            item.setLastUpdatedByServer(serverId);
        }
        db.put(item);
        log("[CLIENT UPDATE] Đã cập nhật bản ghi " + item.getId() + " trên " + serverId + " (" + item.getLastUpdatedByServer() + ")");
        notifyDataChanged();

        replicateToPeer("UPDATE", item);
        return true;
    }

    @Override
    public synchronized boolean deleteRecord(String id) throws RemoteException {
        RecordItem item = db.get(id);
        if (item == null) return false;

        db.remove(id);
        log("[CLIENT DELETE] Đã xóa bản ghi " + id + " khỏi " + serverId);
        notifyDataChanged();

        RecordItem dummy = new RecordItem();
        dummy.setId(id);
        dummy.setLastUpdatedByServer(serverId);
        replicateToPeer("DELETE", dummy);
        return true;
    }

    @Override
    public List<RecordItem> getAllRecords() throws RemoteException {
        return db.getAll();
    }

    @Override
    public RecordItem getRecordById(String id) throws RemoteException {
        return db.get(id);
    }

    @Override
    public boolean ping() throws RemoteException {
        return true;
    }

    @Override
    public String getServerInfo() throws RemoteException {
        return serverId + " (Port: " + myPort + ", Số bản ghi: " + db.count() + ")";
    }

    // ==================== CÁC PHƯƠNG THỨC ĐỒNG BỘ GIỮA 2 SERVER ====================

    @Override
    public synchronized boolean syncReplicate(String action, RecordItem item, String senderServerId) throws RemoteException {
        if (serverId.equals(senderServerId)) {
            return true;
        }

        log("[ĐỒNG BỘ RMI TỪ " + senderServerId + "] Nhận lệnh: " + action + " cho bản ghi " + item.getId());

        if ("ADD".equals(action) || "UPDATE".equals(action)) {
            db.put(item);
        } else if ("DELETE".equals(action)) {
            db.remove(item.getId());
        }

        notifyDataChanged();
        return true;
    }

    @Override
    public synchronized List<RecordItem> requestFullSync() throws RemoteException {
        log("[FULL RESYNC] Máy chủ đối tác yêu cầu đồng bộ toàn bộ cơ sở dữ liệu!");
        return db.getAll();
    }

    private void replicateToPeer(String action, RecordItem item) {
        new Thread(() -> {
            try {
                IDataSyncService peer = (IDataSyncService) Naming.lookup(peerServiceUrl);
                peer.syncReplicate(action, item, serverId);
                log("[ĐỒNG BỘ THÀNH CÔNG] Đã đẩy thay đổi (" + action + ") sang " + peerServiceUrl);
                updatePeerStatus(true);
            } catch (Exception e) {
                log("[CẢNH BÁO ĐỒNG BỘ] Không thể kết nối tới Peer Server (" + peerServiceUrl + "): " + e.getMessage());
                updatePeerStatus(false);
            }
        }).start();
    }

    public void startPeerMonitor() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(4000);
                    IDataSyncService peer = (IDataSyncService) Naming.lookup(peerServiceUrl);
                    boolean alive = peer.ping();

                    if (!isPeerOnline && alive) {
                        log("[TỰ ĐỘNG PHÁT HIỆN] Peer Server (" + peerHost + ":" + peerPort + ") đã ONLINE trở lại!");
                        log("[CATCH-UP SYNC] Đang kéo toàn bộ dữ liệu mới nhất từ Peer Server về...");

                        List<RecordItem> peerData = peer.requestFullSync();
                        if (peerData != null && !peerData.isEmpty()) {
                            for (RecordItem it : peerData) {
                                RecordItem local = db.get(it.getId());
                                if (local == null || it.getLastModified() > local.getLastModified()) {
                                    db.put(it);
                                }
                            }
                            notifyDataChanged();
                            log("[HOÀN TẤT ĐỒNG BỘ] Đã đồng bộ bù thành công (" + db.count() + " bản ghi)");
                        }

                        List<RmiUser> peerUsers = peer.getAllUsers();
                        if (peerUsers != null) {
                            for (RmiUser u : peerUsers) {
                                db.putUser(u);
                            }
                            notifyUsersChanged();
                        }
                    }
                    updatePeerStatus(alive);
                } catch (Exception e) {
                    if (isPeerOnline) {
                        log("[MẤT KẾT NỐI] Peer Server (" + peerServiceUrl + ") đã OFFLINE.");
                    }
                    updatePeerStatus(false);
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void updatePeerStatus(boolean online) {
        this.isPeerOnline = online;
        for (SyncEventListener l : listeners) {
            l.onPeerStatusChanged(online);
        }
    }

    private void notifyDataChanged() {
        List<RecordItem> list = db.getAll();
        for (SyncEventListener l : listeners) {
            l.onDataChanged(list);
        }
    }

    private void notifyUsersChanged() {
        List<RmiUser> users = db.getAllUsers();
        for (SyncEventListener l : listeners) {
            l.onUsersChanged(users);
        }
    }

    private void log(String text) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String full = "[" + sdf.format(new Date()) + " - " + serverId + "] " + text;
        System.out.println(full);
        for (SyncEventListener l : listeners) {
            l.onLog(full);
        }
    }

    public List<RecordItem> getCurrentData() {
        return db.getAll();
    }

    public List<RmiUser> getCurrentUsers() {
        return db.getAllUsers();
    }

    public String getServerId() { return serverId; }
    public int getMyPort() { return myPort; }
    public String getPeerHost() { return peerHost; }
    public int getPeerPort() { return peerPort; }
}
