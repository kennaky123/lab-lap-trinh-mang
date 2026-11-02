package rmi.server;

import rmi.common.RecordItem;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý cơ sở dữ liệu lưu trữ bền vững trên đĩa cho từng Server
 */
public class RmiDatabase {
    private final String dataDir;
    private final File dataFile;
    private final Map<String, RecordItem> recordMap = new ConcurrentHashMap<>();

    public RmiDatabase(String dataDir) {
        this.dataDir = dataDir;
        File dir = new File(dataDir);
        if (!dir.exists()) dir.mkdirs();
        this.dataFile = new File(dir, "records.dat");
        loadData();
    }

    @SuppressWarnings("unchecked")
    private synchronized void loadData() {
        if (dataFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
                Map<String, RecordItem> map = (Map<String, RecordItem>) ois.readObject();
                recordMap.putAll(map);
            } catch (Exception e) {
                System.err.println("[RMI DB] Lỗi nạp dữ liệu: " + e.getMessage());
            }
        }
    }

    public synchronized void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dataFile))) {
            oos.writeObject(new HashMap<>(recordMap));
        } catch (Exception e) {
            System.err.println("[RMI DB] Lưu dữ liệu thất bại: " + e.getMessage());
        }
    }

    public synchronized List<RecordItem> getAll() {
        List<RecordItem> list = new ArrayList<>(recordMap.values());
        list.sort(Comparator.comparing(RecordItem::getId));
        return list;
    }

    public synchronized RecordItem get(String id) {
        return recordMap.get(id);
    }

    public synchronized boolean put(RecordItem item) {
        if (item == null || item.getId() == null) return false;
        recordMap.put(item.getId(), item);
        saveData();
        return true;
    }

    public synchronized boolean remove(String id) {
        if (id == null) return false;
        RecordItem removed = recordMap.remove(id);
        if (removed != null) {
            saveData();
            return true;
        }
        return false;
    }

    public synchronized void replaceAll(List<RecordItem> items) {
        recordMap.clear();
        for (RecordItem it : items) {
            recordMap.put(it.getId(), it);
        }
        saveData();
    }

    public int count() {
        return recordMap.size();
    }
}

