package ftp.common;

import java.io.File;

/**
 * Cấu hình hệ thống FTP Server và FTP Client
 */
public class FtpConfig {
    // Cổng điều khiển FTP mặc định (Sử dụng 2121 để không cần quyền Administrator trên Windows)
    public static final int DEFAULT_PORT = 2121;
    public static final int UDP_DISCOVERY_PORT = 2122;

    // Dải cổng phục vụ cho Passive Data Connection (PASV)
    public static final int PASV_PORT_MIN = 30000;
    public static final int PASV_PORT_MAX = 30100;

    // Thư mục lưu trữ mặc định
    public static final String SERVER_ROOT_DIR = resolvePath("server_root");
    public static final String CLIENT_LOCAL_DIR = resolvePath("client_local");
    public static final String DATA_DIR = resolvePath("ftp_data");

    // Kích thước bộ đệm truyền tệp tin (8 KB)
    public static final int BUFFER_SIZE = 8192;

    private static String resolvePath(String subDir) {
        File local = new File(subDir);
        File nestedParent = new File("lap-trinh-mang/ftp-swing-app");
        if (!local.exists() && nestedParent.exists()) {
            return new File(nestedParent, subDir).getPath();
        }
        return local.getPath();
    }
}

