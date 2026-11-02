package rmi.common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote Interface định nghĩa các phương thức gọi từ xa (RMI):
 * - Thao tác dữ liệu từ Client (CRUD)
 * - Thao tác đồng bộ hóa phân tán giữa 2 Server (Data Replication)
 */
public interface IDataSyncService extends Remote {

    // ==================== CÁC PHƯƠNG THỨC CHO CLIENT ====================

    List<RecordItem> getAllRecords() throws RemoteException;

    RecordItem getRecordById(String id) throws RemoteException;

    boolean addRecord(RecordItem item) throws RemoteException;

    boolean updateRecord(RecordItem item) throws RemoteException;

    boolean deleteRecord(String id) throws RemoteException;

    // ==================== CÁC PHƯƠNG THỨC ĐỒNG BỘ 2 SERVER ====================

    /**
     * Nhận yêu cầu sao chép (Replicate) thay đổi từ Server đối tác
     * @param action "ADD", "UPDATE", "DELETE"
     * @param item Đối tượng dữ liệu
     * @param senderServerId Tên Server gửi (tránh vòng lặp vô tận ping-pong)
     */
    boolean syncReplicate(String action, RecordItem item, String senderServerId) throws RemoteException;

    /**
     * Đồng bộ toàn bộ dữ liệu khi một Server vừa bật lại sau khi mất kết nối (Catch-up / Resync)
     */
    List<RecordItem> requestFullSync() throws RemoteException;

    /**
     * Kiểm tra trạng thái sống của Server đối tác
     */
    boolean ping() throws RemoteException;

    /**
     * Lấy tên và thông tin của Server hiện tại
     */
    String getServerInfo() throws RemoteException;
}

